package com.gathin.tvhome.spotifybridge;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local secure storage for Spotify OAuth tokens encrypted via AndroidKeyStore (AES-256-GCM).
 * Never stores plaintext tokens, never logs tokens, and never exports tokens to external apps or Git.
 */
public class SpotifyTokenStore implements TokenStorage {

    private static final String PREF_NAME = "spotify_tokens";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_ENC_ACCESS_TOKEN = "enc_access_token";
    private static final String KEY_ENC_REFRESH_TOKEN = "enc_refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private final SharedPreferences prefs;
    private long sessionVersion = 0L;

    public SpotifyTokenStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized long getSessionVersion() {
        return sessionVersion;
    }

    public synchronized boolean saveTokensIfVersion(long expectedVersion, String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
        if (this.sessionVersion != expectedVersion) {
            return false;
        }
        saveTokens(clientId, accessToken, refreshToken, expiresInSeconds);
        return true;
    }

    public synchronized void saveTokens(String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
        long expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L);
        String encAccess = accessToken != null ? SpotifyKeyStore.encrypt(accessToken) : "";
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ENC_ACCESS_TOKEN, encAccess)
                .putLong(KEY_EXPIRES_AT, expiresAt);

        if (clientId != null && !clientId.isEmpty()) {
            editor.putString(KEY_CLIENT_ID, clientId);
        }
        if (refreshToken != null && !refreshToken.isEmpty()) {
            String encRefresh = SpotifyKeyStore.encrypt(refreshToken);
            editor.putString(KEY_ENC_REFRESH_TOKEN, encRefresh);
        }
        editor.apply();
    }

    public synchronized void saveTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        saveTokens(null, accessToken, refreshToken, expiresInSeconds);
    }

    public synchronized String getClientId() {
        return prefs.getString(KEY_CLIENT_ID, null);
    }

    public synchronized String getAccessToken() {
        String encrypted = prefs.getString(KEY_ENC_ACCESS_TOKEN, null);
        return encrypted != null ? SpotifyKeyStore.decrypt(encrypted) : null;
    }

    public synchronized String getRefreshToken() {
        String encrypted = prefs.getString(KEY_ENC_REFRESH_TOKEN, null);
        return encrypted != null ? SpotifyKeyStore.decrypt(encrypted) : null;
    }

    public synchronized boolean isExpired() {
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
        // Margin of 60 seconds before actual expiration to refresh ahead of time
        return System.currentTimeMillis() >= (expiresAt - 60_000L);
    }

    public synchronized boolean hasRefreshToken() {
        String token = getRefreshToken();
        return token != null && !token.isEmpty();
    }

    public synchronized void clear() {
        sessionVersion++;
        prefs.edit().clear().apply();
    }
}
