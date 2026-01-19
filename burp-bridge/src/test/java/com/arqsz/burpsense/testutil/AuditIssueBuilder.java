package com.arqsz.burpsense.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueDefinition;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

/**
 * Builder for creating mock AuditIssue instances for testing
 */
public class AuditIssueBuilder {

    private String name = "Test Issue";
    private String baseUrl = "https://example.com";
    private String host = "example.com";
    private int port = 443;
    private boolean secure = true;
    private AuditIssueSeverity severity = AuditIssueSeverity.MEDIUM;
    private AuditIssueConfidence confidence = AuditIssueConfidence.CERTAIN;
    private String detail = "Test issue detail";
    private String remediation = null;
    private String background = null;
    private List<HttpRequestResponse> requestResponses = new ArrayList<>();

    public AuditIssueBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public AuditIssueBuilder withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public AuditIssueBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    public AuditIssueBuilder withPort(int port) {
        this.port = port;
        return this;
    }

    public AuditIssueBuilder withSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public AuditIssueBuilder withSeverity(AuditIssueSeverity severity) {
        this.severity = severity;
        return this;
    }

    public AuditIssueBuilder withConfidence(AuditIssueConfidence confidence) {
        this.confidence = confidence;
        return this;
    }

    public AuditIssueBuilder withDetail(String detail) {
        this.detail = detail;
        return this;
    }

    public AuditIssueBuilder withRemediation(String remediation) {
        this.remediation = remediation;
        return this;
    }

    public AuditIssueBuilder withBackground(String background) {
        this.background = background;
        return this;
    }

    public AuditIssueBuilder withRequestResponse(HttpRequestResponse requestResponse) {
        this.requestResponses.add(requestResponse);
        return this;
    }

    public AuditIssue build() {
        AuditIssue issue = mock(AuditIssue.class);

        when(issue.name()).thenReturn(name);
        when(issue.baseUrl()).thenReturn(baseUrl);
        when(issue.severity()).thenReturn(severity);
        when(issue.confidence()).thenReturn(confidence);
        when(issue.detail()).thenReturn(detail);
        when(issue.remediation()).thenReturn(remediation);
        when(issue.requestResponses()).thenReturn(requestResponses);

        var httpService = mock(HttpService.class);
        when(httpService.host()).thenReturn(host);
        when(httpService.port()).thenReturn(port);
        when(httpService.secure()).thenReturn(secure);
        when(issue.httpService()).thenReturn(httpService);

        if (background != null) {
            AuditIssueDefinition definition = mock(AuditIssueDefinition.class);
            when(definition.name()).thenReturn(name);
            when(definition.background()).thenReturn(background);
            when(issue.definition()).thenReturn(definition);
        }

        return issue;
    }

    public static AuditIssueBuilder anIssue() {
        return new AuditIssueBuilder();
    }
}
