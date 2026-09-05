package com.ecomm.user.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for hashing opaque tokens (refresh tokens) before DB persistence.
 *
 * <p>SHA-256 is intentionally chosen over BCrypt here because:
 * <ul>
 *   <li>Refresh tokens are already high-entropy random strings (48 bytes)
 *       so a slow adaptive hash is unnecessary.</li>
 *   <li>We need a fast, deterministic lookup by hash on every token-refresh call.</li>
 * </ul>
 */
public final class HashUtil {

    private HashUtil() {}

    /**
     * Returns the SHA-256 hex digest of the given input string.
     *
     * @param input plain-text token
     * @return lowercase hex string (64 chars)
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JVM spec — this cannot happen
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
