package com.web.labportalbackend.face.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class FaceEmbeddingCipherTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsAsBase64AesGcmAndAuthenticatesCiphertext() {
        FaceEmbeddingCipher cipher = new FaceEmbeddingCipher(KEY);
        String plaintext = "[0.1,0.2]";

        String encrypted = cipher.encrypt(plaintext);

        assertNotEquals(plaintext, encrypted);
        Base64.getDecoder().decode(encrypted);
        assertNotEquals(encrypted, cipher.encrypt(plaintext));

        byte[] tampered = Base64.getDecoder().decode(encrypted);
        tampered[tampered.length - 1] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered)));
    }
}
