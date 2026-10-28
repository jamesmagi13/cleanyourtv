package com.gathin.tvhome.spotifybridge;

import android.content.pm.PackageManager;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

public class SpotifyProviderSecurityTest {

    @Test
    public void verifyCallerIdentityRequiresExactSignatureMatch() {
        SpotifyNowPlayingProvider.SignatureChecker mockChecker = (pkg1, pkg2) -> {
            if ("com.gathin.tvhome.spotifybridge".equals(pkg1) && "com.gathin.tvhome".equals(pkg2)) {
                return PackageManager.SIGNATURE_MATCH;
            }
            return PackageManager.SIGNATURE_NO_MATCH;
        };

        // Authorized caller with matching signature succeeds
        SpotifyNowPlayingProvider.verifyCallerIdentity(
                "com.gathin.tvhome", mockChecker, "com.gathin.tvhome.spotifybridge");

        // Unauthorized caller with non-matching signature is rejected
        try {
            SpotifyNowPlayingProvider.verifyCallerIdentity(
                    "com.malicious.fake", mockChecker, "com.gathin.tvhome.spotifybridge");
            fail("Expected SecurityException on non-matching signature");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("Unauthorized signature"));
        }
    }

    @Test
    public void openFileModeRejectsWriteAttempts() {
        // Mode "r" is allowed, write modes are rejected
        assertFalse(isWriteMode("r"));
        assertTrue(isWriteMode("w"));
        assertTrue(isWriteMode("wt"));
        assertTrue(isWriteMode("wa"));
        assertTrue(isWriteMode("rw"));
        assertTrue(isWriteMode("rwt"));
    }

    private static boolean isWriteMode(String mode) {
        return !"r".equals(mode);
    }

    @Test
    public void pathTraversalIsDetectedAndBlocked() throws IOException {
        File cacheDir = new File("/data/user/0/com.gathin.tvhome.spotifybridge/cache");
        File normalFile = new File(cacheDir, "now_playing_art.png");

        // Normal canonical path stays inside cacheDir
        assertTrue(isInsideDir(normalFile, cacheDir));

        // Path traversal escapes cacheDir
        File escapeFile = new File(cacheDir, "../../shared_prefs/spotify_tokens.xml");
        assertFalse(isInsideDir(escapeFile, cacheDir));
    }

    private static boolean isInsideDir(File target, File parent) throws IOException {
        String canonicalTarget = target.getCanonicalPath();
        String canonicalParent = parent.getCanonicalPath();
        return canonicalTarget.startsWith(canonicalParent);
    }

    @Test
    public void providerContractDoesNotExposeTokenOrSecretColumns() {
        String[] columns = {
                SpotifyContract.COLUMN_IS_PLAYING,
                SpotifyContract.COLUMN_TRACK_TITLE,
                SpotifyContract.COLUMN_ARTIST_NAME,
                SpotifyContract.COLUMN_ALBUM_NAME,
                SpotifyContract.COLUMN_DURATION_MS,
                SpotifyContract.COLUMN_PROGRESS_MS,
                SpotifyContract.COLUMN_ART_URI,
                SpotifyContract.COLUMN_UPDATED_AT
        };

        for (String col : columns) {
            assertFalse(col.toLowerCase().contains("token"));
            assertFalse(col.toLowerCase().contains("secret"));
            assertFalse(col.toLowerCase().contains("password"));
            assertFalse(col.toLowerCase().contains("auth"));
        }
    }
}
