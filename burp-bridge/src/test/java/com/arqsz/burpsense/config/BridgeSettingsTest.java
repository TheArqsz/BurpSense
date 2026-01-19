package com.arqsz.burpsense.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.constants.PreferenceConstants;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.testutil.MockMontoyaApiFactory;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.Preferences;

@DisplayName("BridgeSettings")
class BridgeSettingsTest {

    private BridgeSettings settings;
    private MontoyaApi api;
    private Preferences prefs;

    @BeforeEach
    void setUp() {
        api = MockMontoyaApiFactory.createBasicMock();
        prefs = api.persistence().preferences();
        settings = new BridgeSettings(prefs, api);
    }

    @Nested
    @DisplayName("API Key Management")
    class ApiKeyManagement {

        @Test
        @DisplayName("should add and retrieve keys")
        void shouldAddAndRetrieveKeys() {
            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");

            settings.addKey(key1);
            settings.addKey(key2);
            List<ApiKey> keys = settings.getApiKeys();

            assertThat(keys).hasSize(2);
            assertThat(keys).extracting(ApiKey::name)
                    .containsExactly("Key1", "Key2");
        }

        @Test
        @DisplayName("should find key by token")
        void shouldFindKeyByToken() {
            ApiKey key = ApiKey.create("TestKey");
            settings.addKey(key);

            ApiKey found = settings.findKeyByToken(key.token());

            assertThat(found).isNotNull();
            assertThat(found.token()).isEqualTo(key.token());
            assertThat(found.name()).isEqualTo("TestKey");
        }

        @Test
        @DisplayName("should return null for unknown token")
        void shouldReturnNullForUnknownToken() {
            ApiKey found = settings.findKeyByToken("unknown-token");

            assertThat(found).isNull();
        }

        @Test
        @DisplayName("should remove key by index")
        void shouldRemoveKeyByIndex() {
            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");
            ApiKey key3 = ApiKey.create("Key3");

            settings.addKey(key1);
            settings.addKey(key2);
            settings.addKey(key3);

            settings.removeKey(1);
            List<ApiKey> keys = settings.getApiKeys();

            assertThat(keys).hasSize(2);
            assertThat(keys).extracting(ApiKey::name)
                    .containsExactly("Key1", "Key3");
        }

        @Test
        @DisplayName("should handle remove with invalid index")
        void shouldHandleRemoveWithInvalidIndex() {
            ApiKey key = ApiKey.create("Key1");
            settings.addKey(key);

            settings.removeKey(10);
            settings.removeKey(-1);

            assertThat(settings.getApiKeys()).hasSize(1);
        }

        @Test
        @DisplayName("should update last used timestamp")
        void shouldUpdateLastUsedTimestamp() {
            ApiKey key = ApiKey.create("TestKey");
            settings.addKey(key);

            settings.updateLastUsed(key.token());

            ApiKey updated = settings.findKeyByToken(key.token());
            assertThat(updated.lastUsed()).isNotNull();
        }

        @Test
        @DisplayName("should not affect other keys when updating last used")
        void shouldNotAffectOtherKeysWhenUpdatingLastUsed() {
            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");
            settings.addKey(key1);
            settings.addKey(key2);

            settings.updateLastUsed(key1.token());

            ApiKey updated1 = settings.findKeyByToken(key1.token());
            ApiKey updated2 = settings.findKeyByToken(key2.token());

            assertThat(updated1.lastUsed()).isNotNull();
            assertThat(updated2.lastUsed()).isNull();
        }

        @Test
        @DisplayName("should handle empty keys list")
        void shouldHandleEmptyKeysList() {
            List<ApiKey> keys = settings.getApiKeys();

            assertThat(keys).isEmpty();
        }
    }

    @Nested
    @DisplayName("Network Configuration")
    class NetworkConfiguration {

        @Test
        @DisplayName("should get default IP when not configured")
        void shouldGetDefaultIpWhenNotConfigured() {
            String ip = settings.getIp();

            assertThat(ip).isEqualTo(ServerConstants.DEFAULT_BIND_ADDRESS);
        }

        @Test
        @DisplayName("should get configured IP")
        void shouldGetConfiguredIp() {
            when(prefs.getString(PreferenceConstants.PREF_BIND_ADDRESS))
                    .thenReturn("192.168.1.100");

            String ip = settings.getIp();

            assertThat(ip).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should set IP address")
        void shouldSetIpAddress() {
            settings.setIp("10.0.0.1");

            verify(prefs).setString(PreferenceConstants.PREF_BIND_ADDRESS, "10.0.0.1");
        }

        @Test
        @DisplayName("should get default port when not configured")
        void shouldGetDefaultPortWhenNotConfigured() {
            int port = settings.getPort();

            assertThat(port).isEqualTo(ServerConstants.DEFAULT_PORT);
        }

        @Test
        @DisplayName("should get configured port")
        void shouldGetConfiguredPort() {
            when(prefs.getInteger(PreferenceConstants.PREF_PORT))
                    .thenReturn(9999);

            int port = settings.getPort();

            assertThat(port).isEqualTo(9999);
        }

        @Test
        @DisplayName("should set port")
        void shouldSetPort() {
            settings.setPort(8888);

            verify(prefs).setInteger(PreferenceConstants.PREF_PORT, 8888);
        }
    }

    @Nested
    @DisplayName("CORS Configuration")
    class CorsConfiguration {

        @Test
        @DisplayName("should get default allowed origins")
        void shouldGetDefaultAllowedOrigins() {
            List<String> origins = settings.getAllowedOrigins();

            assertThat(origins).containsExactlyInAnyOrder(
                    ServerConstants.DEFAULT_ALLOWED_ORIGIN_LOCALHOST,
                    ServerConstants.DEFAULT_ALLOWED_ORIGIN_127);
        }

        @Test
        @DisplayName("should get configured allowed origins")
        void shouldGetConfiguredAllowedOrigins() {
            when(prefs.getString(PreferenceConstants.PREF_ALLOWED_ORIGINS))
                    .thenReturn("http://example.com,https://test.com");

            List<String> origins = settings.getAllowedOrigins();

            assertThat(origins).containsExactly("http://example.com", "https://test.com");
        }

        @Test
        @DisplayName("should set allowed origins")
        void shouldSetAllowedOrigins() {
            List<String> origins = List.of("http://app1.com", "http://app2.com");

            settings.setAllowedOrigins(origins);

            verify(prefs).setString(
                    PreferenceConstants.PREF_ALLOWED_ORIGINS,
                    "http://app1.com,http://app2.com");
        }

        @Test
        @DisplayName("should handle wildcard origin")
        void shouldHandleWildcardOrigin() {
            when(prefs.getString(PreferenceConstants.PREF_ALLOWED_ORIGINS))
                    .thenReturn("*");

            List<String> origins = settings.getAllowedOrigins();

            assertThat(origins).containsExactly("*");
        }

        @Test
        @DisplayName("should handle single origin")
        void shouldHandleSingleOrigin() {
            when(prefs.getString(PreferenceConstants.PREF_ALLOWED_ORIGINS))
                    .thenReturn("http://localhost:3000");

            List<String> origins = settings.getAllowedOrigins();

            assertThat(origins).containsExactly("http://localhost:3000");
        }
    }

    @Nested
    @DisplayName("Cache Management")
    class CacheManagement {

        @Test
        @DisplayName("should cache keys for performance")
        void shouldCacheKeysForPerformance() {
            ApiKey key = ApiKey.create("TestKey");
            settings.addKey(key);

            ApiKey found1 = settings.findKeyByToken(key.token());
            ApiKey found2 = settings.findKeyByToken(key.token());
            ApiKey found3 = settings.findKeyByToken(key.token());

            assertThat(found1).isNotNull();
            assertThat(found2).isNotNull();
            assertThat(found3).isNotNull();
            assertThat(found1.token()).isEqualTo(found2.token());
            assertThat(found2.token()).isEqualTo(found3.token());
        }

        @Test
        @DisplayName("should rebuild cache after adding key")
        void shouldRebuildCacheAfterAddingKey() {
            ApiKey key1 = ApiKey.create("Key1");
            settings.addKey(key1);

            ApiKey key2 = ApiKey.create("Key2");
            settings.addKey(key2);

            assertThat(settings.findKeyByToken(key1.token())).isNotNull();
            assertThat(settings.findKeyByToken(key2.token())).isNotNull();
        }

        @Test
        @DisplayName("should rebuild cache after removing key")
        void shouldRebuildCacheAfterRemovingKey() {
            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");
            settings.addKey(key1);
            settings.addKey(key2);

            settings.removeKey(0);

            assertThat(settings.findKeyByToken(key1.token())).isNull();
            assertThat(settings.findKeyByToken(key2.token())).isNotNull();
        }
    }
}