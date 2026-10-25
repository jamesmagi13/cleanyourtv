package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests state transitions, timeout handling, and cancellation in IdleArtController. */
public class IdleArtControllerTest {

    private static class FakeClock implements IdleArtController.Clock {
        static class Scheduled {
            final Runnable runnable;
            final long delayMs;
            Scheduled(Runnable r, long d) { this.runnable = r; this.delayMs = d; }
        }
        final List<Scheduled> scheduled = new ArrayList<>();
        final List<Runnable> cancelled = new ArrayList<>();

        @Override
        public void postDelayed(Runnable r, long delayMs) {
            scheduled.add(new Scheduled(r, delayMs));
        }

        @Override
        public void removeCallbacks(Runnable r) {
            cancelled.add(r);
            scheduled.removeIf(s -> s.runnable == r);
        }

        void runNext() {
            if (!scheduled.isEmpty()) {
                Scheduled s = scheduled.remove(0);
                s.runnable.run();
            }
        }
    }

    private static class FakeHost implements IdleArtController.Host {
        boolean canEnter = true;
        float lastAlpha = -1f;
        boolean lastAnimate = false;
        long lastDuration = -1L;
        int applyCount = 0;

        @Override
        public boolean canEnterIdleArt() {
            return canEnter;
        }

        @Override
        public void applyIdleArtOpacity(float alpha, boolean animate, long durationMs) {
            lastAlpha = alpha;
            lastAnimate = animate;
            lastDuration = durationMs;
            applyCount++;
        }
    }

    @Test
    public void disabledByDefaultWhenZeroSeconds() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(0, 30, false, host, clock);

        assertFalse(ctrl.isEnabled());
        assertFalse(ctrl.isIdle());

        ctrl.start();
        assertTrue(clock.scheduled.isEmpty());

        ctrl.onUserInteraction();
        assertTrue(clock.scheduled.isEmpty());

        ctrl.onWindowFocusChanged(true);
        assertTrue(clock.scheduled.isEmpty());

        ctrl.stop();
        assertTrue(clock.cancelled.isEmpty());
        assertEquals(0, host.applyCount);
    }

    @Test
    public void normalCountdownAndEnterIdle() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        assertTrue(ctrl.isEnabled());
        ctrl.start();

        assertEquals(1, host.applyCount);
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);

        // Timer fires
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
        assertTrue(host.lastAnimate);
        assertEquals(400L, host.lastDuration);
    }

    @Test
    public void reducedMotionBypassesAnimation() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, true, host, clock);

        ctrl.start();
        clock.runNext();

        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
        assertFalse(host.lastAnimate);
        assertEquals(0L, host.lastDuration);
    }

    @Test
    public void userInteractionWakesImmediately() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        clock.runNext();
        assertTrue(ctrl.isIdle());

        // Any user interaction must immediately reset to 1.0f without animation delay
        ctrl.onUserInteraction();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertFalse(host.lastAnimate);
        assertEquals(0L, host.lastDuration);

        // Next countdown scheduled
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);
    }

    @Test
    public void userInteractionWhenNotIdleDoesNotTriggerRedundantApply() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        int count = host.applyCount;

        ctrl.onUserInteraction();
        assertEquals(count, host.applyCount);
    }

    @Test
    public void suppressedWhenHostCannotEnter() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        host.canEnter = false; // e.g. popup is open
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        clock.runNext();

        // Must NOT dim UI while host is suppressed
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);

        // Recheck timer scheduled
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);
    }

    @Test
    public void stopRestoresAlphaAndClearsCallbacks() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        clock.runNext();
        assertTrue(ctrl.isIdle());

        ctrl.stop();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertTrue(clock.scheduled.isEmpty());
    }

    @Test
    public void windowFocusHandling() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        clock.runNext();
        assertTrue(ctrl.isIdle());

        // Lost focus (e.g. system overlay or switching app)
        ctrl.onWindowFocusChanged(false);
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertTrue(clock.scheduled.isEmpty());

        // Regained focus
        ctrl.onWindowFocusChanged(true);
        assertEquals(1, clock.scheduled.size());
    }

    @Test
    public void rapidRepeatedInteractionsNeverEnterIdle() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        // Simulate rapid typing or navigation every 5 seconds
        for (int i = 0; i < 10; i++) {
            assertEquals(1, clock.scheduled.size());
            assertEquals(15000L, clock.scheduled.get(0).delayMs);
            ctrl.onUserInteraction();
            assertFalse(ctrl.isIdle());
        }
        assertEquals(1, clock.scheduled.size());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
    }

    @Test
    public void delayedHostUnblockEntersIdleOnSubsequentAttempt() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        host.canEnter = false; // Popup open
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        clock.runNext(); // Attempt 1: blocked
        assertFalse(ctrl.isIdle());
        assertEquals(1, clock.scheduled.size());

        // Popup closed
        host.canEnter = true;
        clock.runNext(); // Attempt 2: allowed
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
    }

    @Test
    public void multipleStartsDoNotDuplicateTimers() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        ctrl.start();
        ctrl.start();
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);
    }

    @Test
    public void boundaryValueClamping() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();

        // Negative seconds => disabled
        IdleArtController negativeSeconds = new IdleArtController(-10, 30, false, host, clock);
        assertFalse(negativeSeconds.isEnabled());
        assertEquals(0, negativeSeconds.getIdleSeconds());

        // Negative opacity => clamped to 0
        IdleArtController negativeOpacity = new IdleArtController(15, -20, false, host, clock);
        assertEquals(0, negativeOpacity.getOpacityPercent());
        negativeOpacity.start();
        clock.runNext();
        assertEquals(0.0f, host.lastAlpha, 0.001f);

        // Opacity > 100 => clamped to 100
        IdleArtController excessiveOpacity = new IdleArtController(15, 250, false, host, clock);
        assertEquals(100, excessiveOpacity.getOpacityPercent());
        excessiveOpacity.start();
        clock.runNext();
        assertEquals(1.0f, host.lastAlpha, 0.001f);
    }

    @Test
    public void appSwitchingLifecycleSimulation() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        // 1. User on Home -> becomes idle
        ctrl.start();
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);

        // 2. User opens an external app (e.g. YouTube) -> onPause
        ctrl.stop();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertTrue(clock.scheduled.isEmpty());

        // 3. User returns to Home -> onResume
        ctrl.start();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertEquals(1, clock.scheduled.size());

        // 4. Stays untouched again -> becomes idle
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
    }

    @Test
    public void stopThenInteractionDoesNotSchedule() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        assertTrue(ctrl.isRunning());
        assertEquals(1, clock.scheduled.size());

        ctrl.stop();
        assertFalse(ctrl.isRunning());
        assertTrue(clock.scheduled.isEmpty());

        // Interaction while stopped MUST NOT schedule any timers
        ctrl.onUserInteraction();
        assertFalse(ctrl.isRunning());
        assertTrue(clock.scheduled.isEmpty());
    }

    @Test
    public void stopThenFocusRegainDoesNotSchedule() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        assertTrue(ctrl.isRunning());
        assertEquals(1, clock.scheduled.size());

        ctrl.stop();
        assertFalse(ctrl.isRunning());
        assertTrue(clock.scheduled.isEmpty());

        // Gaining window focus while stopped MUST NOT schedule any timers
        ctrl.onWindowFocusChanged(true);
        assertFalse(ctrl.isRunning());
        assertTrue(clock.scheduled.isEmpty());
    }

    @Test
    public void startAfterStopRestoresCountdown() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        assertTrue(ctrl.isRunning());

        ctrl.stop();
        assertFalse(ctrl.isRunning());
        assertTrue(clock.scheduled.isEmpty());

        // New start() call restores running state and countdown
        ctrl.start();
        assertTrue(ctrl.isRunning());
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);

        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
    }

    @Test
    public void interactionDuringActiveFadeCancelsAndSnaps() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        // Transition to idle triggers fade animation
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
        assertTrue(host.lastAnimate);
        assertEquals(400L, host.lastDuration);

        // Immediate user input while fade is active snaps UI to 1.0f with no duration
        ctrl.onUserInteraction();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertFalse(host.lastAnimate);
        assertEquals(0L, host.lastDuration);
    }

    @Test
    public void timeoutWhileNotRunningDoesNotTriggerFade() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        FakeClock.Scheduled s = clock.scheduled.get(0);
        ctrl.stop();

        // If runnable somehow executed after stop
        s.runnable.run();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
    }

    @Test
    public void actionDownAndUpResetCountdownCleanly() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        int initialApplies = host.applyCount;

        // Simulate ACTION_DOWN event
        ctrl.onUserInteraction();
        assertEquals(1, clock.scheduled.size());
        assertEquals(initialApplies, host.applyCount);

        // Simulate matching ACTION_UP event shortly after
        ctrl.onUserInteraction();
        assertEquals(1, clock.scheduled.size());
        assertEquals(initialApplies, host.applyCount);
        assertFalse(ctrl.isIdle());
    }

    @Test
    public void dpadBurstNavigationKeepsSingleTimerAndFullAlpha() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        // Simulate rapid DPAD burst navigation across tiles (e.g. 10 rapid arrow presses)
        for (int i = 0; i < 10; i++) {
            ctrl.onUserInteraction();
            assertEquals(1, clock.scheduled.size());
            assertEquals(15000L, clock.scheduled.get(0).delayMs);
            assertFalse(ctrl.isIdle());
            assertEquals(1.0f, host.lastAlpha, 0.001f);
        }
        assertEquals(1, host.applyCount);
    }

    @Test
    public void menuOpenSuppressesAndClosingAllowsIdleAfterNewCountdown() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        assertEquals(1, clock.scheduled.size());

        // User opens tile menu or app library
        host.canEnter = false;

        // 15 seconds expire while menu is open
        clock.runNext();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        // Rescheduled
        assertEquals(1, clock.scheduled.size());

        // User interacts inside menu
        ctrl.onUserInteraction();
        assertEquals(1, clock.scheduled.size());

        // Menu dismissed
        host.canEnter = true;

        // Next 15s cycle completes untouched
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
    }

    @Test
    public void dockToGridFocusTransitionsKeepUiAwake() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();

        // User navigates from dock tile (down) to grid tile
        ctrl.onUserInteraction();
        assertFalse(ctrl.isIdle());

        // User navigates left, right within grid
        ctrl.onUserInteraction();
        ctrl.onUserInteraction();
        assertEquals(1, clock.scheduled.size());

        // User navigates back up to topButtons
        ctrl.onUserInteraction();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
    }

    @Test
    public void consecutiveFadeInterruptionSnapsCleanly() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();

        // Cycle 1: Enter idle, interrupted mid-fade
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
        assertTrue(host.lastAnimate);

        ctrl.onUserInteraction();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertFalse(host.lastAnimate);
        assertEquals(0L, host.lastDuration);

        // Cycle 2: Wait 15s, enter idle again, interrupted again
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
        assertTrue(host.lastAnimate);

        ctrl.onUserInteraction();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertFalse(host.lastAnimate);
        assertEquals(0L, host.lastDuration);
    }

    @Test
    public void appSwitchLongSessionLifecycleIntegrity() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        clock.runNext();
        assertTrue(ctrl.isIdle());

        // App switches away (e.g. HDMI or Netflix)
        ctrl.stop();
        assertFalse(ctrl.isRunning());
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertTrue(clock.scheduled.isEmpty());

        // Spurious events during external app playback must be completely ignored
        ctrl.onUserInteraction();
        ctrl.onWindowFocusChanged(true);
        assertTrue(clock.scheduled.isEmpty());
        assertFalse(ctrl.isRunning());

        // Returning to Home (onResume)
        ctrl.start();
        assertTrue(ctrl.isRunning());
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);
        assertEquals(1.0f, host.lastAlpha, 0.001f);
    }

    @Test
    public void heldKeyContinuousRepeatsKeepSingleTimerAndPreventIdle() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        int initialApplies = host.applyCount;

        // 1. Initial ACTION_DOWN (repeatCount = 0)
        ctrl.onUserInteraction();
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);
        assertFalse(ctrl.isIdle());
        assertEquals(initialApplies, host.applyCount);

        // 2. Button held down generating 30 repeat events (repeatCount 1..30)
        for (int repeat = 1; repeat <= 30; repeat++) {
            ctrl.onUserInteraction();
            // Invariant: queue size never grows, callback is always rescheduled to +15s
            assertEquals(1, clock.scheduled.size());
            assertEquals(15000L, clock.scheduled.get(0).delayMs);
            assertFalse(ctrl.isIdle());
            assertEquals(1.0f, host.lastAlpha, 0.001f);
        }

        // 3. User releases button: ACTION_UP
        ctrl.onUserInteraction();
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);
        assertFalse(ctrl.isIdle());

        // 4. Inactivity for 15 seconds after release -> Idle Art triggers
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
        assertTrue(host.lastAnimate);
    }

    @Test
    public void userInteractionDuringFadeSnapsImmediatelyAndRestoresFullAlpha() {
        FakeClock clock = new FakeClock();
        FakeHost host = new FakeHost();
        IdleArtController ctrl = new IdleArtController(15, 30, false, host, clock);

        ctrl.start();
        // Enter idle
        clock.runNext();
        assertTrue(ctrl.isIdle());
        assertEquals(0.30f, host.lastAlpha, 0.001f);
        assertTrue(host.lastAnimate);

        // Interaction occurs mid-fade (e.g. user presses DPAD or Back)
        ctrl.onUserInteraction();
        assertFalse(ctrl.isIdle());
        assertEquals(1.0f, host.lastAlpha, 0.001f);
        assertFalse(host.lastAnimate); // Snaps immediately without animation delay
        assertEquals(0L, host.lastDuration);

        // Rescheduled for next 15s
        assertEquals(1, clock.scheduled.size());
        assertEquals(15000L, clock.scheduled.get(0).delayMs);
    }
}
