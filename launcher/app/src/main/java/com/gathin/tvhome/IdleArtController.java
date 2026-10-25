package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;

/**
 * Coordinates Idle Art Mode: after the TV sits untouched on Home for {@code idleSeconds},
 * fades the navigation UI (grid, dock, pills, focus highlight) to {@code opacityPercent}%
 * so the wallpaper/painting remains crisp and clear.
 *
 * Any user interaction (remote key, mouse, touch) immediately wakes the UI back to 100%
 * with zero lag, without consuming the event.
 */
final class IdleArtController {

    interface Host {
        /** Returns false if popups, dialogs, or reordering are active, or if window has no focus. */
        boolean canEnterIdleArt();
        /** Applies opacity to the navigation UI. */
        void applyIdleArtOpacity(float alpha, boolean animate, long durationMs);
    }

    interface Clock {
        void postDelayed(Runnable r, long delayMs);
        void removeCallbacks(Runnable r);
    }

    static Clock defaultClock(Handler handler) {
        return new Clock() {
            @Override
            public void postDelayed(Runnable r, long delayMs) {
                handler.postDelayed(r, delayMs);
            }

            @Override
            public void removeCallbacks(Runnable r) {
                handler.removeCallbacks(r);
            }
        };
    }

    private final int idleSeconds;
    private final int opacityPercent;
    private final boolean reducedMotion;
    private final Host host;
    private final Clock clock;
    private final boolean enabled;
    private boolean isIdle = false;
    private boolean running = false;

    private final Runnable enterIdleRunnable = this::onIdleTimeout;

    IdleArtController(int idleSeconds, int opacityPercent, boolean reducedMotion, Host host, Clock clock) {
        this.idleSeconds = Math.max(0, idleSeconds);
        this.opacityPercent = Math.max(0, Math.min(100, opacityPercent));
        this.reducedMotion = reducedMotion;
        this.host = host;
        this.clock = clock;
        this.enabled = this.idleSeconds > 0;
    }

    IdleArtController(int idleSeconds, int opacityPercent, boolean reducedMotion, Host host) {
        this(idleSeconds, opacityPercent, reducedMotion, host, defaultClock(new Handler(Looper.getMainLooper())));
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean isRunning() {
        return running;
    }

    boolean isIdle() {
        return isIdle;
    }

    int getIdleSeconds() {
        return idleSeconds;
    }

    int getOpacityPercent() {
        return opacityPercent;
    }

    void start() {
        if (!enabled) return;
        running = true;
        resetToNormal();
        scheduleCountdown();
    }

    void stop() {
        if (!enabled) return;
        running = false;
        clock.removeCallbacks(enterIdleRunnable);
        resetToNormal();
    }

    void onUserInteraction() {
        if (!enabled || !running) return;
        clock.removeCallbacks(enterIdleRunnable);
        if (isIdle) {
            resetToNormal();
        }
        scheduleCountdown();
    }

    void onWindowFocusChanged(boolean hasFocus) {
        if (!enabled || !running) return;
        if (hasFocus) {
            onUserInteraction();
        } else {
            clock.removeCallbacks(enterIdleRunnable);
            resetToNormal();
        }
    }

    private void scheduleCountdown() {
        clock.removeCallbacks(enterIdleRunnable);
        clock.postDelayed(enterIdleRunnable, idleSeconds * 1000L);
    }

    private void onIdleTimeout() {
        if (!enabled || !running) return;
        if (!host.canEnterIdleArt()) {
            scheduleCountdown();
            return;
        }
        isIdle = true;
        float alpha = opacityPercent / 100f;
        long duration = reducedMotion ? 0L : 400L;
        host.applyIdleArtOpacity(alpha, !reducedMotion, duration);
    }

    private void resetToNormal() {
        isIdle = false;
        host.applyIdleArtOpacity(1.0f, false, 0L);
    }
}
