package com.gathin.tvhome.spotifybridge;

import android.content.Context;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Moderate background polling engine for Spotify Web API (/v1/me/player/currently-playing).
 * - Only active when the clock screensaver has subscribed (subscriberCount > 0).
 * - Stops immediately when unsubscribed without destroying the reusable executor pool.
 * - Handles multiple start -> stop -> start cycles gracefully.
 * - Preserves rate-limit and QUOTA_EXCEEDED backoffs across screensaver stop/start cycles.
 * - Manages automatic token refresh via TokenStorage and SpotifyApiClient with safe backoffs on transient failures.
 * - Atomically guards publication under generation and session validity locks (zero race window).
 * - Expires playback metadata after prolonged network failures without flickering on single transient glitches.
 * - Atomically downloads album artwork, validating HTTPS and preventing stale artwork from persisting.
 * - Triggers ContentResolver.notifyChange() when playback metadata changes or expires.
 */
public class SpotifyPoller {

    public static final long ACTIVE_POLL_INTERVAL_SEC = 15L;
    public static final long IDLE_POLL_INTERVAL_SEC = 30L;
    public static final long ERROR_BACKOFF_SEC = 30L;
    public static final long QUOTA_EXCEEDED_BACKOFF_SEC = 3600L; // 1 hour backoff on QUOTA_EXCEEDED
    public static final int MAX_CONSECUTIVE_ERRORS_BEFORE_EXPIRY = 3;

    public interface ProviderNotifier {
        void notifyChange();
    }

    private static SpotifyPoller instance;

    public static synchronized SpotifyPoller getInstance(Context context) {
        if (instance == null) {
            Context appCtx = context != null ? context.getApplicationContext() : null;
            instance = new SpotifyPoller(appCtx, new SpotifyApiClient(), new SpotifyTokenStore(appCtx));
        }
        return instance;
    }

    public static synchronized void setInstanceForTesting(SpotifyPoller testInstance) {
        if (instance != null && instance != testInstance) {
            instance.destroy();
        }
        instance = testInstance;
    }

    private static ScheduledExecutorService createDefaultScheduler() {
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "spotify-poller");
            t.setDaemon(true);
            return t;
        });
        exec.setKeepAliveTime(10, TimeUnit.SECONDS);
        exec.allowCoreThreadTimeOut(true);
        exec.setRemoveOnCancelPolicy(true);
        return exec;
    }

    private final Context context;
    private final SpotifyApiClient apiClient;
    private final TokenStorage tokenStore;
    private ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final File cacheDir;
    private final ProviderNotifier notifier;

    private boolean running = false;
    private ScheduledFuture<?> scheduledTask;
    private String lastDownloadedArtUrl = "";
    private int generation = 0;
    private int consecutiveErrors = 0;
    private long backoffUntilTimestampMs = 0L;

    public SpotifyPoller(Context context, SpotifyApiClient apiClient, TokenStorage tokenStore) {
        this(context, apiClient, tokenStore,
                createDefaultScheduler(),
                true,
                context != null ? context.getCacheDir() : null,
                null);
    }

    public SpotifyPoller(Context context, SpotifyApiClient apiClient, TokenStorage tokenStore,
                         ScheduledExecutorService scheduler, boolean ownsScheduler,
                         File cacheDir, ProviderNotifier notifier) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.apiClient = apiClient;
        this.tokenStore = tokenStore;
        this.scheduler = scheduler != null ? scheduler : createDefaultScheduler();
        this.ownsScheduler = ownsScheduler;
        this.cacheDir = cacheDir != null ? cacheDir
                : (context != null && context.getCacheDir() != null ? context.getCacheDir()
                : new File(System.getProperty("java.io.tmpdir")));
        this.notifier = notifier;
    }

    public synchronized void start() {
        if (running) return;
        if (ownsScheduler && (scheduler == null || scheduler.isShutdown())) {
            scheduler = createDefaultScheduler();
        }
        running = true;
        generation++;
        long delaySec = getBackoffRemainingSeconds();
        schedulePoll(delaySec, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        running = false;
        generation++;
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTask = null;
        }
    }

    public synchronized void destroy() {
        stop();
        if (ownsScheduler && scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    public synchronized void pollImmediately() {
        if (!running) {
            // No active screensaver subscribers: do not make network calls.
            return;
        }
        long delaySec = getBackoffRemainingSeconds();
        schedulePoll(delaySec, TimeUnit.SECONDS);
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int getConsecutiveErrors() {
        return consecutiveErrors;
    }

    public synchronized long getBackoffRemainingSeconds() {
        long now = System.currentTimeMillis();
        return backoffUntilTimestampMs > now ? (backoffUntilTimestampMs - now + 999L) / 1000L : 0L;
    }

    public synchronized void setBackoff(long delaySec) {
        long until = System.currentTimeMillis() + (delaySec * 1000L);
        if (until > backoffUntilTimestampMs) {
            backoffUntilTimestampMs = until;
        }
    }

    public synchronized void resetBackoff() {
        backoffUntilTimestampMs = 0L;
    }

    public synchronized void onSessionRestored() {
        resetBackoff();
        consecutiveErrors = 0;
        if (tokenStore.hasRefreshToken() && SpotifyNowPlayingProvider.getSubscriberCount() > 0) {
            start();
            schedulePoll(0, TimeUnit.SECONDS);
        }
    }

    public synchronized void onSessionTerminated() {
        resetBackoff();
        consecutiveErrors = 0;
        stop();
        clearCachedArt();
    }

    private synchronized void schedulePoll(long delay, TimeUnit unit) {
        if (!running || scheduler == null || scheduler.isShutdown()) return;
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        scheduledTask = scheduler.schedule(this::executePoll, delay, unit);
    }

    /**
     * Executes a single polling cycle. Package-private for directed testing.
     * @return the delay in seconds until the next recommended poll.
     */
    public long executePoll() {
        final int currentGen;
        synchronized (this) {
            currentGen = this.generation;
            long remainingBackoff = getBackoffRemainingSeconds();
            if (remainingBackoff > 0) {
                scheduleNext(remainingBackoff, currentGen);
                return remainingBackoff;
            }
        }

        if (!tokenStore.hasRefreshToken()) {
            // No credentials configured
            boolean changed = SpotifyNowPlayingProvider.setPlaybackState(false, "", "", "", "", 0, 0, false);
            clearCachedArt();
            if (changed) {
                notifyProviderChange();
            }
            scheduleNext(IDLE_POLL_INTERVAL_SEC, currentGen);
            return IDLE_POLL_INTERVAL_SEC;
        }

        String accessToken = tokenStore.getAccessToken();
        if (accessToken == null || tokenStore.isExpired()) {
            String refreshToken = tokenStore.getRefreshToken();
            String clientId = tokenStore.getClientId();
            if (refreshToken != null && clientId != null) {
                long expectedVersion = tokenStore.getSessionVersion();
                SpotifyApiClient.TokenResponse tokenResp = apiClient.refreshAccessToken(clientId, refreshToken);
                if (tokenResp.success) {
                    boolean saved = tokenStore.saveTokensIfVersion(expectedVersion, clientId, tokenResp.accessToken, tokenResp.refreshToken, tokenResp.expiresIn);
                    if (saved) {
                        accessToken = tokenResp.accessToken;
                        consecutiveErrors = 0;
                    } else {
                        // Session was invalidated while refresh was in flight! Discard!
                        return 0;
                    }
                } else if ("invalid_grant".equals(tokenResp.error)) {
                    // Revoked token
                    tokenStore.clear();
                    SpotifyNowPlayingProvider.clearPlaybackState(context);
                    notifyProviderChange();
                    scheduleNext(IDLE_POLL_INTERVAL_SEC, currentGen);
                    return IDLE_POLL_INTERVAL_SEC;
                } else if (tokenResp.statusCode == 429) {
                    handleError();
                    long delay = Math.max(ERROR_BACKOFF_SEC, tokenResp.retryAfterSeconds);
                    setBackoff(delay);
                    scheduleNext(delay, currentGen);
                    return delay;
                } else {
                    handleError();
                    setBackoff(ERROR_BACKOFF_SEC);
                    scheduleNext(ERROR_BACKOFF_SEC, currentGen);
                    return ERROR_BACKOFF_SEC;
                }
            } else {
                scheduleNext(IDLE_POLL_INTERVAL_SEC, currentGen);
                return IDLE_POLL_INTERVAL_SEC;
            }
        }

        SpotifyApiClient.PlaybackResponse resp = apiClient.fetchCurrentlyPlaying(accessToken);

        // Check if stopped or session changed while the network request was in flight
        synchronized (this) {
            if (generation != currentGen || !tokenStore.hasRefreshToken()) {
                clearCachedArt();
                return 0; // Discard late response
            }
        }

        long nextDelay = IDLE_POLL_INTERVAL_SEC;

        if (resp.statusCode == 200) {
            consecutiveErrors = 0;
            resetBackoff();
            boolean hasArt = false;
            if (resp.isPlaying) {
                nextDelay = ACTIVE_POLL_INTERVAL_SEC;
                if (!resp.albumArtUrl.isEmpty()) {
                    hasArt = syncAlbumArt(resp.albumArtUrl, currentGen);
                } else {
                    clearCachedArt();
                }
            } else {
                nextDelay = IDLE_POLL_INTERVAL_SEC;
            }

            synchronized (this) {
                if (generation != currentGen || !tokenStore.hasRefreshToken()) {
                    clearCachedArt();
                    return 0;
                }
                boolean changed = SpotifyNowPlayingProvider.setPlaybackState(
                        resp.isPlaying,
                        resp.trackTitle,
                        resp.artistName,
                        resp.albumName,
                        resp.spotifyUri,
                        resp.durationMs,
                        resp.progressMs,
                        hasArt
                );
                if (changed) {
                    notifyProviderChange();
                }
            }
        } else if (resp.statusCode == 204) {
            // Idle / No music playing
            consecutiveErrors = 0;
            resetBackoff();
            nextDelay = IDLE_POLL_INTERVAL_SEC;
            synchronized (this) {
                if (generation != currentGen || !tokenStore.hasRefreshToken()) {
                    return 0;
                }
                clearCachedArt();
                boolean changed = SpotifyNowPlayingProvider.setPlaybackState(false, "", "", "", "", 0, 0, false);
                if (changed) {
                    notifyProviderChange();
                }
            }
        } else if (resp.statusCode == 401 || resp.tokenExpired) {
            String refreshToken = tokenStore.getRefreshToken();
            String clientId = tokenStore.getClientId();
            if (refreshToken != null && clientId != null) {
                long expectedVersion = tokenStore.getSessionVersion();
                SpotifyApiClient.TokenResponse tokenResp = apiClient.refreshAccessToken(clientId, refreshToken);
                if (tokenResp.success) {
                    boolean saved = tokenStore.saveTokensIfVersion(expectedVersion, clientId, tokenResp.accessToken, tokenResp.refreshToken, tokenResp.expiresIn);
                    if (saved) {
                        consecutiveErrors = 0;
                        nextDelay = 1L; // Retry playback query with fresh access token soon
                    } else {
                        return 0;
                    }
                } else if ("invalid_grant".equals(tokenResp.error)) {
                    tokenStore.clear();
                    SpotifyNowPlayingProvider.clearPlaybackState(context);
                    notifyProviderChange();
                    nextDelay = IDLE_POLL_INTERVAL_SEC;
                } else if (tokenResp.statusCode == 429) {
                    handleError();
                    nextDelay = Math.max(ERROR_BACKOFF_SEC, tokenResp.retryAfterSeconds);
                    setBackoff(nextDelay);
                } else {
                    // Transient failure on refresh (5xx, timeout, network error): safe backoff without 1s loop!
                    handleError();
                    nextDelay = ERROR_BACKOFF_SEC;
                    setBackoff(nextDelay);
                }
            } else {
                nextDelay = IDLE_POLL_INTERVAL_SEC;
            }
        } else if (resp.statusCode == 429) {
            handleError();
            if (resp.quotaExceeded) {
                nextDelay = QUOTA_EXCEEDED_BACKOFF_SEC;
            } else {
                nextDelay = Math.max(ACTIVE_POLL_INTERVAL_SEC, resp.retryAfterSeconds);
            }
            setBackoff(nextDelay);
        } else if (resp.statusCode == 403 && resp.quotaExceeded) {
            handleError();
            nextDelay = QUOTA_EXCEEDED_BACKOFF_SEC;
            setBackoff(nextDelay);
        } else {
            handleError();
            nextDelay = ERROR_BACKOFF_SEC;
            setBackoff(nextDelay);
        }

        scheduleNext(nextDelay, currentGen);
        return nextDelay;
    }

    private void handleError() {
        consecutiveErrors++;
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS_BEFORE_EXPIRY) {
            boolean changed = SpotifyNowPlayingProvider.setPlaybackState(false, "", "", "", "", 0, 0, false);
            clearCachedArt();
            if (changed) {
                notifyProviderChange();
            }
        }
    }

    private void scheduleNext(long delaySec, int currentGen) {
        synchronized (this) {
            if (running && generation == currentGen) {
                schedulePoll(delaySec, TimeUnit.SECONDS);
            }
        }
    }

    private boolean syncAlbumArt(String artUrl, int currentGen) {
        if (artUrl == null || artUrl.isEmpty() || !artUrl.startsWith("https://")) {
            clearCachedArt();
            return false;
        }

        File artFile = new File(cacheDir, "now_playing_art.png");

        if (artUrl.equals(lastDownloadedArtUrl) && artFile.exists() && artFile.length() > 0) {
            return true;
        }

        boolean success = apiClient.downloadAlbumArt(artUrl, artFile);
        synchronized (this) {
            if (generation != currentGen || !tokenStore.hasRefreshToken()) {
                clearCachedArt();
                return false;
            }
            if (success) {
                lastDownloadedArtUrl = artUrl;
                return true;
            } else {
                clearCachedArt();
                return false;
            }
        }
    }

    private void clearCachedArt() {
        lastDownloadedArtUrl = "";
        File artFile = new File(cacheDir, "now_playing_art.png");
        if (artFile.exists()) {
            artFile.delete();
        }
    }

    private void notifyProviderChange() {
        if (notifier != null) {
            notifier.notifyChange();
            return;
        }
        if (context != null) {
            try {
                context.getContentResolver().notifyChange(SpotifyContract.NOW_PLAYING_URI, null);
            } catch (Exception ignored) {
            }
        }
    }
}
