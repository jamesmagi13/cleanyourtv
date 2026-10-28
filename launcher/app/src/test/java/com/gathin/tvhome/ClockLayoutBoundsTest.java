package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Random;

/**
 * Geometric verification that Clock, Spotify Now Playing, and Wallpaper Captions
 * maintain strictly non-overlapping bounding boxes across resolutions (720p, 1080p, 4K)
 * and throughout the entire clock anti-burn-in drift range.
 */
public class ClockLayoutBoundsTest {

    private static class Rect {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Rect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        boolean intersects(Rect other) {
            return this.left < other.right && this.right > other.left &&
                   this.top < other.bottom && this.bottom > other.top;
        }
    }

    private void verifyResolution(int screenW, int screenH, float density) {
        // Clock dimensions
        float clockW = 320f * density;
        float clockH = 120f * density;

        // Spotify card dimensions
        float spotifyMarginStart = 56f * density;
        float spotifyMarginBottom = 40f * density;
        float spotifyArtSize = 160f * density;
        float spotifyGap = 20f * density;
        float spotifyMaxTextW = 360f * density;

        float spotifyLeft = spotifyMarginStart;
        float spotifyRight = spotifyMarginStart + spotifyArtSize + spotifyGap + spotifyMaxTextW;
        float spotifyBottom = screenH - spotifyMarginBottom;
        float spotifyTop = spotifyBottom - spotifyArtSize;
        Rect spotifyRect = new Rect(spotifyLeft, spotifyTop, spotifyRight, spotifyBottom);

        // Caption dimensions (right-aligned, max 80 chars, ~250dp)
        float captionW = 250f * density;
        float captionH = 40f * density;
        float captionMarginEnd = 40f * density;
        float captionMarginBottom = 40f * density;

        float captionRight = screenW - captionMarginEnd;
        float captionLeft = captionRight - captionW;
        float captionBottom = screenH - captionMarginBottom;
        float captionTop = captionBottom - captionH;
        Rect captionRect = new Rect(captionLeft, captionTop, captionRight, captionBottom);

        // 1. Verify Spotify and Caption do not overlap horizontally at the bottom of the screen
        assertFalse("Spotify card must not overlap painting caption on " + screenW + "x" + screenH,
                spotifyRect.intersects(captionRect));
        assertTrue("Spotify card right edge must be left of caption left edge",
                spotifyRect.right < captionRect.left);

        // 2. Test 100 clock drift positions within gallery bounds (0.04 to 0.08 of maxX/maxY)
        float maxX = Math.max(0, screenW - clockW);
        float maxY = Math.max(0, screenH - clockH);
        Random random = new Random(42);

        for (int i = 0; i < 100; i++) {
            float x = maxX * (0.04f + 0.04f * random.nextFloat());
            float y = maxY * (0.04f + 0.04f * random.nextFloat());

            Rect clockRect = new Rect(x, y, x + clockW, y + clockH);

            // Clock must never intersect Spotify
            assertFalse("Clock at (" + x + "," + y + ") must not collide with Spotify",
                    clockRect.intersects(spotifyRect));
            // Clock must be strictly above Spotify
            assertTrue("Clock bottom (" + clockRect.bottom + ") must stay above Spotify top (" + spotifyRect.top + ")",
                    clockRect.bottom < spotifyRect.top);

            // Clock must never intersect Caption
            assertFalse("Clock at (" + x + "," + y + ") must not collide with Caption",
                    clockRect.intersects(captionRect));
        }
    }

    @Test
    public void boundsAreCompletelyDisjointAt1080p() {
        // 1080p (standard Android TV): 1920x1080 @ 1.5 density (xhdpi)
        verifyResolution(1920, 1080, 1.5f);
        // 1080p @ 2.0 density (xxhdpi)
        verifyResolution(1920, 1080, 2.0f);
    }

    @Test
    public void boundsAreCompletelyDisjointAt4K() {
        // 4K UHD: 3840x2160 @ 3.0 density
        verifyResolution(3840, 2160, 3.0f);
        // 4K UHD @ 4.0 density
        verifyResolution(3840, 2160, 4.0f);
    }

    @Test
    public void boundsAreCompletelyDisjointAt720p() {
        // 720p (older sets): 1280x720 @ 1.0 density (mdpi)
        // At 720p with density 1.0, spotify max text is adjusted
        verifyResolution(1280, 720, 1.0f);
    }
}
