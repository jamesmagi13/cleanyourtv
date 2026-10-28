package com.gathin.tvhome.spotifybridge;

import android.content.Context;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Moderate background polling engine for Spotify Web API (/v1/me/player/currently-playing).
 * - Only active when the clock screensaver has subscribed (subscriberCount > 0).
 * - Stops immediately when unsubscribed.
 * - Manages automatic token refresh via TokenStorage and SpotifyApiClient.
 * - Handles 204 No Content, 401 Unauthorized, 403 Forbidden, and 429 Rate Limiting with Retry-After backoff.
 * - Atomically downloads album artwork, validating HTTPS and preventing stale artwork from persisting.
 * - Triggers ContentResolver.notifyChange() only when playback metadata actually changes.
 */
public class SpotifyPoller {

    public static final long ACTIVE_POLL_INTERVAL_SEC = 15L;
    public static final long IDLE_POLL_INTERVAL_SEC = 30L;
    public static final long ERROR_BACKOFF_SEC = 30L;
    public static final long QUOTA_EXCEEDED_BACKOFF_SEC = 3600L; // 1 hour backoff on QUOTA_EXCEEDED

    public interface ProviderNotifier {
        void notifyChange();
    }

    private static SpotifyPoller instance;

    public static synchronized SpotifyPoller getInstance(Context context) {
        if (instance == null) {
            Context appCtx = context.getApplicationContext();
            instance = new SpotifyPoller(appCtx, new SpotifyApiClient(), new SpotifyTokenStore(appCtx));
        }
        return instance;
    }

    public static synchronized void setInstanceForTesting(SpotifyPoller testInstance) {
        if (instance != null && instance != testInstance) {
            instance.stop();
        }
        instance = testInstance;
    }

    private final Context context;
    private final SpotifyApiClient apiClient;
    private final TokenStorage tokenStore;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final File cacheDir;
    private final ProviderNotifier notifier;

    private boolean running = false;
    private ScheduledFuture<?> scheduledTask;
    private String lastDownloadedArtUrl = "";

    public SpotifyPoller(Context context, SpotifyApiClient apiClient, TokenStorage tokenStore) {
        this(context, apiClient, tokenStore,
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "spotify-poller")),
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
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
        this.cacheDir = cacheDir != null ? cacheDir
                : (context != null && context.getCacheDir() != null ? context.getCacheDir()
                : new File(System.getProperty("java.io.tmpdir")));
        this.notifier = notifier;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        schedulePoll(0, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTask = null;
        }
        if (ownsScheduler && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    public synchronized void pollImmediately() {
        if (!running) {
            scheduler.execute(this::executePoll);
        } else {
            schedulePoll(0, TimeUnit.SECONDS);
        }
    }

    public synchronized boolean isRunning() {
        return running;
    }

    private synchronized void schedulePoll(long delay, TimeUnit unit) {
        if (!running || scheduler.isShutdown()) return;
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
        if (!tokenStore.hasRefreshToken()) {
            // No credentials configured
            boolean changed = SpotifyNowPlayingProvider.setPlaybackState(false, "", "", "", "", 0, 0, false);
            if (changed) {
                notifyProviderChange();
            }
            scheduleNext(IDLE_POLL_INTERVAL_SEC);
            return IDLE_POLL_INTERVAL_SEC;
        }

        String accessToken = tokenStore.getAccessToken();
        if (accessToken == null || tokenStore.isExpired()) {
            String refreshToken = tokenStore.getRefreshToken();
            String clientId = tokenStore.getClientId();
            if (refreshToken != null && clientId != null) {
                SpotifyApiClient.TokenResponse tokenResp = apiClient.refreshAccessToken(clientId, refreshToken);
                if (tokenResp.success) {
                    tokenStore.saveTokens(clientId, tokenResp.accessToken, tokenResp.refreshToken, tokenResp.expiresIn);
                    accessToken = tokenResp.accessToken;
                } else if ("invalid_grant".equals(tokenResp.error)) {
                    // Revoked token
                    tokenStore.clear();
                    SpotifyNowPlayingProvider.clearPlaybackState(context);
                    notifyProviderChange();
                    scheduleNext(IDLE_POLL_INTERVAL_SEC);
                    return IDLE_POLL_INTERVAL_SEC;
                } else {
                    scheduleNext(ERROR_BACKOFF_SEC);
                    return ERROR_BACKOFF_SEC;
                }
            } else {
                scheduleNext(IDLE_POLL_INTERVAL_SEC);
                return IDLE_POLL_INTERVAL_SEC;
            }
        }

        SpotifyApiClient.PlaybackResponse resp = apiClient.fetchCurrentlyPlaying(accessToken);
        long nextDelay = IDLE_POLL_INTERVAL_SEC;

        if (resp.statusCode == 200) {
            boolean hasArt = false;
            if (resp.isPlaying) {
                nextDelay = ACTIVE_POLL_INTERVAL_SEC;
                if (!resp.albumArtUrl.isEmpty()) {
                    hasArt = syncAlbumArt(resp.albumArtUrl);
                } else {
                    clearCachedArt();
                }
            } else {
                nextDelay = IDLE_POLL_INTERVAL_SEC;
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
        } else if (resp.statusCode == 204) {
            // Idle / No music playing
            nextDelay = IDLE_POLL_INTERVAL_SEC;
            clearCachedArt();
            boolean changed = SpotifyNowPlayingProvider.setPlaybackState(false, "", "", "", "", 0, 0, false);
            if (changed) {
                notifyProviderChange();
            }
        } else if (resp.statusCode == 401 || resp.tokenExpired) {
            // Token expired during call; refresh next cycle immediately
            nextDelay = 1L;
            String refreshToken = tokenStore.getRefreshToken();
            String clientId = tokenStore.getClientId();
            if (refreshToken != null && clientId != null) {
                SpotifyApiClient.TokenResponse tokenResp = apiClient.refreshAccessToken(clientId, refreshToken);
                if (tokenResp.success) {
                    tokenStore.saveTokens(clientId, tokenResp.accessToken, tokenResp.refreshToken, tokenResp.expiresIn);
                } else if ("invalid_grant".equals(tokenResp.error)) {
                    tokenStore.clear();
                    SpotifyNowPlayingProvider.clearPlaybackState(context);
                    notifyProviderChange();
                }
            }
        } else if (resp.statusCode == 429) {
            if (resp.quotaExceeded) {
                // Spotify July 2026 Quota Exceeded: back off for 1 hour to prevent request loops
                nextDelay = QUOTA_EXCEEDED_BACKOFF_SEC;
            } else {
                // Rate limited: adhere strictly to Retry-After header (minimum 15s)
                nextDelay = Math.max(ACTIVE_POLL_INTERVAL_SEC, resp.retryAfterSeconds);
            }
        } else if (resp.statusCode == 403 && resp.quotaExceeded) {
            nextDelay = QUOTA_EXCEEDED_BACKOFF_SEC;
        } else {
            nextDelay = ERROR_BACKOFF_SEC;
        }

        scheduleNext(nextDelay);
        return nextDelay;
    }

    private void scheduleNext(long delaySec) {
        synchronized (this) {
            if (running) {
                schedulePoll(delaySec, TimeUnit.SECONDS);
            }
        }
    }

    private boolean syncAlbumArt(String artUrl) {
        if (artUrl == null || artUrl.isEmpty() || !artUrl.startsWith("https://")) {
            clearCachedArt();
            return false;
        }

        File artFile = new File(cacheDir, "now_playing_art.png");

        if (artUrl.equals(lastDownloadedArtUrl) && artFile.exists() && artFile.length() > 0) {
            return true;
        }

        boolean success = apiClient.downloadAlbumArt(artUrl, artFile);
        if (success) {
            lastDownloadedArtUrl = artUrl;
            return true;
        } else {
            clearCachedArt();
            return false;
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
