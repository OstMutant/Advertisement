package org.ost.apikey.security;

import lombok.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates high-entropy raw API keys and computes their SHA-256 hash for storage.
 */
@Component
public class ApiKeyHasher {

    private static final int RAW_KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[RAW_KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(@NonNull String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
