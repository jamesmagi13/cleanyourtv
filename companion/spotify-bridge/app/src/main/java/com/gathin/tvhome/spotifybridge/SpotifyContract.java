package com.gathin.tvhome.spotifybridge;

import android.net.Uri;

/**
 * Public IPC Contract for Spotify Now Playing between the bridge companion and the launcher.
 */
public final class SpotifyContract {

    public static final String AUTHORITY = "com.gathin.tvhome.spotify";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static final String TABLE_NOW_PLAYING = "now_playing";
    public static final Uri NOW_PLAYING_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NOW_PLAYING);

    public static final String PATH_ART = "art";
    public static final Uri ART_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_ART);

    public static final String COLUMN_IS_PLAYING = "is_playing";
    public static final String COLUMN_TRACK_TITLE = "track_title";
    public static final String COLUMN_ARTIST_NAME = "artist_name";
    public static final String COLUMN_ALBUM_NAME = "album_name";
    public static final String COLUMN_DURATION_MS = "duration_ms";
    public static final String COLUMN_PROGRESS_MS = "progress_ms";
    public static final String COLUMN_ART_URI = "art_uri";
    public static final String COLUMN_SPOTIFY_URI = "spotify_uri";
    public static final String COLUMN_UPDATED_AT = "updated_at";

    public static final String METHOD_SUBSCRIBE = "subscribe";
    public static final String METHOD_UNSUBSCRIBE = "unsubscribe";

    private SpotifyContract() {}
}
