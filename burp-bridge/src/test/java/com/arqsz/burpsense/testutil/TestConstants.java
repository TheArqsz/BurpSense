package com.arqsz.burpsense.testutil;

/**
 * Constants used across test classes
 */
public class TestConstants {
    public static final String TEST_HOST = "127.0.0.1";
    public static final int TEST_PORT = 8765;
    public static final String TEST_API_KEY = "test-api-key-12345";

    public static final int DEFAULT_TIMEOUT_MS = 5000;
    public static final int ASYNC_TIMEOUT_MS = 10000;

    public static final String TEST_ISSUE_NAME = "SQL Injection";
    public static final String TEST_BASE_URL = "https://test.example.com";
    public static final String TEST_HOST_NAME = "test.example.com";

    private TestConstants() {
    }
}
