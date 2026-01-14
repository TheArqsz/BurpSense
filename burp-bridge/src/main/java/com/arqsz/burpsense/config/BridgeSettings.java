package com.arqsz.burpsense.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arqsz.burpsense.constants.PreferenceConstants;
import com.arqsz.burpsense.constants.ServerConstants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.Preferences;

/**
 * Manages bridge configuration and API key storage
 */
public class BridgeSettings {

    private final Preferences preferences;
    private final KeyStorage keyStorage;
    private final Gson gson = new Gson();
    private final MontoyaApi api;

    private Map<String, ApiKey> keyCache = null;
    private long lastCacheUpdate = 0;

    /**
     * Initializes bridge settings with key storage
     * 
     * @param preferences The Burp preferences for persistence
     * @param api         The Montoya API instance
     */
    public BridgeSettings(Preferences preferences, MontoyaApi api) {
        this.preferences = preferences;
        this.api = api;
        try {
            this.keyStorage = new KeyStorage(api);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize a key storage", e);
        }
        this.rebuildCache();
    }

    /**
     * Gets all stored API keys
     * 
     * @return List of API keys
     */
    public List<ApiKey> getApiKeys() {
        String encrypted = preferences.getString(PreferenceConstants.PREF_API_KEYS_ENCRYPTED);
        if (encrypted == null) {
            return new ArrayList<>();
        }

        try {
            return keyStorage.decryptKeys(encrypted);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Adds a new API key
     * 
     * @param key The API key to add
     */
    public void addKey(ApiKey key) {
        List<ApiKey> keys = getApiKeys();
        keys.add(key);
        saveKeys(keys);
    }

    /**
     * Removes an API key at the specified index
     * 
     * @param index The index of the key to remove
     */
    public void removeKey(int index) {
        List<ApiKey> keys = getApiKeys();
        if (index >= 0 && index < keys.size()) {
            keys.remove(index);
            saveKeys(keys);
        }
    }

    /**
     * Finds an API key by its token value
     * 
     * @param token The token to search for
     * @return The API key, or null if not found
     */
    public ApiKey findKeyByToken(String token) {
        if (isCacheStale()) {
            rebuildCache();
        }
        return keyCache.get(token);
    }

    /**
     * Updates the last used timestamp for an API key
     * 
     * @param token The token of the key to update
     */
    public void updateLastUsed(String token) {
        List<ApiKey> keys = getApiKeys();
        List<ApiKey> updated = new ArrayList<>();

        for (ApiKey key : keys) {
            if (key.token().equals(token)) {
                updated.add(key.withLastUsedNow());
            } else {
                updated.add(key);
            }
        }

        saveKeys(updated);
    }

    /**
     * Saves the list of API keys with encryption
     * 
     * @param keys The keys to save
     */
    private void saveKeys(List<ApiKey> keys) {
        try {
            String encrypted = keyStorage.encryptKeys(keys);
            preferences.setString(PreferenceConstants.PREF_API_KEYS_ENCRYPTED, encrypted);
            rebuildCache();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt keys", e);
        }
    }

    /**
     * Rebuilds the in-memory cache of API keys
     */
    private void rebuildCache() {
        keyCache = new HashMap<>();
        for (ApiKey key : getApiKeys()) {
            keyCache.put(key.token(), key);
        }
        lastCacheUpdate = System.currentTimeMillis();
    }

    /**
     * Checks if the cache needs to be refreshed
     * 
     * @return true if cache is stale
     */
    private boolean isCacheStale() {
        return keyCache == null ||
                (System.currentTimeMillis() - lastCacheUpdate) > PreferenceConstants.CACHE_TTL_MS;
    }

    /**
     * Gets the configured bind IP address
     * 
     * @return The IP address
     */
    public String getIp() {
        String ip = preferences.getString(PreferenceConstants.PREF_BIND_ADDRESS);
        return ip != null ? ip : ServerConstants.DEFAULT_BIND_ADDRESS;
    }

    /**
     * Sets the bind IP address
     * 
     * @param ip The IP address to bind to
     */
    public void setIp(String ip) {
        preferences.setString(PreferenceConstants.PREF_BIND_ADDRESS, ip);
    }

    /**
     * Gets the configured port number
     * 
     * @return The port number
     */
    public int getPort() {
        Integer port = preferences.getInteger(PreferenceConstants.PREF_PORT);
        return port != null ? port : ServerConstants.DEFAULT_PORT;
    }

    /**
     * Sets the port number
     * 
     * @param port The port to listen on
     */
    public void setPort(int port) {
        preferences.setInteger(PreferenceConstants.PREF_PORT, port);
    }

    /**
     * Gets the list of allowed CORS origins
     * 
     * @return List of allowed origin URLs
     */
    public List<String> getAllowedOrigins() {
        String origins = preferences.getString(PreferenceConstants.PREF_ALLOWED_ORIGINS);
        if (origins == null) {
            return List.of(
                    ServerConstants.DEFAULT_ALLOWED_ORIGIN_LOCALHOST,
                    ServerConstants.DEFAULT_ALLOWED_ORIGIN_127);
        }
        return Arrays.asList(origins.split(","));
    }

    /**
     * Sets the list of allowed CORS origins
     * 
     * @param origins List of allowed origin URLs
     */
    public void setAllowedOrigins(List<String> origins) {
        preferences.setString(PreferenceConstants.PREF_ALLOWED_ORIGINS, String.join(",", origins));
    }
}