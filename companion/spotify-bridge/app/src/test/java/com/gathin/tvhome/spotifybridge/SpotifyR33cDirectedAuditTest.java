package com.gathin.tvhome.spotifybridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Directed deterministic test suite verifying the three R3.3c audit requirements:
 * 1. Two real instances of SpotifyTokenStore on the same shared storage with delayed refresh and OAuth logout.
 * 2. Subscription, unsubscription and Binder death concurrent with playback publication without deadlock.
 * 3. Third HTTP error in-flight followed by stop(), with no subsequent modification to Provider state.
 */
public class SpotifyR33cDirectedAuditTest {

    private File tempCacheDir;

    @Before
    public void setUp() throws Exception {
        tempCacheDir = Files.createTempDirectory("spotify_r33c_audit_cache").toFile();
        SpotifyNowPlayingProvider.resetSubscribersForTesting();
        SpotifyNowPlayingProvider.clearPlaybackState(null);
        SpotifyTokenStore.resetSessionVersionForTesting();
    }

    @After
    public void tearDown() {
        SpotifyNowPlayingProvider.resetSubscribersForTesting();
        SpotifyNowPlayingProvider.clearPlaybackState(null);
        if (tempCacheDir != null && tempCacheDir.exists()) {
            File[] files = tempCacheDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            tempCacheDir.delete();
        }
    }

    /**
     * Thread-safe in-memory SharedPreferences implementation simulating Android's SharedPreferencesImpl.
     */
    private static class FakeSharedPreferences implements android.content.SharedPreferences {
        private final Map<String, Object> map = new HashMap<>();

        @Override public synchronized Map<String, ?> getAll() { return new HashMap<>(map); }
        @Override public synchronized String getString(String key, String defValue) {
            Object val = map.get(key);
            return val instanceof String ? (String) val : defValue;
        }
        @Override public synchronized java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return defValues; }
        @Override public synchronized int getInt(String key, int defValue) {
            Object val = map.get(key);
            return val instanceof Integer ? (Integer) val : defValue;
        }
        @Override public synchronized long getLong(String key, long defValue) {
            Object val = map.get(key);
            return val instanceof Long ? (Long) val : defValue;
        }
        @Override public synchronized float getFloat(String key, float defValue) { return defValue; }
        @Override public synchronized boolean getBoolean(String key, boolean defValue) { return defValue; }
        @Override public synchronized boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return new FakeEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private class FakeEditor implements Editor {
            private final Map<String, Object> changes = new HashMap<>();
            private boolean clearRequested = false;

            @Override public synchronized Editor putString(String key, String value) { changes.put(key, value); return this; }
            @Override public synchronized Editor putStringSet(String key, java.util.Set<String> values) { return this; }
            @Override public synchronized Editor putInt(String key, int value) { changes.put(key, value); return this; }
            @Override public synchronized Editor putLong(String key, long value) { changes.put(key, value); return this; }
            @Override public synchronized Editor putFloat(String key, float value) { return this; }
            @Override public synchronized Editor putBoolean(String key, boolean value) { return this; }
            @Override public synchronized Editor remove(String key) { changes.put(key, this); return this; }
            @Override public synchronized Editor clear() { clearRequested = true; return this; }

            @Override
            public synchronized boolean commit() {
                synchronized (FakeSharedPreferences.this) {
                    if (clearRequested) map.clear();
                    for (Map.Entry<String, Object> entry : changes.entrySet()) {
                        if (entry.getValue() == this) map.remove(entry.getKey());
                        else map.put(entry.getKey(), entry.getValue());
                    }
                }
                return true;
            }

            @Override public void apply() { commit(); }
        }
    }

    private static class MockTestBinder implements android.os.IBinder {
        private DeathRecipient recipient;

        @Override public void linkToDeath(DeathRecipient r, int flags) { this.recipient = r; }
        @Override
        public boolean unlinkToDeath(DeathRecipient r, int flags) {
            if (this.recipient == r) {
                this.recipient = null;
                return true;
            }
            return false;
        }
        public void simulateBinderDeath() {
            if (recipient != null) {
                recipient.binderDied();
            }
        }
        @Override public String getInterfaceDescriptor() { return "mock"; }
        @Override public boolean pingBinder() { return true; }
        @Override public boolean isBinderAlive() { return true; }
        @Override public android.os.IInterface queryLocalInterface(String descriptor) { return null; }
        @Override public void dump(java.io.FileDescriptor fd, String[] args) {}
        @Override public void dumpAsync(java.io.FileDescriptor fd, String[] args) {}
        @Override public boolean transact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) { return false; }
    }

    private static class MockHttpConn extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;

        MockHttpConn(URL url, int responseCode, String responseBody) {
            super(url);
            this.responseCode = responseCode;
            this.responseBody = responseBody != null ? responseBody : "";
        }

        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return responseCode; }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseCode >= 400) throw new IOException("HTTP " + responseCode);
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override public OutputStream getOutputStream() { return new java.io.ByteArrayOutputStream(); }
    }

    /**
     * Requirement 1: Two real instances of SpotifyTokenStore over the exact same storage.
     * Tests that a logout/clear on instance B invalidates an in-flight refresh or in-flight OAuth on instance A.
     */
    @Test
    public void testTwoTokenStoreInstancesOnSharedStorageWithDelayedRefreshAndOAuthLogout() throws Exception {
        FakeSharedPreferences sharedStorage = new FakeSharedPreferences();
        SpotifyTokenStore pollerStore = new SpotifyTokenStore(sharedStorage);
        SpotifyTokenStore authStore = new SpotifyTokenStore(sharedStorage);

        // 1. Initial valid login stored via authStore
        authStore.saveTokens("client_alpha", "access_initial", "refresh_initial", 3600);
        assertTrue(pollerStore.hasRefreshToken());
        assertEquals("client_alpha", pollerStore.getClientId());
        assertEquals("access_initial", pollerStore.getAccessToken());

        // 2. Scenario A: In-flight refresh on pollerStore while authStore clears session
        CountDownLatch refreshInFlightLatch = new CountDownLatch(1);
        CountDownLatch logoutDoneLatch = new CountDownLatch(1);
        AtomicBoolean pollerSavedTokens = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> pollerThread = executor.submit(() -> {
                long expectedVersion = pollerStore.getSessionVersion();
                refreshInFlightLatch.countDown();
                try {
                    assertTrue(logoutDoneLatch.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Refresh completes late: attempts to save tokens on pollerStore
                boolean saved = pollerStore.saveTokensIfVersion(
                        expectedVersion,
                        "client_alpha",
                        "new_late_access",
                        "new_late_refresh",
                        3600
                );
                pollerSavedTokens.set(saved);
            });

            Future<?> authThread = executor.submit(() -> {
                try {
                    assertTrue(refreshInFlightLatch.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // User clicks disconnect on authStore
                authStore.clear();
                logoutDoneLatch.countDown();
            });

            pollerThread.get(5, TimeUnit.SECONDS);
            authThread.get(5, TimeUnit.SECONDS);

            assertFalse("Late refresh must be rejected when session was cleared by another store instance", pollerSavedTokens.get());
            assertNull("pollerStore tokens must remain null", pollerStore.getAccessToken());
            assertNull("authStore tokens must remain null", authStore.getAccessToken());
            assertFalse("pollerStore hasRefreshToken must be false", pollerStore.hasRefreshToken());
            assertFalse("authStore hasRefreshToken must be false", authStore.hasRefreshToken());

            // 3. Scenario B: In-flight OAuth token exchange on authStore while clear occurs on pollerStore
            CountDownLatch oauthInFlightLatch = new CountDownLatch(1);
            CountDownLatch pollerClearDoneLatch = new CountDownLatch(1);
            AtomicBoolean oauthSavedTokens = new AtomicBoolean(false);

            Future<?> oauthWorker = executor.submit(() -> {
                long expectedOauthVersion = authStore.getSessionVersion();
                oauthInFlightLatch.countDown();
                try {
                    assertTrue(pollerClearDoneLatch.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // OAuth callback arrives late
                boolean saved = authStore.saveTokensIfVersion(
                        expectedOauthVersion,
                        "client_beta",
                        "oauth_access",
                        "oauth_refresh",
                        3600
                );
                oauthSavedTokens.set(saved);
            });

            Future<?> clearWorker = executor.submit(() -> {
                try {
                    assertTrue(oauthInFlightLatch.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Invalidation occurs on pollerStore (e.g. invalid_grant or session reset)
                pollerStore.clear();
                pollerClearDoneLatch.countDown();
            });

            oauthWorker.get(5, TimeUnit.SECONDS);
            clearWorker.get(5, TimeUnit.SECONDS);

            assertFalse("OAuth tokens must be rejected when session was cleared on another instance", oauthSavedTokens.get());
            assertNull("authStore access token must remain null", authStore.getAccessToken());
            assertNull("pollerStore access token must remain null", pollerStore.getAccessToken());
            assertFalse("authStore hasRefreshToken must be false", authStore.hasRefreshToken());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Requirement 2: High-concurrency stress test with subscribe/unsubscribe and Binder death
     * executed concurrently with playback publication and executePoll without deadlock.
     */
    @Test(timeout = 10000)
    public void testSubscriptionChurnAndBinderDeathConcurrentWithPublicationHasZeroDeadlock() throws Exception {
        FakeSharedPreferences sharedStorage = new FakeSharedPreferences();
        SpotifyTokenStore store = new SpotifyTokenStore(sharedStorage);
        store.saveTokens("client", "access", "refresh", 3600);

        String trackJson = "{\"is_playing\": true, \"item\": {\"name\": \"Stress Track\", \"artists\": [{\"name\": \"Artist\"}], \"album\": {\"name\": \"Album\", \"images\": []}}, \"progress_ms\": 500}";
        SpotifyApiClient client = new SpotifyApiClient(url -> new MockHttpConn(new URL(url), 200, trackJson));
        SpotifyPoller poller = new SpotifyPoller(null, client, store, null, false, tempCacheDir, () -> {});

        int iterations = 150;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        try {
            // Worker 1: Subscription and Unsubscription churn
            Future<?> subWorker = executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {}
                for (int i = 0; i < iterations; i++) {
                    MockTestBinder binder = new MockTestBinder();
                    SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, binder, poller);
                    SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_UNSUBSCRIBE, binder, poller);
                }
            });

            // Worker 2: Binder death events
            Future<?> deathWorker = executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {}
                for (int i = 0; i < iterations; i++) {
                    MockTestBinder binder = new MockTestBinder();
                    SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, binder, poller);
                    binder.simulateBinderDeath();
                }
            });

            // Worker 3: Concurrent publication & poller execution
            Future<?> pubWorker = executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {}
                for (int i = 0; i < iterations; i++) {
                    SpotifyNowPlayingProvider.setPlaybackState(
                            i % 2 == 0,
                            "Track " + i,
                            "Artist " + i,
                            "Album " + i,
                            "spotify:track:" + i,
                            3000L,
                            1000L,
                            false
                    );
                    poller.executePoll();
                }
            });

            startLatch.countDown();

            // All workers must complete without hanging in deadlock
            subWorker.get(5, TimeUnit.SECONDS);
            deathWorker.get(5, TimeUnit.SECONDS);
            pubWorker.get(5, TimeUnit.SECONDS);

            // Cleanup check
            SpotifyNowPlayingProvider.resetSubscribersForTesting();
            assertEquals("Subscribers must be 0 after reset", 0, SpotifyNowPlayingProvider.getSubscriberCount());
        } finally {
            poller.destroy();
            executor.shutdownNow();
        }
    }

    /**
     * Requirement 3: Third consecutive HTTP error in flight, followed by stop(),
     * must NOT alter the Provider state or expire active playback metadata.
     */
    @Test(timeout = 5000)
    public void testThirdHttpErrorInFlightFollowedByStopDoesNotAlterProviderState() throws Exception {
        FakeSharedPreferences sharedStorage = new FakeSharedPreferences();
        SpotifyTokenStore store = new SpotifyTokenStore(sharedStorage);
        store.saveTokens("client", "valid_access", "valid_refresh", 3600);

        // 1. Establish initial playing state in Provider
        SpotifyNowPlayingProvider.setPlaybackState(
                true,
                "Preserved Track Title",
                "Preserved Artist",
                "Preserved Album",
                "spotify:track:preserved",
                240000L,
                60000L,
                true
        );
        assertTrue(SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);
        assertEquals("Preserved Track Title", SpotifyNowPlayingProvider.getPlaybackSnapshot().trackTitle);

        CountDownLatch thirdErrorInFlightLatch = new CountDownLatch(1);
        CountDownLatch stopCompletedLatch = new CountDownLatch(1);
        AtomicInteger networkCallIndex = new AtomicInteger(0);

        SpotifyApiClient client = new SpotifyApiClient(url -> {
            int call = networkCallIndex.incrementAndGet();
            if (call < 3) {
                // First 2 calls return 500 immediately
                return new MockHttpConn(new URL(url), 500, "{\"error\": \"transient_server_error\"}");
            } else {
                // 3rd call signals it is in flight and waits for poller.stop() to be called
                thirdErrorInFlightLatch.countDown();
                try {
                    assertTrue(stopCompletedLatch.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new MockHttpConn(new URL(url), 500, "{\"error\": \"third_server_error\"}");
            }
        });

        // Controlled scheduler so background schedule doesn't fire concurrently with manual test steps
        java.util.concurrent.ScheduledThreadPoolExecutor manualScheduler = new java.util.concurrent.ScheduledThreadPoolExecutor(1) {
            @Override
            public <V> java.util.concurrent.ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
                return null;
            }
            @Override
            public java.util.concurrent.ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                return null;
            }
        };

        SpotifyPoller poller = new SpotifyPoller(null, client, store, manualScheduler, false, tempCacheDir, () -> {});

        // Trigger first 2 errors manually
        poller.executePoll();
        assertEquals(1, poller.getConsecutiveErrors());
        poller.resetBackoff();

        poller.executePoll();
        assertEquals(2, poller.getConsecutiveErrors());
        poller.resetBackoff();

        // Verify Provider still shows active playing track after 2 errors (resilient to transient glitches)
        assertTrue("Provider should stay playing after 2 errors", SpotifyNowPlayingProvider.getPlaybackSnapshot().isPlaying);
        assertEquals("Preserved Track Title", SpotifyNowPlayingProvider.getPlaybackSnapshot().trackTitle);

        // Start poller for the 3rd poll
        poller.start();
        assertTrue(poller.isRunning());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Run 3rd poll in background thread
            Future<Long> pollFuture = executor.submit(poller::executePoll);

            // Wait until 3rd HTTP call is in flight
            assertTrue(thirdErrorInFlightLatch.await(5, TimeUnit.SECONDS));

            // Stop poller while the 3rd error response is in transit
            poller.stop();
            assertFalse(poller.isRunning());

            // Allow the 3rd HTTP error to return to executePoll
            stopCompletedLatch.countDown();
            long result = pollFuture.get(5, TimeUnit.SECONDS);
            assertEquals("Discarded poll due to stop() must return 0", 0L, result);

            // CRITICAL ASSERTION: The 3rd error must NOT have expired or modified the Provider state!
            SpotifyNowPlayingProvider.PlaybackSnapshot snapshot = SpotifyNowPlayingProvider.getPlaybackSnapshot();
            assertTrue("Provider playback state must NOT be expired or altered after stop()", snapshot.isPlaying);
            assertEquals("Preserved Track Title", snapshot.trackTitle);
            assertEquals("Preserved Artist", snapshot.artistName);
            assertEquals("Preserved Album", snapshot.albumName);
            assertEquals("spotify:track:preserved", snapshot.spotifyUri);
            assertTrue(snapshot.hasAlbumArt);
        } finally {
            poller.destroy();
            executor.shutdownNow();
        }
    }

    /**
     * Requirement 4: Concurrent transitions (Subscribe, Unsubscribe, and Binder Death)
     * forced to race and attempt transition inversion with CountDownLatch.
     * Proves that transitions are strictly serialized under SUBSCRIPTION_LOCK and reconciled with
     * the actual subscriber count, guaranteeing poller.isRunning() == (subscriberCount > 0).
     */
    @Test(timeout = 10000)
    public void testConcurrentTransitionsReconcileOrderAndPollerStateWithLatches() throws Exception {
        FakeSharedPreferences sharedStorage = new FakeSharedPreferences();
        SpotifyTokenStore store = new SpotifyTokenStore(sharedStorage);
        store.saveTokens("client", "access", "refresh", 3600);

        java.util.concurrent.ScheduledThreadPoolExecutor manualScheduler = new java.util.concurrent.ScheduledThreadPoolExecutor(1) {
            @Override
            public <V> java.util.concurrent.ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
                return null;
            }
            @Override
            public java.util.concurrent.ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                return null;
            }
        };

        SpotifyPoller poller = new SpotifyPoller(null, new SpotifyApiClient(), store, manualScheduler, false, tempCacheDir, () -> {});
        SpotifyNowPlayingProvider.setPollerForTesting(poller);

        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            // Case 1: Subscribe vs Unsubscribe racing on transition boundaries
            // Start state: binderA subscribed (subscriberCount = 1, poller running)
            MockTestBinder binderA = new MockTestBinder();
            SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, binderA, poller);
            assertEquals(1, SpotifyNowPlayingProvider.getSubscriberCount());
            assertTrue(poller.isRunning());

            MockTestBinder binderB = new MockTestBinder();
            CountDownLatch startGate1 = new CountDownLatch(1);

            // Thread 1 unbinds binderA (attempts transition 1 -> 0, which triggers stop)
            Future<?> f1 = executor.submit(() -> {
                try {
                    startGate1.await();
                } catch (InterruptedException ignored) {}
                SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_UNSUBSCRIBE, binderA, poller);
            });

            // Thread 2 binds binderB (attempts transition to keep active, requiring running)
            Future<?> f2 = executor.submit(() -> {
                try {
                    startGate1.await();
                } catch (InterruptedException ignored) {}
                SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, binderB, poller);
            });

            startGate1.countDown();
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            // Exactly 1 subscriber (binderB) remains active
            assertEquals("Subscriber count must be 1 after unbind(A) and bind(B)", 1, SpotifyNowPlayingProvider.getSubscriberCount());
            assertTrue("Poller MUST be running because binderB is active, regardless of thread interleaving", poller.isRunning());

            // Case 2: Binder Death vs Subscribe racing
            // Start state: binderB is subscribed (subscriberCount = 1, poller running)
            MockTestBinder binderC = new MockTestBinder();
            CountDownLatch startGate2 = new CountDownLatch(1);

            // Thread 3 simulates sudden binder death of binderB (attempts transition 1 -> 0, which triggers stop)
            Future<?> f3 = executor.submit(() -> {
                try {
                    startGate2.await();
                } catch (InterruptedException ignored) {}
                binderB.simulateBinderDeath();
            });

            // Thread 4 binds binderC concurrently
            Future<?> f4 = executor.submit(() -> {
                try {
                    startGate2.await();
                } catch (InterruptedException ignored) {}
                SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, binderC, poller);
            });

            startGate2.countDown();
            f3.get(5, TimeUnit.SECONDS);
            f4.get(5, TimeUnit.SECONDS);

            assertEquals("Subscriber count must be 1 after death(B) and bind(C)", 1, SpotifyNowPlayingProvider.getSubscriberCount());
            assertTrue("Poller MUST be running because binderC is active despite concurrent death of binderB", poller.isRunning());

            // Case 3: Binder Death when it is the last subscriber
            binderC.simulateBinderDeath();
            assertEquals("Subscriber count must be 0 after death(C)", 0, SpotifyNowPlayingProvider.getSubscriberCount());
            assertFalse("Poller MUST be stopped when all subscribers have died", poller.isRunning());

            // Case 4: High-frequency concurrent race wave testing all 3 transitions
            for (int wave = 0; wave < 50; wave++) {
                MockTestBinder b1 = new MockTestBinder();
                MockTestBinder b2 = new MockTestBinder();
                MockTestBinder b3 = new MockTestBinder();

                CountDownLatch waveGate = new CountDownLatch(1);
                Future<?> w1 = executor.submit(() -> {
                    try { waveGate.await(); } catch (InterruptedException ignored) {}
                    SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, b1, poller);
                    SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_UNSUBSCRIBE, b1, poller);
                });

                Future<?> w2 = executor.submit(() -> {
                    try { waveGate.await(); } catch (InterruptedException ignored) {}
                    SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, b2, poller);
                    b2.simulateBinderDeath();
                });

                Future<?> w3 = executor.submit(() -> {
                    try { waveGate.await(); } catch (InterruptedException ignored) {}
                    SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_SUBSCRIBE, b3, poller);
                });

                waveGate.countDown();
                w1.get(5, TimeUnit.SECONDS);
                w2.get(5, TimeUnit.SECONDS);
                w3.get(5, TimeUnit.SECONDS);

                // In each wave: b1 subscribed then unsubscribed (net 0), b2 subscribed then died (net 0), b3 subscribed (net +1)
                int subs = SpotifyNowPlayingProvider.getSubscriberCount();
                assertEquals("Expected subscriber count to match active binders in wave " + wave, subs > 0, poller.isRunning());

                // Cleanup wave
                SpotifyNowPlayingProvider.handleSubscription(SpotifyContract.METHOD_UNSUBSCRIBE, b3, poller);
                assertEquals(0, SpotifyNowPlayingProvider.getSubscriberCount());
                assertFalse(poller.isRunning());
            }
        } finally {
            SpotifyNowPlayingProvider.resetSubscribersForTesting();
            poller.destroy();
            executor.shutdownNow();
        }
    }
}
