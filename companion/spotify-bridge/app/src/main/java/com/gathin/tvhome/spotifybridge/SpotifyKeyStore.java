package com.gathin.tvhome.spotifybridge;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hardware-backed or AndroidKeyStore encryption helper for Spotify OAuth tokens.
 * Uses AES-256 in GCM mode (AES/GCM/NoPadding) with random 12-byte IV per encryption.
 * The encryption key never leaves the Android KeyStore.
 */
public final class SpotifyKeyStore {

    private static final String ALIAS = "SpotifyTokenKey";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    private static SecretKey testKeyFallback;

    private SpotifyKeyStore() {}

    private static boolean isAndroidKeyStoreAvailable() {
        for (Provider p : Security.getProviders()) {
            if (ANDROID_KEY_STORE.equals(p.getName())) {
                return true;
            }
        }
        return false;
    }

    private static synchronized SecretKey getOrCreateKey() throws Exception {
        if (!isAndroidKeyStoreAvailable()) {
            // Fallback for host JVM unit test execution where AndroidKeyStore is absent
            if (testKeyFallback == null) {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                testKeyFallback = keyGen.generateKey();
            }
            return testKeyFallback;
        }

        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(ALIAS)) {
            KeyGenerator keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGen.init(spec);
            return keyGen.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(ALIAS, null)).getSecretKey();
    }

    /**
     * Encrypts plaintext using AES/GCM/NoPadding.
     * Returns "Base64(IV):Base64(Ciphertext)".
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return "";
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String encodedIv = Base64.getUrlEncoder().withoutPadding().encodeToString(iv);
            String encodedCipher = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
            return encodedIv + ":" + encodedCipher;
        } catch (Exception e) {
            throw new RuntimeException("Keystore encryption failed", e);
        }
    }

    /**
     * Decrypts "Base64(IV):Base64(Ciphertext)" using AES/GCM/NoPadding.
     */
    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return null;
        int sep = encrypted.indexOf(':');
        if (sep <= 0) return null;

        try {
            String ivPart = encrypted.substring(0, sep);
            String cipherPart = encrypted.substring(sep + 1);

            byte[] iv = Base64.getUrlDecoder().decode(ivPart);
            byte[] ciphertext = Base64.getUrlDecoder().decode(cipherPart);

            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // Corrupted ciphertext or tampered IV
        }
    }
}
