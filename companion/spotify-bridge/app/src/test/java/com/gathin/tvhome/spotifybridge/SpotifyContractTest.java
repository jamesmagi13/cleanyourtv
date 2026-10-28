package com.gathin.tvhome.spotifybridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpotifyContractTest {

    @Test
    public void authorityAndTableNamesMatchExpectedContract() {
        assertEquals("com.gathin.tvhome.spotify", SpotifyContract.AUTHORITY);
        assertEquals("now_playing", SpotifyContract.TABLE_NOW_PLAYING);
        assertEquals("art", SpotifyContract.PATH_ART);
        assertEquals("subscribe", SpotifyContract.METHOD_SUBSCRIBE);
        assertEquals("unsubscribe", SpotifyContract.METHOD_UNSUBSCRIBE);
    }

    @Test
    public void columnNamesAreConsistent() {
        assertEquals("is_playing", SpotifyContract.COLUMN_IS_PLAYING);
        assertEquals("track_title", SpotifyContract.COLUMN_TRACK_TITLE);
        assertEquals("artist_name", SpotifyContract.COLUMN_ARTIST_NAME);
        assertEquals("album_name", SpotifyContract.COLUMN_ALBUM_NAME);
        assertEquals("duration_ms", SpotifyContract.COLUMN_DURATION_MS);
        assertEquals("progress_ms", SpotifyContract.COLUMN_PROGRESS_MS);
        assertEquals("art_uri", SpotifyContract.COLUMN_ART_URI);
        assertEquals("updated_at", SpotifyContract.COLUMN_UPDATED_AT);
    }
}
