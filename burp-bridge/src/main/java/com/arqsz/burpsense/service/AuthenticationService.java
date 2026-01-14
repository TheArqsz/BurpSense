package com.arqsz.burpsense.service;

import java.util.Optional;

import com.arqsz.burpsense.config.ApiKey;
import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.SecurityConstants;

/**
 * Service for handling API authentication
 */
public class AuthenticationService {

    private final BridgeSettings settings;

    public AuthenticationService(BridgeSettings settings) {
        this.settings = settings;
    }

    /**
     * Validates a Bearer token from an Authorization header
     * 
     * @param authHeader The Authorization header value
     * @return An Optional containing the API key if valid, empty otherwise
     */
    public Optional<ApiKey> validateBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(SecurityConstants.AUTH_HEADER_PREFIX)) {
            return Optional.empty();
        }

        String token = authHeader.substring(SecurityConstants.AUTH_HEADER_PREFIX_LENGTH);
        ApiKey key = settings.findKeyByToken(token);

        return Optional.ofNullable(key);
    }

    /**
     * Updates the last used timestamp for an API key
     * 
     * @param apiKey The API key that was just used
     */
    public void recordKeyUsage(ApiKey apiKey) {
        settings.updateLastUsed(apiKey.token());
    }
}