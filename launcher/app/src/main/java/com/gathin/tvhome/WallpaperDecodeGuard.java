package com.gathin.tvhome;

import java.util.concurrent.Callable;

/** A native decode may ignore interruption. Serialize even across retired/new player sessions. */
final class WallpaperDecodeGuard {
    private WallpaperDecodeGuard() { }

    static synchronized <T> T run(Callable<T> decode) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        return decode.call();
    }
}
