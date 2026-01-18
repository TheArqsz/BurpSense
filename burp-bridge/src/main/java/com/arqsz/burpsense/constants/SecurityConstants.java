package com.arqsz.burpsense.constants;

/**
 * Constants for security and encryption
 */
public final class SecurityConstants {

    public static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    public static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final String KEY_ALGORITHM = "AES";

    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int GCM_IV_LENGTH_BYTES = 12;
    public static final int KEY_LENGTH_BITS = 256;
    public static final int PBKDF2_ITERATION_COUNT = 600_000;

    public static final int API_TOKEN_BYTES = 32;

    public static final int SALT_LENGTH_BYTES = 16;

    public static final String AUTH_HEADER_PREFIX = "Bearer ";
    public static final int AUTH_HEADER_PREFIX_LENGTH = 7;

    public static final int MAX_REQUESTS_PER_MINUTE = 10;
    public static final long WINDOW_SECONDS = 60;

    private SecurityConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}