package com.gathin.tvhome.spotifybridge;

/**
 * Manages the single-use OAuth 2.0 PKCE session and CSRF state validation.
 * Enforces:
 * - Single-use code verifier: wiped immediately upon consumption.
 * - Strict state matching to prevent Cross-Site Request Forgery.
 * - Session timeout: expired sessions are rejected.
 */
public final class SpotifyAuthSession {

    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private static final class ActiveSession {
        final String verifier;
        final String state;
        final long createdAt;

        ActiveSession(String verifier, String state) {
            this.verifier = verifier;
            this.state = state;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SESSION_TIMEOUT_MS;
        }
    }

    private static ActiveSession activeSession;

    private SpotifyAuthSession() {}

    /**
     * Starts a new PKCE session and generates the authorization URL.
     * Replaces any existing pending session.
     */
    public static synchronized String startSession(String clientId, String redirectUri) {
        String verifier = SpotifyPkce.generateCodeVerifier();
        String challenge = SpotifyPkce.generateCodeChallenge(verifier);
        String state = SpotifyPkce.generateState();

        activeSession = new ActiveSession(verifier, state);
        return SpotifyPkce.buildAuthorizeUrl(clientId, redirectUri, challenge, state);
    }

    /**
     * Consumes the authorization code and validates the CSRF state.
     * Wipes the session immediately to guarantee single-use.
     *
     * @return the code_verifier needed for the token exchange
     * @throws SecurityException if state is invalid, mismatched, or session expired
     */
    public static synchronized String consumeCallback(String code, String state) throws SecurityException {
        if (activeSession == null) {
            throw new SecurityException("No active OAuth session");
        }

        if (activeSession.isExpired()) {
            activeSession = null;
            throw new SecurityException("OAuth session expired");
        }

        if (state == null || !state.equals(activeSession.state)) {
            activeSession = null;
            throw new SecurityException("State mismatch - possible CSRF attack");
        }

        if (code == null || code.trim().isEmpty()) {
            activeSession = null;
            throw new SecurityException("Missing authorization code");
        }

        String verifier = activeSession.verifier;
        activeSession = null; // Enforce single-use: cannot be reused
        return verifier;
    }

    public static synchronized boolean hasActiveSession() {
        return activeSession != null && !activeSession.isExpired();
    }

    public static synchronized void cancelSession() {
        activeSession = null;
    }
}
