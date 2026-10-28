package com.gathin.tvhome.spotifybridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpotifyPkceTest {

    @Test
    public void codeVerifierMeetsRfc7636Requirements() {
        String verifier1 = SpotifyPkce.generateCodeVerifier();
        String verifier2 = SpotifyPkce.generateCodeVerifier();

        assertEquals(64, verifier1.length());
        assertEquals(64, verifier2.length());
        assertNotEquals(verifier1, verifier2);

        // Verify character set: [A-Z], [a-z], [0-9], "-", ".", "_", "~"
        assertTrue(verifier1.matches("^[A-Za-z0-9\\-._~]{64}$"));
        assertTrue(verifier2.matches("^[A-Za-z0-9\\-._~]{64}$"));
    }

    @Test
    public void codeChallengeMatchesRfc7636AppendixBTestVector() {
        // Official RFC 7636 Appendix B Test Vector:
        // code_verifier = dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
        // code_challenge = E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

        String challenge = SpotifyPkce.generateCodeChallenge(verifier);
        assertEquals(expectedChallenge, challenge);
    }

    @Test
    public void buildAuthorizeUrlContainsRequiredParameters() {
        String clientId = "test-client-id-12345";
        String redirectUri = "tvhome-spotify://callback";
        String challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        String state = "random-state-xyz";

        String url = SpotifyPkce.buildAuthorizeUrl(clientId, redirectUri, challenge, state);

        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"));
        assertTrue(url.contains("client_id=test-client-id-12345"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("code_challenge=" + challenge));
        assertTrue(url.contains("state=" + state));
        assertTrue(url.contains("scope=user-read-currently-playing+user-read-playback-state")
                || url.contains("scope=user-read-currently-playing%20user-read-playback-state"));
    }
}
