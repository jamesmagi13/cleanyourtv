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

        @Override
        public void saveTokens(String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
            this.clientId = clientId;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expired = false;
        }

        @Override public String getClientId() { return clientId; }
        @Override public String getAccessToken() { return accessToken; }
        @Override public String getRefreshToken() { return refreshToken; }
        @Override public boolean isExpired() { return expired; }
        @Override public boolean hasRefreshToken() { return refreshToken != null && !refreshToken.isEmpty(); }
        @Override
        public void clear() {
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
}
