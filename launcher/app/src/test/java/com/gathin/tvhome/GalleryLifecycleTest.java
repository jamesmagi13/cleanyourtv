package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Real ownership/work policies with synthetic lifecycle events; no claim of Android UI coverage. */
public class GalleryLifecycleTest {
    @Test public void homeClockHomeHasOneOwnerDespiteLateHomeCallbacks() {
        GalleryOwners<String> owners = new GalleryOwners<>();
        owners.start("home", false);
        assertEquals("home", owners.owner());
        owners.start("clock", true);
        owners.start("home", false);
        assertEquals("clock", owners.owner());
        owners.stop("home");
        assertEquals("clock", owners.owner());
        owners.stop("clock");
        assertNull(owners.owner());
        owners.start("home", false);
        assertEquals("home", owners.owner());
    }

    @Test public void dreamOverResumedHomeReturnsOwnershipWhenDetached() {
        GalleryOwners<String> owners = new GalleryOwners<>();
        owners.start("home", false);
        owners.start("activity", true);
        owners.start("dream", true);
        assertEquals("dream", owners.owner());
        owners.stop("activity"); // late Activity pause must not stop Dream
        assertEquals("dream", owners.owner());
        owners.stop("dream");
        assertEquals("home", owners.owner());
        owners.stop("dream"); // stop + detach is idempotent
        assertEquals("home", owners.owner());
    }

    @Test public void onlyOneDecodeAndLiveHandoffKeepsItsTicket() {
        WallpaperWork work = new WallpaperWork();
        assertFalse(work.canLoad());
        work.start();
        long ticket = work.begin();
        assertFalse(work.canLoad());
        work.start(); // changing the visible host does not stop the shared worker
        assertFalse(work.canLoad());
        assertTrue(work.complete(ticket));
        assertTrue(work.canLoad());
    }

    @Test public void pauseResumeRejectsOldCompletionBeforeAllowingAnotherDecode() {
        WallpaperWork work = new WallpaperWork();
        work.start();
        long old = work.begin();
        work.stop();
        work.start();
        assertFalse(work.canLoad());
        assertFalse(work.complete(old));
        assertTrue(work.canLoad());
        assertTrue(work.complete(work.begin()));
    }

    @Test public void disposalRejectsCompletionAndCannotRestart() {
        WallpaperWork work = new WallpaperWork();
        work.start();
        long ticket = work.begin();
        work.close();
        work.start();
        assertFalse(work.complete(ticket));
        assertFalse(work.canLoad());
    }

    @Test public void failedDecodesAreSkippedWithoutChangingTheSourceCollection() {
        List<File> files = Arrays.asList(new File("01.jpg"), new File("02.jpg"), new File("03.jpg"));
        WallpaperWork work = new WallpaperWork();
        work.start();
        for (File expected : files) {
            File selected = WallpaperRotation.pick(work.readable(files), "", "sequential", new Random(1));
            assertEquals(expected, selected);
            assertTrue(work.complete(work.begin())); // decoder returned null, caller rejects this candidate
            work.reject(selected);
        }
        assertNull(WallpaperRotation.pick(work.readable(files), "", "sequential", new Random(1)));
        assertEquals(3, files.size()); // no filesystem changes or mutation of Config's collection
        work.stop();
        work.start();
        assertTrue(work.readable(files).isEmpty()); // no repeated decode loop on a host transition
        assertEquals(3, new WallpaperWork().readable(files).size()); // new session can retry repaired files
    }

    @Test public void survivingImageRemainsAvailableAfterAnotherFails() {
        File good = new File("good.jpg"), bad = new File("bad.jpg");
        WallpaperWork work = new WallpaperWork();
        work.reject(bad);
        List<File> files = work.readable(Arrays.asList(bad, good));
        assertEquals(good, WallpaperRotation.pick(files, "bad.jpg", "sequential", new Random(1)));
        assertEquals(good, WallpaperRotation.find(files, "good.jpg"));
    }
}
