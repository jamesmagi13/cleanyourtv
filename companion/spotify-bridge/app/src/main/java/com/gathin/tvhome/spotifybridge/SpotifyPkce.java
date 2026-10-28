package com.gathin.tvhome.spotifybridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Spotify OAuth 2.0 PKCE helper (RFC 7636).
 * Computes S256 code challenge from a cryptographically random code verifier.
 * Requires NO Client Secret anywhere in code or repository.
 */
public final class SpotifyPkce {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final int VERIFIER_LENGTH = 64;
    private static final int STATE_LENGTH = 32;
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state";

    public static final String DEFAULT_REDIRECT_URI = SpotifyLoopbackServer.DEFAULT_REDIRECT_URI;

    private SpotifyPkce() {}

    /**
     * Generates a high-entropy cryptographically random code verifier string (RFC 7636).
     */
    public static String generateCodeVerifier() {
        return randomString(VERIFIER_LENGTH);
    }

    /**
     * Generates a high-entropy cryptographically random state parameter for CSRF mitigation.
     */
    public static String generateState() {
        return randomString(STATE_LENGTH);
    }

    private static String randomString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    /**
     * Calculates the S256 code challenge: BASE64URL-ENCODE(SHA256(ASCII(code_verifier))) without padding.
     */
    public static String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Builds the authorization URL for user login via browser or custom tab.
     */
    public static String buildAuthorizeUrl(String clientId, String redirectUri, String codeChallenge, String state) {
        return "https://accounts.spotify.com/authorize?"
                + "client_id=" + urlEncode(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&code_challenge_method=S256"
                + "&code_challenge=" + urlEncode(codeChallenge)
                + "&state=" + urlEncode(state)
                + "&scope=" + urlEncode(SCOPES);
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }
}
