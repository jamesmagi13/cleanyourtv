package com.gathin.tvhome;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spotify Now Playing presentation exclusively for the clock screensaver (dream_clock).
 * Displays a large album cover, track title and artist floating over the background artwork
 * without any solid black panel. Communicates with the auxiliary companion component
 * via secure IPC (ContentProvider and openFileDescriptor) without requiring internet permission.
 */
final class ClockSpotify {

    static final String AUTHORITY = "com.gathin.tvhome.spotify";
    static final String NOW_PLAYING_URI_STRING = "content://" + AUTHORITY + "/now_playing";

    static final class TrackInfo {
        final boolean isPlaying;
        final String title;
        final String artist;
        final String album;
        final String artUri;
        final long updatedAt;

        TrackInfo(boolean isPlaying, String title, String artist, String album, String artUri, long updatedAt) {
            this.isPlaying = isPlaying;
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "";
            this.album = album != null ? album : "";
            this.artUri = artUri != null ? artUri : "";
            this.updatedAt = updatedAt;
        }
    }

    private static TrackInfo mockTrack;
    private static Bitmap mockArt;

    static void setMockData(TrackInfo track, Bitmap art) {
        mockTrack = track;
        mockArt = art;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "clock-spotify"));

    private final ViewGroup root;
    private final ImageView artView;
    private final TextView trackView;
    private final TextView artistView;

    private boolean enabled;
    private boolean active;
    private int generation;
    private Bitmap currentArt;
    private TrackInfo currentInfo;

    private ContentObserver observer;

    ClockSpotify(View clockRoot) {
        root = clockRoot.findViewById(R.id.dreamSpotify);
        artView = clockRoot.findViewById(R.id.dreamSpotifyArt);
        trackView = clockRoot.findViewById(R.id.dreamSpotifyTrack);
        artistView = clockRoot.findViewById(R.id.dreamSpotifyArtist);

        if (artView != null) {
            final float density = artView.getResources().getDisplayMetrics().density;
            artView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * density);
                }
            });
            artView.setClipToOutline(true);
        }
    }

    void configure(Config cfg) {
        enabled = cfg.clockSpotify;
    }

    boolean isEnabled() {
        return enabled;
    }

    void start() {
        if (!enabled || root == null) return;
        active = true;
        generation++;

        final Context ctx = root.getContext().getApplicationContext();
        if (observer == null) {
            observer = new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    queryAsync();
                }
            };
            try {
                ctx.getContentResolver().registerContentObserver(Uri.parse(NOW_PLAYING_URI_STRING), true, observer);
            } catch (Exception ignored) {
            }
        }
        sendSubscription(ctx, "subscribe");
        queryAsync();
    }

    void stop() {
        active = false;
        generation++;
        if (root != null) {
            final Context ctx = root.getContext().getApplicationContext();
            if (observer != null) {
                try {
                    ctx.getContentResolver().unregisterContentObserver(observer);
                } catch (Exception ignored) {
                }
                observer = null;
            }
            sendSubscription(ctx, "unsubscribe");
            root.animate().cancel();
        }
    }

    void close() {
        stop();
        executor.shutdownNow();
        if (artView != null) {
            artView.setImageDrawable(null);
        }
        currentArt = null;
        currentInfo = null;
    }

    void queryAsync() {
        if (!active || root == null) return;
        final int gen = generation;
        final Context ctx = root.getContext().getApplicationContext();

        executor.execute(() -> {
            TrackInfo info = mockTrack != null ? mockTrack : fetchTrack(ctx);
            Bitmap art = null;
            if (info != null && info.isPlaying) {
                art = mockArt != null ? mockArt : loadArt(ctx, info.artUri);
            }
            final TrackInfo finalInfo = info;
            final Bitmap finalArt = art;
            handler.post(() -> {
                if (!active || gen != generation) {
                    return;
                }
                applyTrack(finalInfo, finalArt);
            });
        });
    }

    private void applyTrack(TrackInfo info, Bitmap art) {
        currentInfo = info;
        if (info != null && info.isPlaying && trackView != null && artistView != null) {
            trackView.setText(info.title);
            artistView.setText(info.artist);
            if (artView != null) {
                currentArt = art;
                artView.setImageBitmap(art);
            }
            if (root.getVisibility() != View.VISIBLE) {
                root.setAlpha(0f);
                root.setVisibility(View.VISIBLE);
                root.animate().alpha(1f).setDuration(600).start();
            }
        } else {
            if (root.getVisibility() == View.VISIBLE) {
                root.animate().alpha(0f).setDuration(600).withEndAction(() -> {
                    if (!active || currentInfo == null || !currentInfo.isPlaying) {
                        root.setVisibility(View.GONE);
                        if (artView != null) artView.setImageDrawable(null);
                    }
                }).start();
            } else {
                root.setVisibility(View.GONE);
            }
        }
    }

    static TrackInfo parseCursor(Cursor c) {
        if (c == null || !c.moveToFirst()) return null;
        try {
            int playingIdx = c.getColumnIndex("is_playing");
            int titleIdx = c.getColumnIndex("track_title");
            int artistIdx = c.getColumnIndex("artist_name");
            int albumIdx = c.getColumnIndex("album_name");
            int artIdx = c.getColumnIndex("art_uri");
            int updatedIdx = c.getColumnIndex("updated_at");

            boolean playing = playingIdx >= 0 && c.getInt(playingIdx) == 1;
            String title = titleIdx >= 0 ? c.getString(titleIdx) : "";
            String artist = artistIdx >= 0 ? c.getString(artistIdx) : "";
            String album = albumIdx >= 0 ? c.getString(albumIdx) : "";
            String art = artIdx >= 0 ? c.getString(artIdx) : "";
            long updated = updatedIdx >= 0 ? c.getLong(updatedIdx) : 0L;

            return new TrackInfo(playing, title, artist, album, art, updated);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TrackInfo fetchTrack(Context ctx) {
        try (Cursor c = ctx.getContentResolver().query(Uri.parse(NOW_PLAYING_URI_STRING), null, null, null, null)) {
            return parseCursor(c);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap loadArt(Context ctx, String uriString) {
        if (uriString == null || uriString.isEmpty()) return null;
        try (ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(Uri.parse(uriString), "r")) {
            if (pfd == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options);
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
    }

    private static void sendSubscription(Context ctx, String method) {
        try {
            ctx.getContentResolver().call(Uri.parse(NOW_PLAYING_URI_STRING), method, null, null);
        } catch (Exception ignored) {
        }
    }
}
