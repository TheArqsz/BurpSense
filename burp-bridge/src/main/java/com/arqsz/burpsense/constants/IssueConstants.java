package com.arqsz.burpsense.constants;

import java.util.Map;

/**
 * Constants for issue severity and confidence levels
 */
public final class IssueConstants {

    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_INFORMATION = "INFORMATION";

    public static final String CONFIDENCE_CERTAIN = "CERTAIN";
    public static final String CONFIDENCE_FIRM = "FIRM";
    public static final String CONFIDENCE_TENTATIVE = "TENTATIVE";

    public static final Map<String, Integer> SEVERITY_WEIGHTS = Map.of(
            SEVERITY_HIGH, 4,
            SEVERITY_MEDIUM, 3,
            SEVERITY_LOW, 2,
            SEVERITY_INFORMATION, 1);

    public static final Map<String, Integer> CONFIDENCE_WEIGHTS = Map.of(
            CONFIDENCE_CERTAIN, 3,
            CONFIDENCE_FIRM, 2,
            CONFIDENCE_TENTATIVE, 1);

    public static final String QUERY_PARAM_MIN_SEVERITY = "minSeverity";
    public static final String QUERY_PARAM_MIN_CONFIDENCE = "minConfidence";
    public static final String QUERY_PARAM_IN_SCOPE = "inScope";
    public static final String QUERY_PARAM_NAME_REGEX = "nameRegex";

    public static final String DEFAULT_MIN_SEVERITY = SEVERITY_MEDIUM;
    public static final String DEFAULT_MIN_CONFIDENCE = CONFIDENCE_FIRM;
    public static final String DEFAULT_NAME_REGEX = ".*";

    public static final String ID_HASH_ALGORITHM = "SHA-256";
    public static final int ID_HASH_OUTPUT_BYTES = 8;
    public static final String ID_SEPARATOR = "|";

    private IssueConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}