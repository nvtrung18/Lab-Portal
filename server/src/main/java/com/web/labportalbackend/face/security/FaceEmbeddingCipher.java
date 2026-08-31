package com.web.labportalbackend.face.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FaceEmbeddingCipher {

    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final byte[] ASSOCIATED_DATA = "lab-portal:face-embedding:v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKey key;

    public FaceEmbeddingCipher(@Value("${face.embedding-encryption-key}") String base64Key) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Face embedding encryption key must be valid base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("Face embedding encryption key must contain exactly 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Face embedding plaintext is required");
        }
        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(ASSOCIATED_DATA);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Face embedding encryption failed", exception);
        }
    }

    public String decrypt(String encryptedBase64) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedBase64);
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("Encrypted face embedding payload is invalid");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(ASSOCIATED_DATA);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Encrypted face embedding could not be authenticated", exception);
        }
    }
}
