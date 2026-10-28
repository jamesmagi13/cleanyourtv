package com.gathin.tvhome.spotifybridge;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;

public class SpotifySecurityTest {

    @Test
    public void keyStoreEncryptionAndDecryptionSucceeds() {
        String token = "BQC7...sample_token_content...XYZ";
        String encrypted = SpotifyKeyStore.encrypt(token);

        assertNotNull(encrypted);
        assertNotEquals(token, encrypted);
        assertTrue(encrypted.contains(":"));

        String decrypted = SpotifyKeyStore.decrypt(encrypted);
        assertEquals(token, decrypted);
    }

    @Test
    public void keyStoreRejectsCorruptedCiphertext() {
        String token = "sample_token";
        String encrypted = SpotifyKeyStore.encrypt(token);
        String corrupted = encrypted + "tampered";

        assertNull(SpotifyKeyStore.decrypt(corrupted));
        assertNull(SpotifyKeyStore.decrypt("invalid-without-separator"));
        assertNull(SpotifyKeyStore.decrypt(null));
    }

    @Test
    public void authSessionEnforcesStateMatchingAndSingleUse() {
        String clientId = "my-client-id";
        String redirectUri = "http://127.0.0.1:8888/callback";

        String authUrl = SpotifyAuthSession.startSession(clientId, redirectUri);
        assertTrue(authUrl.contains("client_id=" + clientId));
        assertTrue(SpotifyAuthSession.hasActiveSession());

        // Extract state from URL
        int stateIdx = authUrl.indexOf("state=");
        assertTrue(stateIdx > 0);
        int ampIdx = authUrl.indexOf('&', stateIdx);
        String state = ampIdx > 0 ? authUrl.substring(stateIdx + 6, ampIdx) : authUrl.substring(stateIdx + 6);

        // 1. Rejects wrong state (CSRF mitigation)
        try {
            SpotifyAuthSession.consumeCallback("valid-code", "tampered-state");
            fail("Expected SecurityException on state mismatch");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("State mismatch"));
        }

        // Session was wiped on error or must be restarted
        assertFalse(SpotifyAuthSession.hasActiveSession());

        // 2. Fresh session with valid state succeeds
        authUrl = SpotifyAuthSession.startSession(clientId, redirectUri);
        stateIdx = authUrl.indexOf("state=");
        ampIdx = authUrl.indexOf('&', stateIdx);
        state = ampIdx > 0 ? authUrl.substring(stateIdx + 6, ampIdx) : authUrl.substring(stateIdx + 6);

        String verifier = SpotifyAuthSession.consumeCallback("valid-auth-code", state);
        assertNotNull(verifier);
        assertEquals(64, verifier.length());

        // 3. Single-use: cannot be consumed a second time
        try {
            SpotifyAuthSession.consumeCallback("valid-auth-code", state);
            fail("Expected SecurityException on reused callback");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("No active OAuth session"));
        }
    }

    @Test
    public void loopbackServerQueryParsingHandlesStandardParameters() {
        String query = "code=AQB_test_code_123&state=my_csrf_state_456";
        Map<String, String> params = SpotifyLoopbackServer.parseQuery(query);

        assertEquals("AQB_test_code_123", params.get("code"));
        assertEquals("my_csrf_state_456", params.get("state"));
        assertNull(params.get("error"));
    }

    @Test
    public void loopbackServerQueryParsingHandlesErrorParameters() {
        String query = "error=access_denied&state=my_csrf_state_456";
        Map<String, String> params = SpotifyLoopbackServer.parseQuery(query);

        assertEquals("access_denied", params.get("error"));
        assertEquals("my_csrf_state_456", params.get("state"));
        assertNull(params.get("code"));
    }

    @Test
    public void providerMockModeIsDisabledByDefault() {
        // Guarantee that mock mode is not active by default in production
        SpotifyNowPlayingProvider.setLiveState(false, "", "", "", 0L, 0L);
        assertFalse(SpotifyNowPlayingProvider.isMockMode());
    }
}
