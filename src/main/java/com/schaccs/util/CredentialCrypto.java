package com.schaccs.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public final class CredentialCrypto {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialCrypto.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int ITERATIONS = 100_000;
    private static final String SECRET_SALT = "Schaccs!DB2024#Secret";
    private static final String MASTER_KEY_ENV = "SCHACCS_MASTER_KEY";
    private static final String DEFAULT_MASTER_KEY = "schaccs-default-master-key";

    private CredentialCrypto() {}

    private static SecretKey deriveKey() throws Exception {
        String material = resolveMasterKey();
        PBEKeySpec spec = new PBEKeySpec(
                material.toCharArray(),
                SECRET_SALT.getBytes("UTF-8"),
                ITERATIONS,
                256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static String resolveMasterKey() {
        String configured = System.getenv(MASTER_KEY_ENV);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String property = System.getProperty(MASTER_KEY_ENV);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return DEFAULT_MASTER_KEY;
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return "";
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            LOG.warn("Credential encryption failed", e);
            throw new IllegalStateException("Unable to encrypt credential", e);
        }
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            SecretKey key = deriveKey();
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length < IV_LENGTH) {
                throw new IllegalArgumentException("Ciphertext is too short");
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, IV_LENGTH);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, "UTF-8");
        } catch (IllegalArgumentException e) {
            LOG.warn("Credential decryption failed due to invalid ciphertext", e);
            throw e;
        } catch (Exception e) {
            LOG.warn("Credential decryption failed", e);
            throw new IllegalStateException("Unable to decrypt credential", e);
        }
    }
}
