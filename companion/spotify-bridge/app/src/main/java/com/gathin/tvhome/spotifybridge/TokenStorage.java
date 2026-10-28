package com.gathin.tvhome.spotifybridge;

public interface TokenStorage {
    void saveTokens(String clientId, String accessToken, String refreshToken, long expiresInSeconds);
    String getClientId();
    String getAccessToken();
    String getRefreshToken();
    boolean isExpired();
    boolean hasRefreshToken();
    void clear();
}
