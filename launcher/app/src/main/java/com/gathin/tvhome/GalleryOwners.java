package com.gathin.tvhome;

import java.util.LinkedHashMap;
import java.util.Map;

/** Main-thread ownership policy. A dream can overlap a resumed Home; the clock wins. */
final class GalleryOwners<T> {
    private final Map<T, Boolean> active = new LinkedHashMap<>();

    void start(T host, boolean clock) {
        active.remove(host);
        active.put(host, clock);
    }

    void stop(T host) { active.remove(host); }

    T owner() {
        T result = null;
        boolean clock = false;
        for (Map.Entry<T, Boolean> entry : active.entrySet()) {
            if (!clock || entry.getValue()) {
                result = entry.getKey();
                clock = entry.getValue();
            }
        }
        return result;
    }
}
