package com.gathin.tvhome.spotifybridge;

public interface TokenStorage {
    void saveTokens(String clientId, String accessToken, String refreshToken, long expiresInSeconds);
    boolean saveTokensIfVersion(long expectedVersion, String clientId, String accessToken, String refreshToken, long expiresInSeconds);
    long getSessionVersion();
    String getClientId();
    String getAccessToken();
    String getRefreshToken();
    boolean isExpired();
    boolean hasRefreshToken();
    void clear();
}
