package com.arqsz.burpsense.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.arqsz.burpsense.constants.IssueConstants;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

/**
 * Service for handling issue-related operations
 */
public class IssueService {

    /**
     * Generates a unique ID for an audit issue
     * 
     * @param issue The audit issue
     * @return A unique 16-character hex ID
     */
    public String generateIssueId(AuditIssue issue) {
        String rawId = String.join(
                IssueConstants.ID_SEPARATOR,
                issue.name(),
                issue.baseUrl(),
                issue.httpService().host());

        try {
            MessageDigest digest = MessageDigest.getInstance(IssueConstants.ID_HASH_ALGORITHM);
            byte[] hash = digest.digest(rawId.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < IssueConstants.ID_HASH_OUTPUT_BYTES; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(rawId.hashCode());
        }
    }

    /**
     * Checks if an issue meets the minimum severity threshold
     * 
     * @param severity  The issue severity
     * @param threshold The minimum required severity
     * @return true if severity is at or above threshold
     */
    public boolean meetsMinimumSeverity(AuditIssueSeverity severity, String threshold) {
        int severityWeight = IssueConstants.SEVERITY_WEIGHTS.getOrDefault(severity.name(), 0);
        int thresholdWeight = IssueConstants.SEVERITY_WEIGHTS.getOrDefault(threshold.toUpperCase(), 0);
        return severityWeight >= thresholdWeight;
    }

    /**
     * Checks if an issue meets the minimum confidence threshold
     * 
     * @param confidence The issue confidence
     * @param threshold  The minimum required confidence
     * @return true if confidence is at or above threshold
     */
    public boolean meetsMinimumConfidence(AuditIssueConfidence confidence, String threshold) {
        int confidenceWeight = IssueConstants.CONFIDENCE_WEIGHTS.getOrDefault(confidence.name(), 0);
        int thresholdWeight = IssueConstants.CONFIDENCE_WEIGHTS.getOrDefault(threshold.toUpperCase(), 0);
        return confidenceWeight >= thresholdWeight;
    }
}