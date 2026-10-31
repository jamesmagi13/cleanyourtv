package com.gathin.tvhome.spotifybridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hardened ContentProvider exposing sanitized Spotify Now Playing metadata and cached album art.
 * - Enforces signature permission and caller identity verification.
 * - Mode openFile strictly read-only ("r") with canonical path verification.
 * - Exposes zero tokens, zero credentials, zero account identifiers.
 * - Mock mode is strictly off by default; inactive state returns is_playing = 0.
 */
public class SpotifyNowPlayingProvider extends ContentProvider {

    private static final int MATCH_NOW_PLAYING = 1;
    private static final int MATCH_ART = 2;

    private static UriMatcher uriMatcher;

    private static synchronized UriMatcher getUriMatcher() {
        if (uriMatcher == null) {
            uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            uriMatcher.addURI(SpotifyContract.AUTHORITY, SpotifyContract.TABLE_NOW_PLAYING, MATCH_NOW_PLAYING);
            uriMatcher.addURI(SpotifyContract.AUTHORITY, SpotifyContract.PATH_ART, MATCH_ART);
        }
        return uriMatcher;
    }

    private static final String[] COLUMNS = {
            SpotifyContract.COLUMN_IS_PLAYING,
            SpotifyContract.COLUMN_TRACK_TITLE,
            SpotifyContract.COLUMN_ARTIST_NAME,
            SpotifyContract.COLUMN_ALBUM_NAME,
            SpotifyContract.COLUMN_DURATION_MS,
            SpotifyContract.COLUMN_PROGRESS_MS,
            SpotifyContract.COLUMN_ART_URI,
            SpotifyContract.COLUMN_UPDATED_AT,
            SpotifyContract.COLUMN_SPOTIFY_URI
    };

    // State in memory: mockMode is FALSE by default to prevent fictitious tracks in production
    private static boolean mockMode = false;
    private static boolean isPlaying = false;
    private static String trackTitle = "";
    private static String artistName = "";
    private static String albumName = "";
    private static String spotifyUri = "";
    private static long durationMs = 0L;
    private static long progressMs = 0L;
    private static long updatedAt = 0L;
    private static boolean hasAlbumArt = false;

    private static final Object SUBSCRIPTION_LOCK = new Object();
    private static volatile int subscriberCount = 0;
    private static final Map<IBinder, IBinder.DeathRecipient> activeSubscribers = new HashMap<>();
    private static SpotifyPoller pollerInstance = null;

    public static synchronized void setPollerForTesting(SpotifyPoller poller) {
        pollerInstance = poller;
    }

    public static void resetSubscribersForTesting() {
        synchronized (SUBSCRIPTION_LOCK) {
            for (Map.Entry<IBinder, IBinder.DeathRecipient> entry : activeSubscribers.entrySet()) {
                try {
                    entry.getKey().unlinkToDeath(entry.getValue(), 0);
                } catch (Exception ignored) {}
            }
            activeSubscribers.clear();
            subscriberCount = 0;
            if (pollerInstance != null) {
                reconcilePollerState(pollerInstance);
            }
        }
    }

    /**
     * Explicit test helper: only enabled during local simulation tests.
     */
    public static synchronized void enableMockForTesting(boolean enable, boolean playing, String title, String artist, String album) {
        mockMode = enable;
        isPlaying = playing;
        trackTitle = title != null ? title : "";
        artistName = artist != null ? artist : "";
        albumName = album != null ? album : "";
        spotifyUri = "spotify:track:mock123";
        durationMs = 256000L;
        progressMs = 45000L;
        updatedAt = System.currentTimeMillis();
        hasAlbumArt = true;
    }

    public static synchronized boolean setPlaybackState(boolean playing, String title, String artist, String album,
                                                        String uri, long duration, long progress, boolean hasArt) {
        String newTitle = title != null ? title : "";
        String newArtist = artist != null ? artist : "";
        String newAlbum = album != null ? album : "";
        String newUri = uri != null ? uri : "";

        boolean changed = isPlaying != playing
                || !trackTitle.equals(newTitle)
                || !artistName.equals(newArtist)
                || !albumName.equals(newAlbum)
                || !spotifyUri.equals(newUri)
                || hasAlbumArt != hasArt;

        mockMode = false;
        isPlaying = playing;
        trackTitle = newTitle;
        artistName = newArtist;
        albumName = newAlbum;
        spotifyUri = newUri;
        durationMs = duration;
        progressMs = progress;
        hasAlbumArt = hasArt;
        updatedAt = System.currentTimeMillis();
        return changed;
    }

    public static synchronized void setLiveState(boolean playing, String title, String artist, String album, long duration, long progress) {
        setPlaybackState(playing, title, artist, album, "", duration, progress, playing);
    }

    public static void clearPlaybackState(Context context) {
        synchronized (SpotifyNowPlayingProvider.class) {
            mockMode = false;
            isPlaying = false;
            trackTitle = "";
            artistName = "";
            albumName = "";
            spotifyUri = "";
            durationMs = 0L;
            progressMs = 0L;
            hasAlbumArt = false;
            updatedAt = System.currentTimeMillis();
        }
        if (context != null) {
            File artFile = new File(context.getCacheDir(), "now_playing_art.png");
            if (artFile.exists()) {
                artFile.delete();
            }
            try {
                context.getContentResolver().notifyChange(SpotifyContract.NOW_PLAYING_URI, null);
            } catch (Exception ignored) {
            }
        }
    }

    public static final class PlaybackSnapshot {
        public final boolean isPlaying;
        public final String trackTitle;
        public final String artistName;
        public final String albumName;
        public final String spotifyUri;
        public final long durationMs;
        public final long progressMs;
        public final boolean hasAlbumArt;

        public PlaybackSnapshot(boolean isPlaying, String trackTitle, String artistName, String albumName,
                                String spotifyUri, long durationMs, long progressMs, boolean hasAlbumArt) {
            this.isPlaying = isPlaying;
            this.trackTitle = trackTitle;
            this.artistName = artistName;
            this.albumName = albumName;
            this.spotifyUri = spotifyUri;
            this.durationMs = durationMs;
            this.progressMs = progressMs;
            this.hasAlbumArt = hasAlbumArt;
        }
    }

    public static synchronized PlaybackSnapshot getPlaybackSnapshot() {
        return new PlaybackSnapshot(isPlaying, trackTitle, artistName, albumName, spotifyUri, durationMs, progressMs, hasAlbumArt);
    }

    public static int getSubscriberCount() {
        return subscriberCount;
    }

    public static synchronized boolean isMockMode() {
        return mockMode;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    public interface SignatureChecker {
        int checkSignatures(String pkg1, String pkg2);
    }

    private void verifyCaller() {
        String callingPackage = getCallingPackage();
        if (callingPackage == null) {
            return; // Internal call within same process
        }
        Context ctx = getContext();
        if (ctx == null) return;

        PackageManager pm = ctx.getPackageManager();
        verifyCallerIdentity(callingPackage, pm != null ? pm::checkSignatures : null, ctx.getPackageName());
    }

    static void verifyCallerIdentity(String callingPackage, SignatureChecker checker, String myPackage) {
        if (callingPackage == null) return;
        if (checker == null) {
            throw new SecurityException("SignatureChecker unavailable");
        }
        int sigMatch = checker.checkSignatures(myPackage, callingPackage);
        if (sigMatch != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("Unauthorized signature for caller: " + callingPackage);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        verifyCaller();
        if (getUriMatcher().match(uri) != MATCH_NOW_PLAYING) {
            return null;
        }

        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        synchronized (SpotifyNowPlayingProvider.class) {
            String artUri = (isPlaying && hasAlbumArt) ? SpotifyContract.ART_URI.toString() : "";
            cursor.addRow(new Object[]{
                    isPlaying ? 1 : 0,
                    trackTitle,
                    artistName,
                    albumName,
                    durationMs,
                    progressMs,
                    artUri,
                    updatedAt,
                    spotifyUri
            });
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        verifyCaller();

        // Enforce strictly read-only mode; reject any write attempts
        if (!"r".equals(mode)) {
            throw new SecurityException("Write access denied");
        }

        if (getUriMatcher().match(uri) != MATCH_ART) {
            throw new FileNotFoundException("Unknown URI: " + uri);
        }

        Context ctx = getContext();
        if (ctx == null) throw new FileNotFoundException("Context unavailable");

        File cacheDir = ctx.getCacheDir();
        File artFile = new File(cacheDir, "now_playing_art.png");

        // Prevent directory traversal
        try {
            if (!artFile.getCanonicalPath().startsWith(cacheDir.getCanonicalPath())) {
                throw new SecurityException("Illegal file path access");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Path resolution failed");
        }

        if (!artFile.exists() || artFile.length() == 0) {
            if (mockMode) {
                generateMockArt(artFile);
            } else {
                throw new FileNotFoundException("No artwork available");
            }
        }

        return ParcelFileDescriptor.open(artFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private void generateMockArt(File target) {
        try {
            int size = 400;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);

            Paint bgPaint = new Paint();
            bgPaint.setColor(0xFF2C3E50);
            canvas.drawRect(0, 0, size, size, bgPaint);

            Paint circlePaint = new Paint();
            circlePaint.setAntiAlias(true);
            circlePaint.setColor(0xFFE74C3C);
            canvas.drawCircle(size / 2f, size / 2f, size * 0.35f, circlePaint);

            Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(36);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("SPOTIFY", size / 2f, size / 2f + 12, textPaint);

            try (FileOutputStream out = new FileOutputStream(target)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            }
            bitmap.recycle();
        } catch (IOException ignored) {
        }
    }

    public static void reconcilePollerState(SpotifyPoller poller) {
        if (poller == null) return;
        synchronized (SUBSCRIPTION_LOCK) {
            int current = subscriberCount;
            if (current > 0) {
                if (!poller.isRunning()) {
                    poller.start();
                }
            } else {
                if (poller.isRunning()) {
                    poller.stop();
                }
            }
        }
    }

    public static int handleSubscription(String method, IBinder token, SpotifyPoller poller) {
        SpotifyPoller targetPoller = poller != null ? poller : pollerInstance;
        synchronized (SUBSCRIPTION_LOCK) {
            if (SpotifyContract.METHOD_SUBSCRIBE.equals(method)) {
                if (token != null) {
                    if (!activeSubscribers.containsKey(token)) {
                        IBinder.DeathRecipient recipient = () -> {
                            synchronized (SUBSCRIPTION_LOCK) {
                                activeSubscribers.remove(token);
                                subscriberCount = activeSubscribers.size();
                                reconcilePollerState(targetPoller);
                            }
                        };
                        try {
                            token.linkToDeath(recipient, 0);
                            activeSubscribers.put(token, recipient);
                        } catch (RemoteException e) {
                            return subscriberCount;
                        }
                    }
                    subscriberCount = activeSubscribers.size();
                } else {
                    subscriberCount++;
                }
            } else if (SpotifyContract.METHOD_UNSUBSCRIBE.equals(method)) {
                if (token != null) {
                    IBinder.DeathRecipient recipient = activeSubscribers.remove(token);
                    if (recipient != null) {
                        try {
                            token.unlinkToDeath(recipient, 0);
                        } catch (Exception ignored) {
                        }
                    }
                    subscriberCount = activeSubscribers.size();
                } else {
                    subscriberCount = Math.max(0, subscriberCount - 1);
                }
            }
            reconcilePollerState(targetPoller);
            return subscriberCount;
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        verifyCaller();
        IBinder token = null;
        if (extras != null) {
            try {
                token = extras.getBinder(SpotifyContract.EXTRA_TOKEN);
            } catch (Exception ignored) {
            }
        }
        SpotifyPoller poller = pollerInstance != null ? pollerInstance : SpotifyPoller.getInstance(getContext());
        int count = handleSubscription(method, token, poller);
        Bundle res = new Bundle();
        try {
            res.putInt("subscribers", count);
        } catch (Exception ignored) {
        }
        return res;
    }

    @Override
    public String getType(Uri uri) {
        switch (getUriMatcher().match(uri)) {
            case MATCH_NOW_PLAYING:
                return "vnd.android.cursor.item/vnd.com.gathin.tvhome.spotify.now_playing";
            case MATCH_ART:
                return "image/png";
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update not supported");
    }
}
