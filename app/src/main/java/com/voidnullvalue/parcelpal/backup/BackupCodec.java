package com.voidnullvalue.parcelpal.backup;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackupCodec {
    private static final String HEADER = "PARCELPAL1";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BackupCodec() {}

    public static String encode(String json, char[] password) throws GeneralSecurityException {
        if (password == null || password.length == 0) return json;
        byte[] salt = randomBytes(16);
        byte[] iv = randomBytes(12);
        SecretKeySpec key = derive(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(HEADER.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
        Base64.Encoder encoder = Base64.getEncoder();
        return HEADER + "\n" + encoder.encodeToString(salt) + "\n" + encoder.encodeToString(iv) + "\n" + encoder.encodeToString(encrypted);
    }

    public static String decode(String payload, char[] password) throws GeneralSecurityException {
        if (!isEncrypted(payload)) return payload;
        if (password == null || password.length == 0) throw new GeneralSecurityException("Backup password is required");
        String[] lines = payload.trim().split("\\R");
        if (lines.length != 4 || !HEADER.equals(lines[0])) throw new GeneralSecurityException("Invalid ParcelPal backup header");
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] salt;
        byte[] iv;
        byte[] encrypted;
        try {
            salt = decoder.decode(lines[1]);
            iv = decoder.decode(lines[2]);
            encrypted = decoder.decode(lines[3]);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Backup data is malformed", e);
        }
        SecretKeySpec key = derive(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(HEADER.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    public static boolean isEncrypted(String payload) {
        return payload != null && payload.startsWith(HEADER + "\n");
    }

    private static SecretKeySpec derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
