package com.arqsz.burpsense.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.arqsz.burpsense.testutil.AuditIssueBuilder;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

@DisplayName("IssueService")
class IssueServiceTest {

    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueService = new IssueService();
    }

    @Nested
    @DisplayName("generateIssueId")
    class GenerateIssueId {

        @Test
        @DisplayName("should generate consistent ID for same issue")
        void shouldGenerateConsistentId() {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com/api")
                    .withHost("example.com")
                    .build();

            String id1 = issueService.generateIssueId(issue);
            String id2 = issueService.generateIssueId(issue);

            assertThat(id1)
                    .isNotNull()
                    .isNotEmpty()
                    .hasSize(16)
                    .isEqualTo(id2);
        }

        @Test
        @DisplayName("should generate different IDs for different issues")
        void shouldGenerateDifferentIds() {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com/api")
                    .withHost("example.com")
                    .build();

            AuditIssue issue2 = AuditIssueBuilder.anIssue()
                    .withName("XSS")
                    .withBaseUrl("https://example.com/api")
                    .withHost("example.com")
                    .build();

            String id1 = issueService.generateIssueId(issue1);
            String id2 = issueService.generateIssueId(issue2);

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should generate hexadecimal ID")
        void shouldGenerateHexadecimalId() {
            AuditIssue issue = AuditIssueBuilder.anIssue().build();

            String id = issueService.generateIssueId(issue);

            assertThat(id).matches("^[0-9a-f]+$");
        }

        @Test
        @DisplayName("should generate different IDs for same name on different hosts")
        void shouldGenerateDifferentIdsForDifferentHosts() {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com/api")
                    .withHost("example.com")
                    .build();

            AuditIssue issue2 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://test.com/api")
                    .withHost("test.com")
                    .build();

            String id1 = issueService.generateIssueId(issue1);
            String id2 = issueService.generateIssueId(issue2);

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should generate different IDs for same name on different URLs")
        void shouldGenerateDifferentIdsForDifferentUrls() {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com/api/v1")
                    .withHost("example.com")
                    .build();

            AuditIssue issue2 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com/api/v2")
                    .withHost("example.com")
                    .build();

            String id1 = issueService.generateIssueId(issue1);
            String id2 = issueService.generateIssueId(issue2);

            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("meetsMinimumSeverity")
    class MeetsMinimumSeverity {

        @ParameterizedTest(name = "{0} >= {1} should be {2}")
        @CsvSource({
                "HIGH, LOW, true",
                "HIGH, MEDIUM, true",
                "HIGH, HIGH, true",
                "MEDIUM, HIGH, false",
                "LOW, MEDIUM, false",
                "MEDIUM, MEDIUM, true",
                "INFORMATION, LOW, false"
        })
        @DisplayName("should correctly compare severity levels")
        void shouldCorrectlyCompareSeverityLevels(
                String severityStr,
                String thresholdStr,
                boolean expected) {
            AuditIssueSeverity severity = AuditIssueSeverity.valueOf(severityStr);

            boolean result = issueService.meetsMinimumSeverity(severity, thresholdStr);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should handle unknown severity gracefully")
        void shouldHandleUnknownSeverity() {
            AuditIssueSeverity severity = AuditIssueSeverity.MEDIUM;

            boolean result = issueService.meetsMinimumSeverity(severity, "UNKNOWN");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should be case insensitive for threshold")
        void shouldBeCaseInsensitiveForThreshold() {
            AuditIssueSeverity severity = AuditIssueSeverity.HIGH;

            boolean result1 = issueService.meetsMinimumSeverity(severity, "medium");
            boolean result2 = issueService.meetsMinimumSeverity(severity, "MEDIUM");
            boolean result3 = issueService.meetsMinimumSeverity(severity, "MeDiUm");

            assertThat(result1).isTrue();
            assertThat(result2).isTrue();
            assertThat(result3).isTrue();
        }
    }

    @Nested
    @DisplayName("meetsMinimumConfidence")
    class MeetsMinimumConfidence {

        @ParameterizedTest(name = "{0} >= {1} should be {2}")
        @CsvSource({
                "CERTAIN, TENTATIVE, true",
                "CERTAIN, FIRM, true",
                "CERTAIN, CERTAIN, true",
                "FIRM, CERTAIN, false",
                "TENTATIVE, FIRM, false",
                "FIRM, FIRM, true"
        })
        @DisplayName("should correctly compare confidence levels")
        void shouldCorrectlyCompareConfidenceLevels(
                String confidenceStr,
                String thresholdStr,
                boolean expected) {
            AuditIssueConfidence confidence = AuditIssueConfidence.valueOf(confidenceStr);

            boolean result = issueService.meetsMinimumConfidence(confidence, thresholdStr);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should handle unknown confidence gracefully")
        void shouldHandleUnknownConfidence() {
            AuditIssueConfidence confidence = AuditIssueConfidence.FIRM;

            boolean result = issueService.meetsMinimumConfidence(confidence, "UNKNOWN");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should be case insensitive for threshold")
        void shouldBeCaseInsensitiveForThreshold() {
            AuditIssueConfidence confidence = AuditIssueConfidence.CERTAIN;

            boolean result1 = issueService.meetsMinimumConfidence(confidence, "firm");
            boolean result2 = issueService.meetsMinimumConfidence(confidence, "FIRM");
            boolean result3 = issueService.meetsMinimumConfidence(confidence, "FiRm");

            assertThat(result1).isTrue();
            assertThat(result2).isTrue();
            assertThat(result3).isTrue();
        }
    }
}
