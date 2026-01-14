package com.arqsz.burpsense.config;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import com.arqsz.burpsense.constants.PreferenceConstants;
import com.arqsz.burpsense.constants.SecurityConstants;

/**
 * Represents an API key for authentication
 */
public record ApiKey(String name, String token, String createdDate, String lastUsed) {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern(PreferenceConstants.DATETIME_FORMAT);

    /**
     * Creates a new API key with the given name
     * 
     * @param name The name/description for this API key
     * @return A new ApiKey instance
     */
    public static ApiKey create(String name) {
        String token = generateToken();
        String date = LocalDateTime.now().format(DATE_FORMATTER);
        return new ApiKey(name, token, date, null);
    }

    /**
     * Generates a random token
     * 
     * @return A URL-safe base64 encoded token
     */
    private static String generateToken() {
        byte[] randomBytes = new byte[SecurityConstants.API_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Creates a copy of this API key with an updated last used timestamp
     * 
     * @param lastUsed The new last used timestamp
     * @return A new ApiKey instance with updated timestamp
     */
    public ApiKey withLastUsed(String lastUsed) {
        return new ApiKey(name, token, createdDate, lastUsed);
    }

    /**
     * Updates the last used timestamp to now
     * 
     * @return ApiKey instance with current timestamp
     */
    public ApiKey withLastUsedNow() {
        String now = LocalDateTime.now().format(DATE_FORMATTER);
        return new ApiKey(name, token, createdDate, now);
    }
}