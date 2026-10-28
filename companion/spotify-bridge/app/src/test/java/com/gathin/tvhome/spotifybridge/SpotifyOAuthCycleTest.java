package com.gathin.tvhome.spotifybridge;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Directed test suite exercising the complete OAuth cycle with mocked responses:
 * - Successful code exchange and playback query
 * - State mismatch (CSRF attack) rejection
 * - Session expiration
 * - Duplicate callback rejection (single-use PKCE)
 * - Network errors
 * - Token renewal
 * - Invalid refresh token handling
 * - 204 No Content handling
 */
public class SpotifyOAuthCycleTest {

    private static class MockHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;
        private final Map<String, String> responseHeaders = new HashMap<>();
        private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

        MockHttpURLConnection(URL url, int responseCode, String responseBody) {
            super(url);
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return responseCode; }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseCode >= 400) throw new IOException("HTTP error " + responseCode);
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return requestBody;
        }

        @Override
        public String getHeaderField(String name) {
            return responseHeaders.get(name);
        }

        void setHeader(String name, String value) {
            responseHeaders.put(name, value);
        }
    }

    @Test
    public void testSuccessfulOAuthCycleAndPlayback() throws Exception {
        String clientId = "test-client-id";
        String redirectUri = SpotifyLoopbackServer.DEFAULT_REDIRECT_URI;

        // 1. Start session
        String authUrl = SpotifyAuthSession.startSession(clientId, redirectUri);
        assertTrue(authUrl.contains("client_id=" + clientId));
        assertTrue(SpotifyAuthSession.hasActiveSession());

        int stateIdx = authUrl.indexOf("state=");
        int ampIdx = authUrl.indexOf('&', stateIdx);
        String state = ampIdx > 0 ? authUrl.substring(stateIdx + 6, ampIdx) : authUrl.substring(stateIdx + 6);

        // 2. Consume callback with matching state
        String authCode = "valid_auth_code_abc123";
        String verifier = SpotifyAuthSession.consumeCallback(authCode, state);
        assertNotNull(verifier);
        assertFalse(SpotifyAuthSession.hasActiveSession()); // Single-use enforced

        // 3. Mock exchange code POST to /api/token
        String tokenJson = "{\"access_token\": \"mock_acc_123\", \"refresh_token\": \"mock_ref_456\", \"expires_in\": 3600}";
        SpotifyApiClient.HttpConnectionFactory tokenFactory = url -> {
            assertEquals(SpotifyApiClient.ACCOUNTS_TOKEN_URL, url);
            return new MockHttpURLConnection(new URL(url), 200, tokenJson);
        };

        SpotifyApiClient client = new SpotifyApiClient(tokenFactory);
        SpotifyApiClient.TokenResponse tokenResp = client.exchangeAuthorizationCode(clientId, authCode, verifier, redirectUri);

        assertTrue(tokenResp.success);
        assertEquals("mock_acc_123", tokenResp.accessToken);
        assertEquals("mock_ref_456", tokenResp.refreshToken);
        assertEquals(3600L, tokenResp.expiresIn);

        // 4. Mock GET /v1/me/player/currently-playing
        String playbackJson = "{"
                + "\"is_playing\": true,"
                + "\"progress_ms\": 42000,"
                + "\"item\": {"
                + "  \"name\": \"Life on Mars?\","
                + "  \"duration_ms\": 234000,"
                + "  \"artists\": [{\"name\": \"David Bowie\"}],"
                + "  \"album\": {"
                + "    \"name\": \"Hunky Dory\","
                + "    \"images\": [{\"url\": \"https://img/cover640.jpg\", \"width\": 640}]"
                + "  }"
                + "}"
                + "}";

        SpotifyApiClient.HttpConnectionFactory apiFactory = url -> {
            assertEquals(SpotifyApiClient.CURRENTLY_PLAYING_URL, url);
            return new MockHttpURLConnection(new URL(url), 200, playbackJson);
        };

        SpotifyApiClient apiClient = new SpotifyApiClient(apiFactory);
        SpotifyApiClient.PlaybackResponse playResp = apiClient.fetchCurrentlyPlaying(tokenResp.accessToken);

        assertEquals(200, playResp.statusCode);
        assertTrue(playResp.isPlaying);
        assertEquals("Life on Mars?", playResp.trackTitle);
        assertEquals("David Bowie", playResp.artistName);
        assertEquals("Hunky Dory", playResp.albumName);
        assertEquals("https://img/cover640.jpg", playResp.albumArtUrl);
        assertEquals(234000L, playResp.durationMs);
        assertEquals(42000L, playResp.progressMs);
        assertFalse(playResp.tokenExpired);
    }

    @Test
    public void testStateMismatchRejected() {
        String clientId = "test-client";
        String redirectUri = SpotifyLoopbackServer.DEFAULT_REDIRECT_URI;

        SpotifyAuthSession.startSession(clientId, redirectUri);
        assertTrue(SpotifyAuthSession.hasActiveSession());

        try {
            SpotifyAuthSession.consumeCallback("valid_code", "wrong_tampered_state");
            fail("Expected SecurityException on state mismatch");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("State mismatch"));
        }

        assertFalse(SpotifyAuthSession.hasActiveSession());
    }

    @Test
    public void testDuplicateCallbackRejected() {
        String clientId = "test-client";
        String redirectUri = SpotifyLoopbackServer.DEFAULT_REDIRECT_URI;

        String authUrl = SpotifyAuthSession.startSession(clientId, redirectUri);
        int stateIdx = authUrl.indexOf("state=");
        int ampIdx = authUrl.indexOf('&', stateIdx);
        String state = ampIdx > 0 ? authUrl.substring(stateIdx + 6, ampIdx) : authUrl.substring(stateIdx + 6);

        // First consumption
        String verifier = SpotifyAuthSession.consumeCallback("code_1", state);
        assertNotNull(verifier);

        // Second consumption must fail
        try {
            SpotifyAuthSession.consumeCallback("code_1", state);
            fail("Expected SecurityException on reused callback");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("No active OAuth session"));
        }
    }

    @Test
    public void testNetworkErrorHandling() {
        SpotifyApiClient.HttpConnectionFactory failingFactory = url -> {
            throw new IOException("Simulated network timeout");
        };

        SpotifyApiClient client = new SpotifyApiClient(failingFactory);
        SpotifyApiClient.TokenResponse tokenResp = client.exchangeAuthorizationCode("id", "code", "verifier", "uri");
        assertFalse(tokenResp.success);
        assertEquals("network_error", tokenResp.error);

        SpotifyApiClient.PlaybackResponse playResp = client.fetchCurrentlyPlaying("token");
        assertEquals(-1, playResp.statusCode);
        assertFalse(playResp.isPlaying);
    }

    @Test
    public void testRefreshTokenSuccess() throws Exception {
        String refreshJson = "{\"access_token\": \"new_access_token_789\", \"expires_in\": 3600}";
        SpotifyApiClient.HttpConnectionFactory factory = url -> new MockHttpURLConnection(new URL(url), 200, refreshJson);

        SpotifyApiClient client = new SpotifyApiClient(factory);
        SpotifyApiClient.TokenResponse resp = client.refreshAccessToken("client-id", "mock_ref_456");

        assertTrue(resp.success);
        assertEquals("new_access_token_789", resp.accessToken);
        assertEquals("mock_ref_456", resp.refreshToken); // Keeps existing refresh token
        assertEquals(3600L, resp.expiresIn);
    }

    @Test
    public void testInvalidRefreshTokenHandling() throws Exception {
        String errorJson = "{\"error\": \"invalid_grant\", \"error_description\": \"Refresh token revoked\"}";
        SpotifyApiClient.HttpConnectionFactory factory = url -> new MockHttpURLConnection(new URL(url), 400, errorJson);

        SpotifyApiClient client = new SpotifyApiClient(factory);
        SpotifyApiClient.TokenResponse resp = client.refreshAccessToken("client-id", "revoked_token");

        assertFalse(resp.success);
        assertEquals("invalid_grant", resp.error);
        assertEquals("Refresh token revoked", resp.errorDescription);
    }

    @Test
    public void testPlayback204NoContent() throws Exception {
        SpotifyApiClient.HttpConnectionFactory factory = url -> new MockHttpURLConnection(new URL(url), 204, "");

        SpotifyApiClient client = new SpotifyApiClient(factory);
        SpotifyApiClient.PlaybackResponse resp = client.fetchCurrentlyPlaying("token");

        assertEquals(204, resp.statusCode);
        assertFalse(resp.isPlaying);
        assertEquals("", resp.trackTitle);
    }

    @Test
    public void testPlayback401ExpiredTriggersFlag() throws Exception {
        SpotifyApiClient.HttpConnectionFactory factory = url -> new MockHttpURLConnection(new URL(url), 401, "{\"error\": {\"status\": 401}}");

        SpotifyApiClient client = new SpotifyApiClient(factory);
        SpotifyApiClient.PlaybackResponse resp = client.fetchCurrentlyPlaying("expired_token");

        assertEquals(401, resp.statusCode);
        assertTrue(resp.tokenExpired);
        assertFalse(resp.isPlaying);
    }

    @Test
    public void testPlayback429RateLimitedReadsHeader() throws Exception {
        SpotifyApiClient.HttpConnectionFactory factory = url -> {
            MockHttpURLConnection conn = new MockHttpURLConnection(new URL(url), 429, "");
            conn.setHeader("Retry-After", "25");
            return conn;
        };

        SpotifyApiClient client = new SpotifyApiClient(factory);
        SpotifyApiClient.PlaybackResponse resp = client.fetchCurrentlyPlaying("token");

        assertEquals(429, resp.statusCode);
        assertEquals(25L, resp.retryAfterSeconds);
        assertFalse(resp.isPlaying);
    }
}
