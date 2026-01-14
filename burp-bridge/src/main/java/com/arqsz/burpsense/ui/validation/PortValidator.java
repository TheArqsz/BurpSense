package com.arqsz.burpsense.ui.validation;

import com.arqsz.burpsense.constants.UIConstants;

/**
 * Validator for port numbers
 */
public class PortValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final ValidationLevel level;

        public enum ValidationLevel {
            VALID,
            WARNING,
            ERROR
        }

        private ValidationResult(boolean valid, String message, ValidationLevel level) {
            this.valid = valid;
            this.message = message;
            this.level = level;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, "", ValidationLevel.VALID);
        }

        public static ValidationResult warning(String message) {
            return new ValidationResult(true, message, ValidationLevel.WARNING);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, ValidationLevel.ERROR);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public ValidationLevel getLevel() {
            return level;
        }
    }

    /**
     * Validates a port number string
     * 
     * @param portText The port number as a string
     * @return Validation result
     */
    public static ValidationResult validate(String portText) {
        if (portText == null || portText.trim().isEmpty()) {
            return ValidationResult.error("Port cannot be empty");
        }

        try {
            int port = Integer.parseInt(portText.trim());

            if (port < UIConstants.PORT_MIN || port > UIConstants.PORT_MAX) {
                return ValidationResult.error(
                        String.format("Port must be between %d and %d",
                                UIConstants.PORT_MIN, UIConstants.PORT_MAX));
            }

            if (port < UIConstants.PORT_PRIVILEGED_THRESHOLD) {
                return ValidationResult.warning(
                        String.format("Ports below %d may require administrator privileges",
                                UIConstants.PORT_PRIVILEGED_THRESHOLD));
            }

            return ValidationResult.valid();

        } catch (NumberFormatException e) {
            return ValidationResult.error("Port must be a valid number");
        }
    }
}