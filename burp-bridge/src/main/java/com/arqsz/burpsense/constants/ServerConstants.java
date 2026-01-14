package com.arqsz.burpsense.constants;

/**
 * Constants for HTTP server configuration
 */
public final class ServerConstants {

    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    public static final int DEFAULT_PORT = 1337;
    public static final int DEFAULT_MAX_HEADER_SIZE = 8192;
    public static final int DEFAULT_MAX_PARAMETERS = 1000;
    public static final int DEFAULT_MAX_HEADERS = 200;

    public static final int CONNECTION_TIMEOUT_MS = 3000;
    public static final long POLLING_INTERVAL_MS = 60000;

    public static final String DEFAULT_ALLOWED_ORIGIN_LOCALHOST = "http://localhost";
    public static final String DEFAULT_ALLOWED_ORIGIN_127 = "http://127.0.0.1";

    public static final String HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    public static final String HEADER_ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String HEADER_ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String HEADER_ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";

    public static final String HTTP_METHODS_ALLOWED = "GET, POST, OPTIONS";
    public static final String HTTP_HEADERS_ALLOWED = "Authorization, Content-Type";
    public static final String CORS_MAX_AGE = "3600";

    public static final String HTTP_METHOD_OPTIONS = "OPTIONS";

    public static final String CONTENT_TYPE_JSON = "application/json";

    public static final String ENDPOINT_HEALTH = "/health";
    public static final String ENDPOINT_ISSUES = "/issues";
    public static final String ENDPOINT_ISSUE_BY_ID = "/issues/{id}";
    public static final String ENDPOINT_WS = "/ws";

    public static final String JSON_KEY_KNOWN_IDS = "knownIds";

    public static final String HEALTH_RESPONSE_OK = "{\"status\":\"ok\"}";

    private ServerConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}