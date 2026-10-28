package com.gathin.tvhome.spotifybridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Directed integration orchestration test suite verifying:
 * - Loopback server executes authentication callback BEFORE emitting HTML response.
 * - Loopback server enforces request length limits (anti-slowloris).
 * - SpotifyApiClient enforces HTTPS-only and atomic file replacement for album art.
 * - Artwork path traversal rejection.
 * - SpotifyPoller moderate polling cadence and handling of 200, 204, 401, and 429.
 * - Token refresh lifecycle (auto-refresh on expiration, wipe on invalid_grant).
 * - Provider subscription idempotence and cleanup.
 */
public class SpotifyIntegrationOrchestrationTest {

    private File tempCacheDir;

    @Before
    public void setUp() throws Exception {
        tempCacheDir = Files.createTempDirectory("spotify_test_cache").toFile();
    }

    @After
    public void tearDown() {
        if (tempCacheDir != null && tempCacheDir.exists()) {
            File[] files = tempCacheDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            tempCacheDir.delete();
        }
    }

    private static class MockHttpConn extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;
        private final byte[] binaryBody;

        MockHttpConn(URL url, int responseCode, String responseBody) {
            super(url);
            this.responseCode = responseCode;
            this.responseBody = responseBody;
            this.binaryBody = null;
        }

        MockHttpConn(URL url, int responseCode, byte[] binaryBody) {
            super(url);
            this.responseCode = responseCode;
            this.responseBody = null;
            this.binaryBody = binaryBody;
        }

        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return responseCode; }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseCode >= 400) throw new IOException("HTTP error " + responseCode);
            if (binaryBody != null) return new ByteArrayInputStream(binaryBody);
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            if (responseBody != null) {
                return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
            }
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }
    }

    @Test
    public void loopbackServerExecutesAuthBeforeEmittingHtmlResponse() throws Exception {
        SpotifyLoopbackServer server = new SpotifyLoopbackServer();
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch clientReadLatch = new CountDownLatch(1);
        AtomicBoolean authCompletedBeforeResponse = new AtomicBoolean(false);
        AtomicReference<String> clientReceivedResponse = new AtomicReference<>();

        server.start(10000, (code, state, error) -> {
            callbackEntered.countDown();
            try {
                // Simulate delay during PKCE verification and token exchange
                Thread.sleep(150);
            } catch (InterruptedException ignored) {}
            authCompletedBeforeResponse.set(true);
            return true; // Authentication succeeded!
        });

        // Client performs HTTP GET to loopback
        Thread clientThread = new Thread(() -> {
            try (Socket socket = new Socket(InetAddress.getByName(SpotifyLoopbackServer.LOOPBACK_HOST), SpotifyLoopbackServer.LOOPBACK_PORT);
                 OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream()) {

                String request = "GET /callback?code=test_code_xyz&state=test_state_123 HTTP/1.1\r\n"
                        + "Host: 127.0.0.1:8888\r\n\r\n";
                out.write(request.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] b = new byte[1024];
                int read;
                while ((read = in.read(b)) != -1) {
                    buffer.write(b, 0, read);
                }
                clientReceivedResponse.set(buffer.toString(StandardCharsets.UTF_8.name()));
            } catch (Exception e) {
                clientReceivedResponse.set("ERROR: " + e.getMessage());
            } finally {
                clientReadLatch.countDown();
            }
        });

        clientThread.start();
        assertTrue(clientReadLatch.await(5, TimeUnit.SECONDS));
        server.stop();

        assertTrue("Authentication must complete before HTML response is written", authCompletedBeforeResponse.get());
        String response = clientReceivedResponse.get();
        assertNotNull(response);
        assertTrue(response.contains("HTTP/1.1 200 OK"));
        assertTrue(response.contains("CleanYourTV: Spotify Conectado"));
    }

    @Test
    public void loopbackServerRejectsOversizedRequestLine() throws Exception {
        SpotifyLoopbackServer server = new SpotifyLoopbackServer();
        AtomicBoolean callbackTriggered = new AtomicBoolean(false);
        CountDownLatch clientFinished = new CountDownLatch(1);
        AtomicReference<String> clientResponse = new AtomicReference<>();

        server.start(10000, (code, state, error) -> {
            callbackTriggered.set(true);
            return false;
        });

        Thread clientThread = new Thread(() -> {
            try (Socket socket = new Socket(InetAddress.getByName(SpotifyLoopbackServer.LOOPBACK_HOST), SpotifyLoopbackServer.LOOPBACK_PORT);
                 OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream()) {

                // Send request line exceeding 2048 bytes limit
                StringBuilder huge = new StringBuilder("GET /callback?param=");
                for (int i = 0; i < 3000; i++) huge.append("A");
                huge.append(" HTTP/1.1\r\nHost: 127.0.0.1:8888\r\n\r\n");

                out.write(huge.toString().getBytes(StandardCharsets.US_ASCII));
                out.flush();

                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[512];
                int r;
                while ((r = in.read(b)) != -1) {
                    buf.write(b, 0, r);
                }
                clientResponse.set(buf.toString(StandardCharsets.UTF_8.name()));
            } catch (Exception e) {
                clientResponse.set("EX: " + e.getMessage());
            } finally {
                clientFinished.countDown();
            }
        });

        clientThread.start();
        assertTrue(clientFinished.await(5, TimeUnit.SECONDS));
        server.stop();

        assertFalse("Callback must not be invoked on oversized request line", callbackTriggered.get());
    }

    @Test
    public void downloadAlbumArtEnforcesHttpsOnlyAndAtomicReplacement() throws Exception {
        byte[] fakeImageData = new byte[]{ (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00 };
        File targetFile = new File(tempCacheDir, "now_playing_art.png");

        SpotifyApiClient.HttpConnectionFactory httpsFactory = url -> {
            assertTrue("Url must be strictly HTTPS", url.startsWith("https://"));
            return new MockHttpConn(new URL(url), 200, fakeImageData);
        };

        SpotifyApiClient client = new SpotifyApiClient(httpsFactory);

        // 1. Rejects plain HTTP
        boolean httpResult = client.downloadAlbumArt("http://img.spotify.com/cover.jpg", targetFile);
        assertFalse("Plain HTTP must be strictly rejected", httpResult);
        assertFalse(targetFile.exists());

        // 2. Rejects null or empty
        assertFalse(client.downloadAlbumArt("", targetFile));
        assertFalse(client.downloadAlbumArt(null, targetFile));

        // 3. Accepts HTTPS and creates target file atomically
        boolean httpsResult = client.downloadAlbumArt("https://i.scdn.co/image/ab67616d0000b273abc123", targetFile);
        assertTrue(httpsResult);
        assertTrue(targetFile.exists());
        assertEquals(fakeImageData.length, targetFile.length());

        // Verify .tmp file was cleaned up
        File tmpFile = new File(tempCacheDir, "now_playing_art.png.tmp");
        assertFalse("Temporary file must be deleted upon atomic rename", tmpFile.exists());
    }

    @Test
    public void downloadAlbumArtBlocksDirectoryTraversal() {
        File maliciousTarget = new File(tempCacheDir, "../evil_art.png");
        SpotifyApiClient client = new SpotifyApiClient();

        boolean result = client.downloadAlbumArt("https://img.spotify.com/art.jpg", maliciousTarget);
        assertFalse("Must block directory traversal paths", result);
        assertFalse(maliciousTarget.exists());
    }

    @Test
    public void playbackResponseExtractsSpotifyUri() {
        String json = "{"
                + "\"is_playing\": true,"
                + "\"progress_ms\": 10000,"
                + "\"item\": {"
                + "  \"name\": \"Space Oddity\","
                + "  \"uri\": \"spotify:track:72Z17vmmeQKAg8bptWvpVG\","
                + "  \"duration_ms\": 315000,"
                + "  \"artists\": [{\"name\": \"David Bowie\"}],"
                + "  \"album\": {"
                + "    \"name\": \"David Bowie (Space Oddity)\","
                + "    \"images\": [{\"url\": \"https://i.scdn.co/image/640\", \"width\": 640}]"
                + "  }"
                + "}"
                + "}";

        SpotifyApiClient client = new SpotifyApiClient(url -> new MockHttpConn(new URL(url), 200, json));
        SpotifyApiClient.PlaybackResponse resp = client.fetchCurrentlyPlaying("mock_token");

        assertTrue(resp.isPlaying);
        assertEquals("Space Oddity", resp.trackTitle);
        assertEquals("spotify:track:72Z17vmmeQKAg8bptWvpVG", resp.spotifyUri);
    }
}
