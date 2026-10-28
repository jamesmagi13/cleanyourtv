package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One pending decode, with invalidation across a full pause or disposal. Main thread only. */
final class WallpaperWork {
    private final Set<File> failed = new HashSet<>();
    private long generation;
    private boolean active, closed, loading;

    void reject(File file) { failed.add(file); }

    List<File> readable(List<File> files) {
        if (failed.isEmpty()) return files;
        List<File> result = new ArrayList<>(files);
        result.removeAll(failed);
        return result;
    }

    void start() { if (!closed) active = true; }
    void stop() { active = false; generation++; }
    void close() { stop(); closed = true; }
    boolean canLoad() { return active && !closed && !loading; }
    long begin() { loading = true; return generation; }
    boolean complete(long ticket) {
        loading = false;
        return active && !closed && generation == ticket;
    }
}
