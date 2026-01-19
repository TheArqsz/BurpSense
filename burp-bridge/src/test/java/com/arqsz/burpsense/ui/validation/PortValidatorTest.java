package com.arqsz.burpsense.ui.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.arqsz.burpsense.ui.validation.PortValidator.ValidationResult;
import com.arqsz.burpsense.ui.validation.PortValidator.ValidationResult.ValidationLevel;

@DisplayName("PortValidator")
class PortValidatorTest {

    @Nested
    @DisplayName("Valid Ports")
    class ValidPorts {

        @ParameterizedTest(name = "Port {0} should be valid")
        @ValueSource(strings = { "1024", "8080", "3000", "8765", "65535" })
        @DisplayName("should accept valid unprivileged ports")
        void shouldAcceptValidUnprivilegedPorts(String port) {
            ValidationResult result = PortValidator.validate(port);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.VALID);
            assertThat(result.getMessage()).isEmpty();
        }

        @ParameterizedTest(name = "Port {0} should show warning")
        @ValueSource(strings = { "1", "80", "443", "1023" })
        @DisplayName("should warn about privileged ports")
        void shouldWarnAboutPrivilegedPorts(String port) {
            ValidationResult result = PortValidator.validate(port);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.WARNING);
            assertThat(result.getMessage())
                    .isNotEmpty()
                    .contains("administrator privileges");
        }

        @Test
        @DisplayName("should handle port with leading/trailing whitespace")
        void shouldHandlePortWithWhitespace() {
            ValidationResult result = PortValidator.validate("  8080  ");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.VALID);
        }
    }

    @Nested
    @DisplayName("Invalid Ports")
    class InvalidPorts {

        @ParameterizedTest(name = "Port ''{0}'' should be rejected")
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("should reject empty or whitespace-only input")
        void shouldRejectEmptyOrWhitespaceInput(String port) {
            ValidationResult result = PortValidator.validate(port);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.ERROR);
            assertThat(result.getMessage()).contains("cannot be empty");
        }

        @ParameterizedTest(name = "Port ''{0}'' should be rejected")
        @ValueSource(strings = { "abc", "12.34", "port", "8080a", "a8080" })
        @DisplayName("should reject non-numeric input")
        void shouldRejectNonNumericInput(String port) {
            ValidationResult result = PortValidator.validate(port);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.ERROR);
            assertThat(result.getMessage()).contains("valid number");
        }

        @ParameterizedTest(name = "Port {0} should be rejected (out of range)")
        @ValueSource(strings = { "0", "-1", "-100", "65536", "70000", "999999" })
        @DisplayName("should reject ports outside valid range")
        void shouldRejectPortsOutsideValidRange(String port) {
            ValidationResult result = PortValidator.validate(port);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.ERROR);
            assertThat(result.getMessage()).contains("between");
        }

        @Test
        @DisplayName("should reject port with special characters")
        void shouldRejectPortWithSpecialCharacters() {
            ValidationResult result = PortValidator.validate("8080!");

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should reject hexadecimal input")
        void shouldRejectHexadecimalInput() {
            ValidationResult result = PortValidator.validate("0x1F90");

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Boundary Cases")
    class BoundaryCases {

        @Test
        @DisplayName("should accept minimum valid port (1)")
        void shouldAcceptMinimumValidPort() {
            ValidationResult result = PortValidator.validate("1");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.WARNING);
        }

        @Test
        @DisplayName("should accept maximum valid port (65535)")
        void shouldAcceptMaximumValidPort() {
            ValidationResult result = PortValidator.validate("65535");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.VALID);
        }

        @Test
        @DisplayName("should accept first unprivileged port (1024)")
        void shouldAcceptFirstUnprivilegedPort() {
            ValidationResult result = PortValidator.validate("1024");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.VALID);
        }

        @Test
        @DisplayName("should warn for last privileged port (1023)")
        void shouldWarnForLastPrivilegedPort() {
            ValidationResult result = PortValidator.validate("1023");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getLevel()).isEqualTo(ValidationLevel.WARNING);
        }
    }
}