package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockSpotifyTest {

    @Test
    public void trackInfoPreservesFieldsAndDefaults() {
        ClockSpotify.TrackInfo track = new ClockSpotify.TrackInfo(
                true, "Starman", "David Bowie", "Ziggy Stardust", "content://art/1", 123456L);
        assertTrue(track.isPlaying);
        assertEquals("Starman", track.title);
        assertEquals("David Bowie", track.artist);
        assertEquals("Ziggy Stardust", track.album);
        assertEquals("content://art/1", track.artUri);
        assertEquals("", track.spotifyUri);
        assertEquals(123456L, track.updatedAt);

        ClockSpotify.TrackInfo trackWithUri = new ClockSpotify.TrackInfo(
                true, "Starman", "David Bowie", "Ziggy Stardust", "content://art/1", "spotify:track:123", 123456L);
        assertEquals("spotify:track:123", trackWithUri.spotifyUri);

        ClockSpotify.TrackInfo empty = new ClockSpotify.TrackInfo(false, null, null, null, null, 0L);
        assertFalse(empty.isPlaying);
        assertEquals("", empty.title);
        assertEquals("", empty.artist);
        assertEquals("", empty.album);
        assertEquals("", empty.artUri);
        assertEquals(0L, empty.updatedAt);
    }

    @Test
    public void mockDataSetsAndClears() {
        ClockSpotify.TrackInfo track = new ClockSpotify.TrackInfo(
                true, "Heroes", "David Bowie", "Heroes", "", 1000L);
        ClockSpotify.setMockData(track, null);
        ClockSpotify.setMockData(null, null);
    }

    @Test
    public void authorityAndUriAreConsistent() {
        assertEquals("com.gathin.tvhome.spotify", ClockSpotify.AUTHORITY);
        assertEquals("content://com.gathin.tvhome.spotify/now_playing", ClockSpotify.NOW_PLAYING_URI_STRING);
    }
}
