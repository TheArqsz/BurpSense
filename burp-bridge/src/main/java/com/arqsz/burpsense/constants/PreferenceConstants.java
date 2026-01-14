package com.arqsz.burpsense.constants;

/**
 * Constants for preferences and storage
 */
public final class PreferenceConstants {

    public static final String PREF_API_KEYS_ENCRYPTED = "burpsense_bridge_keys_v2";
    public static final String PREF_API_KEYS_LEGACY = "burpsense_bridge_keys";
    public static final String PREF_BIND_ADDRESS = "burpsense_bridge_ip";
    public static final String PREF_PORT = "burpsense_bridge_port";
    public static final String PREF_ALLOWED_ORIGINS = "burpsense_allowed_origins";

    public static final long CACHE_TTL_MS = 60000;

    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm";

    private PreferenceConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}