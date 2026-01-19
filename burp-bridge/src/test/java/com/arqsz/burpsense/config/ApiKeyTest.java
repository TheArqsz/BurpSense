package com.arqsz.burpsense.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.constants.PreferenceConstants;

@DisplayName("ApiKey")
class ApiKeyTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern(PreferenceConstants.DATETIME_FORMAT);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create key with valid token")
        void shouldCreateKeyWithValidToken() {
            ApiKey key = ApiKey.create("TestKey");

            assertThat(key.name()).isEqualTo("TestKey");
            assertThat(key.token())
                    .isNotNull()
                    .isNotEmpty();
            assertThat(key.createdDate()).isNotNull();
            assertThat(key.lastUsed()).isNull();
        }

        @Test
        @DisplayName("should generate unique tokens for different keys")
        void shouldGenerateUniqueTokens() {
            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");

            assertThat(key1.token()).isNotEqualTo(key2.token());
        }

        @Test
        @DisplayName("should generate URL-safe tokens")
        void shouldGenerateUrlSafeTokens() {
            ApiKey key = ApiKey.create("TestKey");

            assertThat(key.token())
                    .doesNotContain("+", "/", "=")
                    .matches("^[A-Za-z0-9_-]+$");
        }

        @Test
        @DisplayName("should generate tokens of consistent length")
        void shouldGenerateTokensOfConsistentLength() {

            ApiKey key1 = ApiKey.create("Key1");
            ApiKey key2 = ApiKey.create("Key2");

            assertThat(key1.token()).hasSize(43);
            assertThat(key2.token()).hasSize(43);
        }

        @Test
        @DisplayName("should set creation date in correct format")
        void shouldSetCreationDateInCorrectFormat() {
            ApiKey key = ApiKey.create("TestKey");

            LocalDateTime.parse(key.createdDate(), FORMATTER);
        }

        @Test
        @DisplayName("should handle special characters in name")
        void shouldHandleSpecialCharactersInName() {
            ApiKey key = ApiKey.create("Test Key! @#$%");

            assertThat(key.name()).isEqualTo("Test Key! @#$%");
            assertThat(key.token()).isNotNull();
        }

        @Test
        @DisplayName("should handle empty name")
        void shouldHandleEmptyName() {
            ApiKey key = ApiKey.create("");

            assertThat(key.name()).isEmpty();
            assertThat(key.token()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("withLastUsed")
    class WithLastUsed {

        @Test
        @DisplayName("should create new key with updated last used")
        void shouldCreateNewKeyWithUpdatedLastUsed() {
            ApiKey original = ApiKey.create("TestKey");
            String timestamp = "2024-01-15 10:30:00";

            ApiKey updated = original.withLastUsed(timestamp);

            assertThat(updated.lastUsed()).isEqualTo(timestamp);
            assertThat(updated.name()).isEqualTo(original.name());
            assertThat(updated.token()).isEqualTo(original.token());
            assertThat(updated.createdDate()).isEqualTo(original.createdDate());
        }

        @Test
        @DisplayName("should not modify original key")
        void shouldNotModifyOriginalKey() {
            ApiKey original = ApiKey.create("TestKey");
            String originalLastUsed = original.lastUsed();

            ApiKey updated = original.withLastUsed("2024-01-15 10:30:00");

            assertThat(original.lastUsed()).isEqualTo(originalLastUsed);
            assertThat(updated.lastUsed()).isEqualTo("2024-01-15 10:30:00");
        }

        @Test
        @DisplayName("should handle null timestamp")
        void shouldHandleNullTimestamp() {
            ApiKey original = ApiKey.create("TestKey");

            ApiKey updated = original.withLastUsed(null);

            assertThat(updated.lastUsed()).isNull();
        }
    }

    @Nested
    @DisplayName("withLastUsedNow")
    class WithLastUsedNow {

        @Test
        @DisplayName("should set last used to current timestamp")
        void shouldSetLastUsedToCurrentTimestamp() {
            ApiKey original = ApiKey.create("TestKey");
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            ApiKey updated = original.withLastUsedNow();
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            assertThat(updated.lastUsed()).isNotNull();

            LocalDateTime lastUsedTime = LocalDateTime.parse(updated.lastUsed(), FORMATTER);

            assertThat(lastUsedTime)
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("should not modify other fields")
        void shouldNotModifyOtherFields() {
            ApiKey original = ApiKey.create("TestKey");

            ApiKey updated = original.withLastUsedNow();

            assertThat(updated.name()).isEqualTo(original.name());
            assertThat(updated.token()).isEqualTo(original.token());
            assertThat(updated.createdDate()).isEqualTo(original.createdDate());
        }

        @Test
        @DisplayName("should create new timestamp on each call")
        void shouldCreateNewTimestampOnEachCall() throws InterruptedException {
            ApiKey original = ApiKey.create("TestKey");

            ApiKey updated1 = original.withLastUsedNow();
            Thread.sleep(1100);
            ApiKey updated2 = original.withLastUsedNow();

            assertThat(updated1.lastUsed()).isNotEqualTo(updated2.lastUsed());
        }
    }

    @Nested
    @DisplayName("Record Properties")
    class RecordProperties {

        @Test
        @DisplayName("should support equality by value")
        void shouldSupportEqualityByValue() {
            ApiKey key1 = new ApiKey("Test", "token123", "2024-01-01 00:00:00", null);
            ApiKey key2 = new ApiKey("Test", "token123", "2024-01-01 00:00:00", null);

            assertThat(key1).isEqualTo(key2);
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        }

        @Test
        @DisplayName("should be immutable")
        void shouldBeImmutable() {
            ApiKey key = ApiKey.create("TestKey");
            String originalToken = key.token();

            ApiKey modified = key.withLastUsed("2024-01-01 00:00:00");

            assertThat(key.token()).isEqualTo(originalToken);
            assertThat(key.lastUsed()).isNull();
            assertThat(modified.lastUsed()).isEqualTo("2024-01-01 00:00:00");
        }

        @Test
        @DisplayName("should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            ApiKey key = new ApiKey("TestKey", "token123", "2024-01-01", null);

            String toString = key.toString();

            assertThat(toString)
                    .contains("TestKey")
                    .contains("token123")
                    .contains("2024-01-01");
        }
    }
}