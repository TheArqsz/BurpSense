package com.arqsz.burpsense.util;

import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates and compiles regex patterns with protection against ReDoS attacks
 */
public class RegexValidator {

    private static final int MAX_PATTERN_LENGTH = 500;
    private static final int MAX_QUANTIFIER_REPETITIONS = 100;
    private static final long COMPILATION_TIMEOUT_MS = 100;
    private static final long MATCH_TIMEOUT_MS = 500;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Checks if a regex pattern is valid and safe.
     *
     * @param regex The regex string to check
     * @return true if valid and safe, false otherwise
     */
    public static boolean isValidRegex(String regex) {
        if (regex == null)
            return false;
        if (regex.isEmpty())
            return true;

        try {
            compileSafe(regex, Pattern.CASE_INSENSITIVE);
            return true;
        } catch (RegexValidationException e) {
            return false;
        }
    }

    /**
     * Validates and compiles a regex pattern
     * 
     * @param regex The regex string to compile
     * @param flags Pattern flags (e.g. Pattern.CASE_INSENSITIVE)
     * @return Compiled pattern if safe, null if dangerous or invalid
     * @throws RegexValidationException if pattern is rejected
     */
    public static Pattern compileSafe(String regex, int flags) throws RegexValidationException {
        if (regex == null) {
            throw new RegexValidationException("Pattern cannot be null or empty");
        }

        if (regex.isEmpty()) {
            return Pattern.compile("", flags);
        }

        if (regex.length() > MAX_PATTERN_LENGTH) {
            throw new RegexValidationException(
                    "Pattern too long");
        }

        validatePatternSafety(regex);

        try {
            return compileWithTimeout(regex, flags);
        } catch (TimeoutException e) {
            throw new RegexValidationException("Pattern compilation timed out - likely too complex");
        } catch (PatternSyntaxException e) {
            throw new RegexValidationException("Invalid regex syntax.");
        } catch (Exception e) {
            throw new RegexValidationException("Failed to compile pattern");
        }
    }

    /**
     * Validates pattern safety by checking for ReDoS indicators
     */
    private static void validatePatternSafety(String regex) throws RegexValidationException {
        if (regex.matches(".*\\([^)]*[*+{][^)]*\\)[*+{].*")) {
            throw new RegexValidationException("Nested quantifiers detected - potential ReDoS");
        }

        if (regex.matches(".*\\([^)]*\\|[^)]*\\|[^)]*\\)[*+{].*")) {
            throw new RegexValidationException("Alternation with quantifiers - potential ReDoS");
        }

        if (regex.matches(".*\\{\\d+,\\d+\\}.*")) {
            String[] parts = regex.split("\\{|,|\\}");
            for (String part : parts) {
                try {
                    int num = Integer.parseInt(part.trim());
                    if (num > MAX_QUANTIFIER_REPETITIONS) {
                        throw new RegexValidationException(
                                "Quantifier repetition too high (" + num + ", max " + MAX_QUANTIFIER_REPETITIONS + ")");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        long starCount = regex.chars().filter(ch -> ch == '*').count();
        long plusCount = regex.chars().filter(ch -> ch == '+').count();
        if (starCount + plusCount > 10) {
            throw new RegexValidationException("Too many quantifiers - potential ReDoS");
        }
    }

    /**
     * Compiles pattern with timeout protection
     */
    private static Pattern compileWithTimeout(String regex, int flags)
            throws TimeoutException, PatternSyntaxException {

        Future<Pattern> future = EXECUTOR.submit(() -> Pattern.compile(regex, flags));

        try {
            return future.get(COMPILATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Pattern compilation interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PatternSyntaxException) {
                throw (PatternSyntaxException) cause;
            }
            throw new RuntimeException("Pattern compilation failed", cause);
        }
    }

    /**
     * Performs a pattern match with timeout protection
     * 
     * @param pattern The compiled pattern
     * @param input   The string to match against
     * @return true if match found, false if no match or timeout
     */
    public static boolean findWithTimeout(Pattern pattern, String input) {
        if (input == null) {
            return false;
        }

        Future<Boolean> future = EXECUTOR.submit(() -> pattern.matcher(input).find());

        try {
            return future.get(MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return false;
        }
    }

    /**
     * Exception thrown when regex validation fails
     */
    public static class RegexValidationException extends Exception {
        public RegexValidationException(String message) {
            super(message);
        }
    }
}
