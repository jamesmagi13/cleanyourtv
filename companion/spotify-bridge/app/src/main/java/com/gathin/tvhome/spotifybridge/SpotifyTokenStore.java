package com.gathin.tvhome.spotifybridge;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Local secure storage for Spotify OAuth tokens encrypted via AndroidKeyStore (AES-256-GCM).
 * Never stores plaintext tokens, never logs tokens, and never exports tokens to external apps or Git.
 * Shared in-process session version coordinator prevents late refresh or OAuth callbacks
 * from reviving invalidated sessions across different store instances in the same process.
 */
public class SpotifyTokenStore implements TokenStorage {

    private static final String PREF_NAME = "spotify_tokens";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_ENC_ACCESS_TOKEN = "enc_access_token";
    private static final String KEY_ENC_REFRESH_TOKEN = "enc_refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static final Object PROCESS_LOCK = new Object();
    private static final AtomicLong GLOBAL_SESSION_VERSION = new AtomicLong(1L);

    private final SharedPreferences prefs;

    public SpotifyTokenStore(Context context) {
        this(context != null && context.getApplicationContext() != null
                ? context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                : (context != null ? context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) : null));
    }

    public SpotifyTokenStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static void resetSessionVersionForTesting() {
        synchronized (PROCESS_LOCK) {
            GLOBAL_SESSION_VERSION.set(1L);
        }
    }

    @Override
    public long getSessionVersion() {
        synchronized (PROCESS_LOCK) {
            return GLOBAL_SESSION_VERSION.get();
        }
    }

    @Override
    public boolean saveTokensIfVersion(long expectedVersion, String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
        synchronized (PROCESS_LOCK) {
            if (GLOBAL_SESSION_VERSION.get() != expectedVersion) {
                return false;
            }
            saveTokensInternal(clientId, accessToken, refreshToken, expiresInSeconds);
            return true;
        }
    }

    @Override
    public void saveTokens(String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
        synchronized (PROCESS_LOCK) {
            saveTokensInternal(clientId, accessToken, refreshToken, expiresInSeconds);
        }
    }

    public void saveTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        saveTokens(null, accessToken, refreshToken, expiresInSeconds);
    }

    private void saveTokensInternal(String clientId, String accessToken, String refreshToken, long expiresInSeconds) {
        if (prefs == null) return;
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

    @Override
    public String getClientId() {
        synchronized (PROCESS_LOCK) {
            return prefs != null ? prefs.getString(KEY_CLIENT_ID, null) : null;
        }
    }

    @Override
    public String getAccessToken() {
        synchronized (PROCESS_LOCK) {
            String encrypted = prefs != null ? prefs.getString(KEY_ENC_ACCESS_TOKEN, null) : null;
            return encrypted != null ? SpotifyKeyStore.decrypt(encrypted) : null;
        }
    }

    @Override
    public String getRefreshToken() {
        synchronized (PROCESS_LOCK) {
            String encrypted = prefs != null ? prefs.getString(KEY_ENC_REFRESH_TOKEN, null) : null;
            return encrypted != null ? SpotifyKeyStore.decrypt(encrypted) : null;
        }
    }

    @Override
    public boolean isExpired() {
        synchronized (PROCESS_LOCK) {
            if (prefs == null) return true;
            long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
            // Margin of 60 seconds before actual expiration to refresh ahead of time
            return System.currentTimeMillis() >= (expiresAt - 60_000L);
        }
    }

    @Override
    public boolean hasRefreshToken() {
        synchronized (PROCESS_LOCK) {
            String token = getRefreshToken();
            return token != null && !token.isEmpty();
        }
    }

    @Override
    public void clear() {
        synchronized (PROCESS_LOCK) {
            GLOBAL_SESSION_VERSION.incrementAndGet();
            if (prefs != null) {
                prefs.edit().clear().apply();
            }
        }
    }
}
