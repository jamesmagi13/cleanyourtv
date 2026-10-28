package com.gathin.tvhome;

import android.content.Context;
import android.widget.FrameLayout;

/** One decoder, image, caption and timer in this process. No Activity is held after its lease closes. */
final class WallpaperGallery implements WallpaperPlayback {
    private static Session shared;
    private final Session session;
    private final FrameLayout root;
    private final boolean clock;
    private boolean closed;

    private static final class Session {
        final String key;
        final WallpaperPlayer player;
        final GalleryOwners<WallpaperGallery> owners = new GalleryOwners<>();
        WallpaperGallery attached;
        int references;
        boolean retired;

        Session(Context context, Config config, String key) {
            this.key = key;
            // The staging container uses only application context, never a paused Activity.
            player = new WallpaperPlayer(context.getApplicationContext(),
                    new FrameLayout(context.getApplicationContext()), config, true);
        }

        void reconcile() {
            if (retired) return;
            WallpaperGallery next = owners.owner();
            if (next == attached) return;
            attached = next;
            if (next == null) {
                player.stop();
                player.detach();
            } else {
                // Keep a decode in flight: it belongs to this player, not to its old host.
                player.attach(next.root, next.clock);
                player.start();
            }
        }
    }

    static WallpaperPlayback open(Context context, FrameLayout root, Config config, boolean clock) {
        String key = WallpaperPlayer.galleryKey(config);
        if (shared == null || !shared.key.equals(key)) {
            if (shared != null) {
                shared.retired = true;
                shared.player.close();
                shared.attached = null;
            }
            shared = new Session(context, config, key);
        }
        return new WallpaperGallery(shared, root, clock);
    }

    private WallpaperGallery(Session session, FrameLayout root, boolean clock) {
        this.session = session;
        this.root = root;
        this.clock = clock;
        session.references++;
    }

    @Override public void start() {
        if (closed || session.retired) return;
        session.owners.start(this, clock);
        session.reconcile();
    }

    @Override public void stop() {
        session.owners.stop(this);
        session.reconcile();
    }

    // The shared player schedules its own deadline. Home's minute tick must not advance it too.
    @Override public void update() { }

    @Override public void releaseForMemoryPressure() {
        if (session.owners.owner() == null) session.player.releaseForMemoryPressure();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        stop();
        if (--session.references == 0) {
            session.player.close();
            if (shared == session) shared = null;
        }
    }
}
