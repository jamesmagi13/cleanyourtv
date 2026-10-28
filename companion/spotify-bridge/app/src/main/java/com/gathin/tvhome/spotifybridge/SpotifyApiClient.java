package com.gathin.tvhome.spotifybridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTPS Client for Spotify Accounts (OAuth 2.0 PKCE) and Web API (/v1/me/player/currently-playing).
 * Uses standard HttpURLConnection without external dependencies.
 * Supports custom HttpConnectionFactory for offline unit testing.
 */
public class SpotifyApiClient {

    public static final String ACCOUNTS_TOKEN_URL = "https://accounts.spotify.com/api/token";
    public static final String CURRENTLY_PLAYING_URL = "https://api.spotify.com/v1/me/player/currently-playing";

    public interface HttpConnectionFactory {
        HttpURLConnection openConnection(String url) throws IOException;
    }

    private static final HttpConnectionFactory DEFAULT_FACTORY = url -> (HttpURLConnection) new URL(url).openConnection();
    private final HttpConnectionFactory factory;

    public SpotifyApiClient() {
        this(DEFAULT_FACTORY);
    }

    public SpotifyApiClient(HttpConnectionFactory factory) {
        this.factory = factory != null ? factory : DEFAULT_FACTORY;
    }

    public static final class TokenResponse {
        public final boolean success;
        public final String accessToken;
        public final String refreshToken;
        public final long expiresIn;
        public final String error;
        public final String errorDescription;
        public final int statusCode;
        public final long retryAfterSeconds;

        public TokenResponse(String accessToken, String refreshToken, long expiresIn) {
            this(accessToken, refreshToken, expiresIn, 200, 0);
        }

        public TokenResponse(String accessToken, String refreshToken, long expiresIn, int statusCode, long retryAfterSeconds) {
            this.success = true;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.error = null;
            this.errorDescription = null;
            this.statusCode = statusCode;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public TokenResponse(String error, String errorDescription) {
            this(error, errorDescription, -1, 0);
        }

        public TokenResponse(String error, String errorDescription, int statusCode, long retryAfterSeconds) {
            this.success = false;
            this.accessToken = null;
            this.refreshToken = null;
            this.expiresIn = 0;
            this.error = error != null ? error : "unknown_error";
            this.errorDescription = errorDescription;
            this.statusCode = statusCode;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    public static final class PlaybackResponse {
        public final int statusCode;
        public final boolean isPlaying;
        public final String trackTitle;
        public final String artistName;
        public final String albumName;
        public final String albumArtUrl;
        public final long durationMs;
        public final long progressMs;
        public final boolean tokenExpired;
        public final long retryAfterSeconds;
        public final String spotifyUri;
        public final boolean quotaExceeded;

        private PlaybackResponse(int statusCode, boolean isPlaying, String title, String artist, String album,
                                 String artUrl, String spotifyUri, long duration, long progress,
                                 boolean tokenExpired, long retryAfter, boolean quotaExceeded) {
            this.statusCode = statusCode;
            this.isPlaying = isPlaying;
            this.trackTitle = title != null ? title : "";
            this.artistName = artist != null ? artist : "";
            this.albumName = album != null ? album : "";
            this.albumArtUrl = artUrl != null ? artUrl : "";
            this.spotifyUri = spotifyUri != null ? spotifyUri : "";
            this.durationMs = duration;
            this.progressMs = progress;
            this.tokenExpired = tokenExpired;
            this.retryAfterSeconds = retryAfter;
            this.quotaExceeded = quotaExceeded;
        }

        public static PlaybackResponse active(String title, String artist, String album, String artUrl, String spotifyUri, long duration, long progress) {
            return new PlaybackResponse(200, true, title, artist, album, artUrl, spotifyUri, duration, progress, false, 0, false);
        }

        public static PlaybackResponse paused(String title, String artist, String album, String artUrl, String spotifyUri, long duration, long progress) {
            return new PlaybackResponse(200, false, title, artist, album, artUrl, spotifyUri, duration, progress, false, 0, false);
        }

        public static PlaybackResponse empty(int code) {
            return new PlaybackResponse(code, false, "", "", "", "", "", 0, 0, false, 0, false);
        }

        public static PlaybackResponse expired() {
            return new PlaybackResponse(401, false, "", "", "", "", "", 0, 0, true, 0, false);
        }

        public static PlaybackResponse rateLimited(long retryAfter, boolean quotaExceeded) {
            return new PlaybackResponse(429, false, "", "", "", "", "", 0, 0, false, retryAfter, quotaExceeded);
        }

        public static PlaybackResponse rateLimited(long retryAfter) {
            return rateLimited(retryAfter, false);
        }

        public static PlaybackResponse quotaExceeded() {
            return new PlaybackResponse(429, false, "", "", "", "", "", 0, 0, false, 3600L, true);
        }
    }

    /**
     * Exchanges PKCE authorization code for access and refresh tokens.
     */
    public TokenResponse exchangeAuthorizationCode(String clientId, String code, String verifier, String redirectUri) {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("code_verifier", verifier);

        return postTokenRequest(params);
    }

    /**
     * Renews an access token using a refresh token.
     */
    public TokenResponse refreshAccessToken(String clientId, String refreshToken) {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);

        return postTokenRequest(params);
    }

    private TokenResponse postTokenRequest(Map<String, String> params) {
        HttpURLConnection conn = null;
        try {
            conn = factory.openConnection(ACCOUNTS_TOKEN_URL);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String body = buildFormBody(params);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            int code = conn.getResponseCode();
            long retryAfter = 0;
            String retryAfterHeader = conn.getHeaderField("Retry-After");
            if (retryAfterHeader != null) {
                try {
                    retryAfter = Long.parseLong(retryAfterHeader.trim());
                } catch (NumberFormatException ignored) {}
            }
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = readStream(in);

            JSONObject json = new JSONObject(responseText);
            if (code >= 200 && code < 300) {
                String accessToken = json.getString("access_token");
                String refreshToken = json.optString("refresh_token", params.get("refresh_token"));
                long expiresIn = json.optLong("expires_in", 3600L);
                return new TokenResponse(accessToken, refreshToken, expiresIn, code, retryAfter);
            } else {
                String error = json.optString("error", "http_" + code);
                String desc = json.optString("error_description", responseText);
                return new TokenResponse(error, desc, code, retryAfter);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new TokenResponse("network_error", e.getMessage(), -1, 0);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Polls current playback state from the user's Spotify account.
     */
    public PlaybackResponse fetchCurrentlyPlaying(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            return PlaybackResponse.expired();
        }

        HttpURLConnection conn = null;
        try {
            conn = factory.openConnection(CURRENTLY_PLAYING_URL);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);

            int code = conn.getResponseCode();
            if (code == 204) {
                return PlaybackResponse.empty(204);
            }
            if (code == 401) {
                return PlaybackResponse.expired();
            }
            if (code == 429) {
                String retryStr = conn.getHeaderField("Retry-After");
                long retryAfter = 30L;
                if (retryStr != null) {
                    try { retryAfter = Long.parseLong(retryStr.trim()); } catch (Exception ignored) {}
                }
                String errBody = readStream(conn.getErrorStream());
                boolean quotaExceeded = isQuotaExceededError(errBody);
                return PlaybackResponse.rateLimited(retryAfter, quotaExceeded);
            }
            if (code == 403) {
                String errBody = readStream(conn.getErrorStream());
                if (isQuotaExceededError(errBody)) {
                    return PlaybackResponse.quotaExceeded();
                }
                return PlaybackResponse.empty(403);
            }
            if (code != 200) {
                return PlaybackResponse.empty(code);
            }

            String responseText = readStream(conn.getInputStream());
            if (responseText.isEmpty()) return PlaybackResponse.empty(200);

            JSONObject json = new JSONObject(responseText);
            boolean isPlaying = json.optBoolean("is_playing", false);
            long progressMs = json.optLong("progress_ms", 0L);

            JSONObject item = json.optJSONObject("item");
            if (item == null) {
                // E.g. playing podcast or unsupported item type
                return PlaybackResponse.empty(200);
            }

            String title = item.optString("name", "");
            long durationMs = item.optLong("duration_ms", 0L);

            StringBuilder artistsBuilder = new StringBuilder();
            JSONArray artists = item.optJSONArray("artists");
            if (artists != null) {
                for (int i = 0; i < artists.length(); i++) {
                    JSONObject artistObj = artists.optJSONObject(i);
                    if (artistObj != null) {
                        if (artistsBuilder.length() > 0) artistsBuilder.append(", ");
                        artistsBuilder.append(artistObj.optString("name", ""));
                    }
                }
            }
            String artistName = artistsBuilder.toString();

            String albumName = "";
            String albumArtUrl = "";
            JSONObject album = item.optJSONObject("album");
            if (album != null) {
                albumName = album.optString("name", "");
                JSONArray images = album.optJSONArray("images");
                if (images != null && images.length() > 0) {
                    // Pick the middle size (usually ~300x300 or 640x640)
                    albumArtUrl = selectBestImageUrl(images);
                }
            }

            String spotifyUri = item.optString("uri", "");
            if (spotifyUri.isEmpty()) {
                JSONObject ext = item.optJSONObject("external_urls");
                if (ext != null) {
                    spotifyUri = ext.optString("spotify", "");
                }
            }

            if (isPlaying) {
                return PlaybackResponse.active(title, artistName, albumName, albumArtUrl, spotifyUri, durationMs, progressMs);
            } else {
                return PlaybackResponse.paused(title, artistName, albumName, albumArtUrl, spotifyUri, durationMs, progressMs);
            }
        } catch (Exception e) {
            return PlaybackResponse.empty(-1);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Selects an image with size <= 640px to bound memory footprint.
     */
    static String selectBestImageUrl(JSONArray images) {
        if (images == null || images.length() == 0) return "";
        String fallbackUrl = "";
        for (int i = 0; i < images.length(); i++) {
            JSONObject img = images.optJSONObject(i);
            if (img != null) {
                String url = img.optString("url", "");
                int width = img.optInt("width", 0);
                if (fallbackUrl.isEmpty()) fallbackUrl = url;
                // Target width ~300-640px
                if (width <= 640 && width >= 300) {
                    return url;
                }
            }
        }
        return fallbackUrl;
    }

    /**
     * Inspects Spotify error responses (e.g. July 2026 Developer Policy rate limiting / quota).
     */
    static boolean isQuotaExceededError(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) return false;
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                String reason = error.optString("reason", "");
                if ("QUOTA_EXCEEDED".equalsIgnoreCase(reason)) return true;
                String message = error.optString("message", "");
                if (message.toUpperCase().contains("QUOTA_EXCEEDED") || message.toUpperCase().contains("QUOTA EXCEEDED")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            if (responseBody.toUpperCase().contains("QUOTA_EXCEEDED")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Downloads album artwork into target file safely.
     * Enforces HTTPS-only URLs, canonical path checks, and atomic replacement via .tmp file.
     */
    public boolean downloadAlbumArt(String imageUrl, File targetFile) {
        if (imageUrl == null || imageUrl.isEmpty() || targetFile == null) return false;
        if (!imageUrl.startsWith("https://")) return false; // Strictly HTTPS

        File parentDir = targetFile.getParentFile();
        if (parentDir == null) return false;

        // Path traversal prevention
        try {
            if (!targetFile.getCanonicalPath().startsWith(parentDir.getCanonicalPath())) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        HttpURLConnection conn = null;
        File tempFile = new File(parentDir, targetFile.getName() + ".tmp");
        try {
            conn = factory.openConnection(imageUrl);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return false;

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > 2 * 1024 * 1024) { // Max 2 MB image limit
                        return false;
                    }
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                return tempFile.renameTo(targetFile);
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (conn != null) conn.disconnect();
        }
    }

    private static String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    private static String readStream(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
