package com.gathin.tvhome.spotifybridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SpotifyPollerTest {

    private File tempCacheDir;
    private ScheduledExecutorService scheduler;

    @Before
    public void setUp() throws Exception {
        tempCacheDir = Files.createTempDirectory("spotify_poller_test").toFile();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        if (tempCacheDir != null && tempCacheDir.exists()) {
            File[] files = tempCacheDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            tempCacheDir.delete();
        }
    }

    private static class InMemoryTokenStorage implements TokenStorage {
        String clientId = "test-client";
        String accessToken = "valid-token";
        String refreshToken = "valid-refresh";
        boolean expired = false;
        long sessionVersion = 0L;

        @Override
        public synchronized void saveTokens(String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
            this.clientId = clientId;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expired = false;
        }

        @Override
        public synchronized boolean saveTokensIfVersion(long expectedVersion, String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
            if (this.sessionVersion != expectedVersion) {
                return false;
            }
            saveTokens(clientId, accessToken, refreshToken, expiresInSeconds);
            return true;
        }

        @Override public synchronized long getSessionVersion() { return sessionVersion; }
        @Override public synchronized String getClientId() { return clientId; }
        @Override public synchronized String getAccessToken() { return accessToken; }
        @Override public synchronized String getRefreshToken() { return refreshToken; }
        @Override public synchronized boolean isExpired() { return expired; }
        @Override public synchronized boolean hasRefreshToken() { return refreshToken != null && !refreshToken.isEmpty(); }
        @Override
        public synchronized void clear() {
            sessionVersion++;
            clientId = null;
            accessToken = null;
            refreshToken = null;
            expired = false;
        }
    }

    private static class MockHttpConnection extends HttpURLConnection {
        private final int code;
        private final String body;
        private final Map<String, String> headers = new HashMap<>();

        MockHttpConnection(URL url, int code, String body) {
            super(url);
            this.code = code;
            this.body = body != null ? body : "";
        }

        void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override public String getHeaderField(String name) { return headers.get(name); }
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return code; }

        @Override
        public InputStream getInputStream() throws IOException {
            if (code >= 400) throw new IOException("HTTP error " + code);
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return new java.io.ByteArrayOutputStream();
        }
    }

    @Test
    public void executePollWithActiveTrackDownloadsArtAndReturns15SecDelay() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        AtomicInteger notifyCount = new AtomicInteger(0);

        String playbackJson = "{"
                + "\"is_playing\": true,"
                + "\"progress_ms\": 25000,"
                + "\"item\": {"
                + "  \"name\": \"Heroes\","
                + "  \"uri\": \"spotify:track:heroes123\","
                + "  \"duration_ms\": 360000,"
                + "  \"artists\": [{\"name\": \"David Bowie\"}],"
                + "  \"album\": {"
                + "    \"name\": \"Heroes\","
                + "    \"images\": [{\"url\": \"https://i.scdn.co/art300.jpg\", \"width\": 300}]"
                + "  }"
                + "}"
                + "}";

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            if (url.equals(SpotifyApiClient.CURRENTLY_PLAYING_URL)) {
                return new MockHttpConnection(new URL(url), 200, playbackJson);
            } else if (url.startsWith("https://i.scdn.co/")) {
                return new MockHttpConnection(new URL(url), 200, "fake-image-bytes");
            }
            throw new IOException("Unexpected URL: " + url);
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, notifyCount::incrementAndGet);

        long delay = poller.executePoll();
        assertEquals(SpotifyPoller.ACTIVE_POLL_INTERVAL_SEC, delay);
        assertEquals(15L, delay);
        assertEquals(1, notifyCount.get());

        File artFile = new File(tempCacheDir, "now_playing_art.png");
        assertTrue("Album art should be downloaded atomically", artFile.exists());
        assertTrue(artFile.length() > 0);
    }

    @Test
    public void executePollWith204IdleClearsArtAndReturns30SecDelay() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        AtomicInteger notifyCount = new AtomicInteger(0);

        // Pre-create art file to simulate previous playing state
        SpotifyNowPlayingProvider.setPlaybackState(true, "Previous Track", "Artist", "Album", "spotify:track:prev", 1000L, 500L, true);
        File artFile = new File(tempCacheDir, "now_playing_art.png");
        try {
            Files.write(artFile.toPath(), "old-art".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            fail("Setup failed");
        }
        assertTrue(artFile.exists());

        SpotifyApiClient client = new SpotifyApiClient(url -> new MockHttpConnection(new URL(url), 204, ""));
        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, notifyCount::incrementAndGet);

        long delay = poller.executePoll();
        assertEquals(SpotifyPoller.IDLE_POLL_INTERVAL_SEC, delay);
        assertEquals(30L, delay);
        assertEquals(1, notifyCount.get());

        assertFalse("Cached art must be removed when player is idle (204)", artFile.exists());
    }

    @Test
    public void executePollWithExpiredTokenAutoRefreshes() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        storage.expired = true;
        AtomicInteger notifyCount = new AtomicInteger(0);

        String refreshResponse = "{\"access_token\": \"brand_new_access_token\", \"expires_in\": 3600}";
        String playbackResponse = "{\"is_playing\": false, \"item\": null}";

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            if (url.equals(SpotifyApiClient.ACCOUNTS_TOKEN_URL)) {
                return new MockHttpConnection(new URL(url), 200, refreshResponse);
            } else if (url.equals(SpotifyApiClient.CURRENTLY_PLAYING_URL)) {
                return new MockHttpConnection(new URL(url), 200, playbackResponse);
            }
            throw new IOException("Unexpected URL: " + url);
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, notifyCount::incrementAndGet);

        long delay = poller.executePoll();
        assertEquals("brand_new_access_token", storage.getAccessToken());
        assertFalse(storage.isExpired());
        assertEquals(SpotifyPoller.IDLE_POLL_INTERVAL_SEC, delay);
        assertEquals(30L, delay);
    }

    @Test
    public void executePollWithInvalidGrantClearsTokens() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        storage.expired = true;
        AtomicInteger notifyCount = new AtomicInteger(0);

        String errorResponse = "{\"error\": \"invalid_grant\", \"error_description\": \"Refresh token revoked\"}";

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            if (url.equals(SpotifyApiClient.ACCOUNTS_TOKEN_URL)) {
                return new MockHttpConnection(new URL(url), 400, errorResponse);
            }
            throw new IOException("Unexpected URL: " + url);
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, notifyCount::incrementAndGet);

        long delay = poller.executePoll();
        assertEquals(SpotifyPoller.IDLE_POLL_INTERVAL_SEC, delay);
        assertEquals(30L, delay);
        assertFalse("Tokens must be wiped when revoked with invalid_grant", storage.hasRefreshToken());
    }

    @Test
    public void executePollWith429RateLimitRespectsRetryAfter() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            MockHttpConnection conn = new MockHttpConnection(new URL(url), 429, "");
            conn.setHeader("Retry-After", "45");
            return conn;
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        long delay = poller.executePoll();
        assertEquals(45L, delay);
    }

    @Test
    public void executePollWith429QuotaExceededEnforcesOneHourBackoff() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        String errorJson = "{\"error\": {\"status\": 429, \"message\": \"Rate limit exceeded\", \"reason\": \"QUOTA_EXCEEDED\"}}";

        SpotifyApiClient client = new SpotifyApiClient(url -> new MockHttpConnection(new URL(url), 429, errorJson));
        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        long delay = poller.executePoll();
        assertEquals(SpotifyPoller.QUOTA_EXCEEDED_BACKOFF_SEC, delay);
        assertEquals(3600L, delay);
    }

    @Test
    public void executePollWith403QuotaExceededEnforcesOneHourBackoff() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        String errorJson = "{\"error\": {\"status\": 403, \"message\": \"Daily quota reached\", \"reason\": \"QUOTA_EXCEEDED\"}}";

        SpotifyApiClient client = new SpotifyApiClient(url -> new MockHttpConnection(new URL(url), 403, errorJson));
        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        long delay = poller.executePoll();
        assertEquals(SpotifyPoller.QUOTA_EXCEEDED_BACKOFF_SEC, delay);
        assertEquals(3600L, delay);
    }

    @Test
    public void startAndStopControlsRunningState() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        SpotifyApiClient client = new SpotifyApiClient();
        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        assertFalse(poller.isRunning());
        poller.start();
        assertTrue(poller.isRunning());
        poller.stop();
        assertFalse(poller.isRunning());
    }

    @Test
    public void startStopStartCyclesWithRealExecutor() throws Exception {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        String json200 = "{\"is_playing\": true, \"item\": {\"name\": \"Reused Song\", \"artists\": [{\"name\": \"Artist\"}], \"album\": {\"name\": \"Album\", \"images\": []}}, \"progress_ms\": 1000}";
        AtomicInteger networkHits = new AtomicInteger(0);
        SpotifyApiClient client = new SpotifyApiClient(url -> {
            networkHits.incrementAndGet();
            return new MockHttpConnection(new URL(url), 200, json200);
        });

        // Initialize poller with real default executor (ownsScheduler = true)
        SpotifyPoller poller = new SpotifyPoller(null, client, storage, null, true, tempCacheDir, () -> {});

        // Cycle 1: Start
        poller.start();
        assertTrue(poller.isRunning());
        Thread.sleep(100);
        assertTrue(networkHits.get() >= 1);

        // Stop Cycle 1
        poller.stop();
        assertFalse(poller.isRunning());
        int hitsAfterStop = networkHits.get();
        Thread.sleep(100);
        assertEquals(hitsAfterStop, networkHits.get());

        // Cycle 2: Start again (reusing singleton poller instance)
        poller.start();
        assertTrue(poller.isRunning());
        Thread.sleep(100);
        assertTrue(networkHits.get() > hitsAfterStop);

        // Teardown
        poller.destroy();
        assertFalse(poller.isRunning());
    }

    @Test
    public void pollAfter401WithTransientRefreshFailureAppliesSafeBackoffWithoutFastLoop() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        storage.saveTokens("client", "expired-token", "refresh-token", 3600);

        // Playback returns 401; Token refresh returns 500 transient server error
        SpotifyApiClient client = new SpotifyApiClient(url -> {
            if (url.contains("/api/token")) {
                return new MockHttpConnection(new URL(url), 500, "{\"error\":\"server_error\"}");
            }
            return new MockHttpConnection(new URL(url), 401, "{\"error\":\"token_expired\"}");
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        long delay = poller.executePoll();
        // Safe backoff must be 30 seconds, never 1 second!
        assertEquals(SpotifyPoller.ERROR_BACKOFF_SEC, delay);
        assertEquals(30L, delay);
    }

    @Test
    public void pollImmediatelyWhenNotRunningDoesNotTriggerNetworkCalls() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        AtomicInteger networkCalls = new AtomicInteger(0);
        SpotifyApiClient client = new SpotifyApiClient(url -> {
            networkCalls.incrementAndGet();
            return new MockHttpConnection(new URL(url), 200, "{}");
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        assertFalse(poller.isRunning());
        poller.pollImmediately();

        // No network calls should be dispatched when not running
        assertEquals(0, networkCalls.get());
    }

    @Test
    public void inFlightRequestCompletedAfterStopIsDiscarded() throws Exception {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        java.util.concurrent.CountDownLatch requestEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseResponse = new java.util.concurrent.CountDownLatch(1);

        String delayedTrackJson = "{\"is_playing\": true, \"item\": {\"name\": \"Ghost Track\", \"artists\": [{\"name\": \"Late Artist\"}], \"album\": {\"name\": \"Late Album\", \"images\": []}}, \"progress_ms\": 500}";

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            requestEntered.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            return new MockHttpConnection(new URL(url), 200, delayedTrackJson);
        });

        SpotifyNowPlayingProvider.clearPlaybackState(null);

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});
        poller.start();

        // Wait until HTTP request is in-flight
        assertTrue(requestEntered.await(5, TimeUnit.SECONDS));

        // Screensaver stops while request is in-flight!
        poller.stop();
        assertFalse(poller.isRunning());

        // Release delayed HTTP response
        releaseResponse.countDown();
        Thread.sleep(200);

        // Response must have been discarded! "Ghost Track" must NOT be in provider!
        assertFalse(SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);
        assertEquals("", SpotifyNowPlayingProvider.getPlaybackSnapshot().trackTitle);
    }

    @Test
    public void prolongedNetworkFailureExpiresCurrentlyPlayingTrack() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();

        // Seed with active song
        SpotifyNowPlayingProvider.setPlaybackState(true, "Playing Song", "Artist", "Album", "", 2000, 1000, false);
        assertTrue(SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);

        // Client that always fails with 500 internal server error
        SpotifyApiClient client = new SpotifyApiClient(url -> new MockHttpConnection(new URL(url), 500, "{\"error\": \"down\"}"));
        AtomicInteger notifyCount = new AtomicInteger(0);
        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, notifyCount::incrementAndGet);

        // Poll 1: Single transient error -> preserve existing metadata without flickering
        long delay1 = poller.executePoll();
        assertEquals(SpotifyPoller.ERROR_BACKOFF_SEC, delay1);
        assertTrue("Track should stay visible on single transient failure", SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);
        assertEquals("Playing Song", SpotifyNowPlayingProvider.getPlaybackSnapshot().trackTitle);
        assertEquals(1, poller.getConsecutiveErrors());

        // Simulate passage of backoff time for Poll 2
        poller.resetBackoff();
        long delay2 = poller.executePoll();
        assertEquals(SpotifyPoller.ERROR_BACKOFF_SEC, delay2);
        assertTrue("Track should stay visible on second failure", SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);
        assertEquals(2, poller.getConsecutiveErrors());

        // Simulate passage of backoff time for Poll 3
        poller.resetBackoff();
        long delay3 = poller.executePoll();
        assertEquals(SpotifyPoller.ERROR_BACKOFF_SEC, delay3);
        assertEquals(3, poller.getConsecutiveErrors());
        assertFalse("Track must expire after prolonged network failure", SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);
        assertEquals("", SpotifyNowPlayingProvider.getPlaybackSnapshot().trackTitle);
        assertTrue("Notifier should be triggered on expiry", notifyCount.get() > 0);
    }

    @Test
    public void lateTokenRefreshAfterLogoutIsDiscardedAndDoesNotResurrectTokens() throws Exception {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        storage.saveTokens("client-123", "expired-token", "refresh-token-old", 3600);
        storage.expired = true; // Forces refreshAccessToken

        java.util.concurrent.CountDownLatch refreshEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseRefresh = new java.util.concurrent.CountDownLatch(1);

        String refreshedTokenJson = "{\"access_token\": \"resurrected_access\", \"refresh_token\": \"resurrected_refresh\", \"expires_in\": 3600}";

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            if (url.contains("/api/token")) {
                refreshEntered.countDown();
                try {
                    releaseRefresh.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                return new MockHttpConnection(new URL(url), 200, refreshedTokenJson);
            }
            return new MockHttpConnection(new URL(url), 200, "{}");
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        // Start executePoll on a worker thread
        java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<Long> pollFuture = worker.submit(poller::executePoll);

            // Wait until token refresh is blocking on network
            assertTrue(refreshEntered.await(5, TimeUnit.SECONDS));

            // User logs out concurrently!
            storage.clear();
            assertFalse(storage.hasRefreshToken());
            assertNull(storage.getAccessToken());

            // Release the late refresh HTTP response
            releaseRefresh.countDown();
            long resultDelay = pollFuture.get(5, TimeUnit.SECONDS);

            // Late refresh must be completely discarded; tokens must NOT be saved!
            assertEquals(0L, resultDelay);
            assertFalse("Late token refresh must never resurrect credentials after logout", storage.hasRefreshToken());
            assertNull(storage.getAccessToken());
            assertNull(storage.getRefreshToken());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void eliminationOfConcurrencyWindowGuaranteesCancelledPollNeverPublishesMetadataOrArt() throws Exception {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        java.util.concurrent.CountDownLatch artDownloadEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseArtDownload = new java.util.concurrent.CountDownLatch(1);

        String playbackJson = "{\"is_playing\": true, \"item\": {\"name\": \"Race Track\", \"artists\": [{\"name\": \"Race Artist\"}], \"album\": {\"name\": \"Race Album\", \"images\": [{\"url\": \"https://i.scdn.co/image/race123\"}]}}, \"progress_ms\": 500}";

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            if (url.startsWith("https://i.scdn.co/")) {
                artDownloadEntered.countDown();
                try {
                    releaseArtDownload.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                return new MockHttpConnection(new URL(url), 200, "image-payload");
            }
            return new MockHttpConnection(new URL(url), 200, playbackJson);
        });

        SpotifyNowPlayingProvider.clearPlaybackState(null);

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});
        poller.start();

        java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<Long> pollFuture = worker.submit(poller::executePoll);

            // Wait until album art download is blocking
            assertTrue(artDownloadEntered.await(5, TimeUnit.SECONDS));

            // Concurrently stop poller (screensaver closes or unobserved)
            poller.stop();
            assertFalse(poller.isRunning());

            // Release art download
            releaseArtDownload.countDown();
            long result = pollFuture.get(5, TimeUnit.SECONDS);

            // Must discard atomically: result 0, no metadata in provider, no art on disk
            assertEquals(0L, result);
            assertFalse(SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);
            assertEquals("", SpotifyNowPlayingProvider.getPlaybackSnapshot().trackTitle);
            File artFile = new File(tempCacheDir, "now_playing_art.png");
            assertFalse("Canceled in-flight art must be cleaned up and never persist", artFile.exists());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void retryAfterAndQuotaExceededBackoffsArePreservedAcrossStopAndStart() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();

        // 1. HTTP 429 with Retry-After: 45
        SpotifyApiClient client429 = new SpotifyApiClient(url -> {
            MockHttpConnection conn = new MockHttpConnection(new URL(url), 429, "{}");
            conn.setHeader("Retry-After", "45");
            return conn;
        });

        SpotifyPoller poller = new SpotifyPoller(null, client429, storage, scheduler, false, tempCacheDir, () -> {});
        poller.start();

        long delay429 = poller.executePoll();
        assertEquals(45L, delay429);
        assertTrue(poller.getBackoffRemainingSeconds() >= 44L);

        // Screensaver closes: stop()
        poller.stop();
        assertFalse(poller.isRunning());
        // Remaining backoff must still be preserved
        assertTrue(poller.getBackoffRemainingSeconds() >= 44L);

        // Screensaver reopens: start() must NOT bypass backoff (delay > 0)
        poller.start();
        assertTrue(poller.isRunning());
        assertTrue("Backoff must not be bypassed by start()", poller.getBackoffRemainingSeconds() >= 43L);
        // pollImmediately also must honor remaining backoff
        poller.pollImmediately();
        assertTrue(poller.getBackoffRemainingSeconds() >= 43L);

        // 2. HTTP 403 QUOTA_EXCEEDED (3600 seconds backoff)
        String quotaJson = "{\"error\": {\"status\": 403, \"reason\": \"QUOTA_EXCEEDED\", \"message\": \"Quota reached\"}}";
        SpotifyApiClient clientQuota = new SpotifyApiClient(url -> new MockHttpConnection(new URL(url), 403, quotaJson));
        SpotifyPoller pollerQuota = new SpotifyPoller(null, clientQuota, storage, scheduler, false, tempCacheDir, () -> {});
        pollerQuota.start();

        long delayQuota = pollerQuota.executePoll();
        assertEquals(SpotifyPoller.QUOTA_EXCEEDED_BACKOFF_SEC, delayQuota);
        assertTrue(pollerQuota.getBackoffRemainingSeconds() >= 3590L);

        // Stop and restart screensaver
        pollerQuota.stop();
        assertTrue(pollerQuota.getBackoffRemainingSeconds() >= 3590L);
        pollerQuota.start();
        assertTrue("Quota backoff must be preserved across stop/start", pollerQuota.getBackoffRemainingSeconds() >= 3590L);
    }

    @Test
    public void transitionLogoutToNewLoginWithActiveSubscriberRestartsPollingWithValidSession() {
        InMemoryTokenStorage storage = new InMemoryTokenStorage();
        storage.saveTokens("client", "access1", "refresh1", 3600);

        String trackJson = "{\"is_playing\": true, \"item\": {\"name\": \"Account 2 Song\", \"artists\": [{\"name\": \"Artist 2\"}], \"album\": {\"name\": \"Album 2\", \"images\": []}}, \"progress_ms\": 100}";
        AtomicInteger networkHits = new AtomicInteger(0);
        SpotifyApiClient client = new SpotifyApiClient(url -> {
            networkHits.incrementAndGet();
            return new MockHttpConnection(new URL(url), 200, trackJson);
        });

        SpotifyPoller poller = new SpotifyPoller(null, client, storage, scheduler, false, tempCacheDir, () -> {});

        // 1. Screensaver is open and subscribed
        SpotifyNowPlayingProvider.resetSubscribersForTesting();
        SpotifyNowPlayingProvider.setLiveState(false, "", "", "", 0, 0);
        // Simulate subscriber
        SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, null, poller);
        assertEquals(1, SpotifyNowPlayingProvider.getSubscriberCount());
        assertTrue(poller.isRunning());

        // 2. User logs out: onSessionTerminated
        poller.onSessionTerminated();
        storage.clear();
        assertFalse("Poller must stop on session termination", poller.isRunning());
        assertFalse("Tokens must be cleared", storage.hasRefreshToken());
        assertEquals("Subscriber count remains 1 because screensaver is still open", 1, SpotifyNowPlayingProvider.getSubscriberCount());

        // 3. User logs in with new valid account
        storage.saveTokens("client2", "access2", "refresh2", 3600);
        assertTrue(storage.hasRefreshToken());

        // 4. Session restored: because subscriberCount == 1, polling must restart immediately!
        poller.onSessionRestored();
        assertTrue("Poller must automatically resume when screensaver is still active", poller.isRunning());
    }
}
