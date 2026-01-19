package com.arqsz.burpsense.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import com.arqsz.burpsense.config.ApiKey;
import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.testutil.MockMontoyaApiFactory;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.Preferences;

@DisplayName("AuthenticationService")
class AuthenticationServiceTest {

    private AuthenticationService authService;
    private BridgeSettings settings;
    private MontoyaApi api;
    private ApiKey validKey;

    @BeforeEach
    void setUp() {
        api = MockMontoyaApiFactory.createBasicMock();
        Preferences prefs = api.persistence().preferences();
        settings = new BridgeSettings(prefs, api);

        validKey = ApiKey.create("TestKey");
        settings.addKey(validKey);

        authService = new AuthenticationService(settings);
    }

    @Nested
    @DisplayName("validateBearerToken")
    @TestInstance(Lifecycle.PER_CLASS)
    class ValidateBearerToken {

        @Test
        @DisplayName("should validate correct Bearer token")
        void shouldValidateCorrectBearerToken() {
            String authHeader = "Bearer " + validKey.token();

            Optional<ApiKey> result = authService.validateBearerToken(authHeader);

            assertThat(result)
                    .isPresent()
                    .contains(validKey);
        }

        @Test
        @DisplayName("should reject invalid token")
        void shouldRejectInvalidToken() {
            String authHeader = "Bearer invalid-token-12345";

            Optional<ApiKey> result = authService.validateBearerToken(authHeader);

            assertThat(result).isEmpty();
        }

        @ParameterizedTest(name = "Invalid header: ''{0}''")
        @NullAndEmptySource
        @MethodSource("provideMalformedHeaders")
        @DisplayName("should reject malformed auth headers")
        void shouldRejectMalformedAuthHeaders(String authHeader) {
            Optional<ApiKey> result = authService.validateBearerToken(authHeader);

            assertThat(result).isEmpty();
        }

        private Stream<String> provideMalformedHeaders() {
            return Stream.of(
                    "Basic dXNlcjpwYXNz",
                    "Token abc123",
                    validKey.token(),
                    "Bearer",
                    "Bearer ",
                    "bearer token123");
        }

        @Test
        @DisplayName("should handle token with extra whitespace")
        void shouldHandleTokenWithExtraWhitespace() {
            String authHeader = "Bearer  " + validKey.token() + "  ";

            Optional<ApiKey> result = authService.validateBearerToken(authHeader);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should distinguish between multiple keys")
        void shouldDistinguishBetweenMultipleKeys() {
            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");
            settings.addKey(key1);
            settings.addKey(key2);

            Optional<ApiKey> result1 = authService.validateBearerToken("Bearer " + key1.token());
            Optional<ApiKey> result2 = authService.validateBearerToken("Bearer " + key2.token());

            assertThat(result1).isPresent().contains(key1);
            assertThat(result2).isPresent().contains(key2);
        }
    }

    @Nested
    @DisplayName("recordKeyUsage")
    class RecordKeyUsage {

        @Test
        @DisplayName("should update last used timestamp")
        void shouldUpdateLastUsedTimestamp() {
            ApiKey key = ApiKey.create("TestKey");
            settings.addKey(key);

            authService.recordKeyUsage(key);

            ApiKey updated = settings.findKeyByToken(key.token());
            assertThat(updated.lastUsed()).isNotNull();
        }

        @Test
        @DisplayName("should not affect other keys")
        void shouldNotAffectOtherKeys() {
            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");
            settings.addKey(key1);
            settings.addKey(key2);

            authService.recordKeyUsage(key1);

            ApiKey updated1 = settings.findKeyByToken(key1.token());
            ApiKey updated2 = settings.findKeyByToken(key2.token());

            assertThat(updated1.lastUsed()).isNotNull();
            assertThat(updated2.lastUsed()).isNull();
        }
    }
}