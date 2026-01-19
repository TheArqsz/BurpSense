package com.arqsz.burpsense.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RegexValidator")
class RegexValidatorTest {

    @ParameterizedTest(name = "Valid pattern: {0}")
    @ValueSource(strings = {
            ".*",
            "^test.*",
            "[a-z]+",
            "\\d{3}-\\d{4}",
            "(foo|bar)",
            "^https?://.*",
            "\\w+@\\w+\\.\\w+",
            "[A-Z][a-z]*",
            "\\s*",
            "a{2,5}"
    })
    @DisplayName("should accept valid regex patterns")
    void shouldAcceptValidPatterns(String pattern) {
        assertThat(RegexValidator.isValidRegex(pattern)).isTrue();
    }

    @ParameterizedTest(name = "Invalid pattern: {0}")
    @ValueSource(strings = {
            "[",
            "(",
            "*",
            "(?P<invalid>)",
            "\\x{gggg}",
            "[z-a]",
            "(?<)",
            "**"
    })
    @DisplayName("should reject invalid regex patterns")
    void shouldRejectInvalidPatterns(String pattern) {
        assertThat(RegexValidator.isValidRegex(pattern)).isFalse();
    }

    @Test
    @DisplayName("should handle null pattern")
    void shouldHandleNullPattern() {
        assertThat(RegexValidator.isValidRegex(null)).isFalse();
    }

    @Test
    @DisplayName("should handle empty pattern")
    void shouldHandleEmptyPattern() {
        assertThat(RegexValidator.isValidRegex("")).isTrue();
    }

    @Test
    @DisplayName("should reject unclosed character class")
    void shouldRejectUnclosedCharacterClass() {
        assertThat(RegexValidator.isValidRegex("[abc")).isFalse();
    }

    @Test
    @DisplayName("should reject unclosed group")
    void shouldRejectUnclosedGroup() {
        assertThat(RegexValidator.isValidRegex("(abc")).isFalse();
    }

    @Test
    @DisplayName("should accept escaped special characters")
    void shouldAcceptEscapedSpecialCharacters() {
        assertThat(RegexValidator.isValidRegex("\\[\\]\\(\\)\\{\\}\\.\\*\\+\\?\\^\\$\\|\\\\")).isTrue();
    }
}
