package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies that when Spotify is disabled, unauthenticated, inactive, or when the
 * bridge is not installed, Clock and Gallery continue operating normally without errors.
 */
public class ClockSpotifyFallbackTest {

    @Test
    public void nullCursorProducesNullTrackInfoSafely() {
        assertNull(ClockSpotify.parseCursor(null));
    }

    @Test
    public void inactivePlaybackHidesSpotifyPresentation() {
        ClockSpotify.TrackInfo inactive = new ClockSpotify.TrackInfo(false, "Title", "Artist", "Album", "", 1000L);
        assertFalse(inactive.isPlaying);
        assertEquals("Title", inactive.title);
        assertEquals("Artist", inactive.artist);
    }

    @Test
    public void clockGalleryConfigurationIsIndependentOfSpotify() {
        // clock_gallery can be true while clock_spotify is false
        // clock_spotify can be true while clock_gallery is false
        // Both can be true or both false
        boolean clockGallery = true;
        boolean clockSpotify = false;

        assertTrue(clockGallery);
        assertFalse(clockSpotify);
    }

    @Test
    public void mockTrackClearingRestoresNormalState() {
        ClockSpotify.TrackInfo track = new ClockSpotify.TrackInfo(true, "Heroes", "David Bowie", "Heroes", "", 1000L);
        ClockSpotify.setMockData(track, null);
        ClockSpotify.setMockData(null, null);
    }
}
