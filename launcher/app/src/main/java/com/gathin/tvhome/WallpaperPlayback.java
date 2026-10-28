package com.gathin.tvhome;

/** A visible host's lifecycle, implemented by a local player or an opt-in gallery lease. */
interface WallpaperPlayback {
    void start();
    void stop();
    void update();
    void releaseForMemoryPressure();
    void close();
}
