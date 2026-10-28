package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WallpaperDecodeGuardTest {
    @Test public void retiredNativeDecodeMustFinishBeforeReplacementStarts() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService old = Executors.newSingleThreadExecutor();
        ExecutorService replacement = Executors.newSingleThreadExecutor();
        AtomicBoolean newEntered = new AtomicBoolean();
        try {
            Future<?> previous = old.submit(() -> WallpaperDecodeGuard.run(() -> {
                entered.countDown();
                // Model BitmapFactory continuing after shutdownNow interrupts its worker.
                while (true) {
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test release timed out");
                        break;
                    } catch (InterruptedException ignored) { }
                }
                return null;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            old.shutdownNow();
            Future<?> next = replacement.submit(() -> WallpaperDecodeGuard.run(() -> {
                newEntered.set(true);
                return null;
            }));
            try { next.get(100, TimeUnit.MILLISECONDS); fail("concurrent decode"); }
            catch (TimeoutException expected) { }
            assertFalse(newEntered.get());
            release.countDown();
            previous.get(5, TimeUnit.SECONDS);
            next.get(5, TimeUnit.SECONDS);
            assertTrue(newEntered.get());
        } finally {
            release.countDown();
            old.shutdownNow();
            replacement.shutdownNow();
        }
    }

    @Test public void interruptedQueuedWorkNeverStartsItsDecode() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicBoolean called = new AtomicBoolean();
        try {
            worker.submit(() -> {
                Thread.currentThread().interrupt();
                try {
                    WallpaperDecodeGuard.run(() -> { called.set(true); return null; });
                    fail("interrupted decode was admitted");
                } catch (InterruptedException expected) {
                    // Executor worker is disposable; do not alter the test runner's interrupt flag.
                } catch (Exception unexpected) {
                    throw new AssertionError(unexpected);
                }
            }).get(5, TimeUnit.SECONDS);
            assertFalse(called.get());
        } finally { worker.shutdownNow(); }
    }

    @Test public void failureReleasesGuardForNextImage() throws Exception {
        try {
            WallpaperDecodeGuard.run(() -> { throw new IllegalStateException("decode failure"); });
            fail("failure lost");
        } catch (IllegalStateException expected) { }
        assertEquals("next", WallpaperDecodeGuard.run(() -> "next"));
    }
}
