package com.arqsz.burpsense.api.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.IssueService;
import com.arqsz.burpsense.testutil.AuditIssueBuilder;
import com.arqsz.burpsense.testutil.MockMontoyaApiFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.sitemap.SiteMap;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;

@DisplayName("SingleIssueHandler")
class SingleIssueHandlerTest {

    private SingleIssueHandler handler;
    private BridgeServer server;
    private MontoyaApi api;
    private SiteMap siteMap;
    private List<AuditIssue> issues;
    private IssueService issueService;
    private HttpServerExchange exchange;
    private TestResponseSender responseSender;
    private HeaderMap responseHeaders;

    @BeforeEach
    void setUp() {
        api = MockMontoyaApiFactory.createBasicMock();
        siteMap = api.siteMap();

        issues = new ArrayList<>();
        when(siteMap.issues()).thenReturn(issues);

        issueService = new IssueService();

        server = mock(BridgeServer.class);
        when(server.getApi()).thenReturn(api);
        when(server.getIssues()).thenReturn(issues);
        when(server.getIssueService()).thenReturn(issueService);

        handler = new SingleIssueHandler(server);

        exchange = mock(HttpServerExchange.class);
        ServerConnection connection = mock(ServerConnection.class);
        when(exchange.getConnection()).thenReturn(connection);

        responseHeaders = new HeaderMap();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        responseSender = new TestResponseSender();
        when(exchange.getResponseSender()).thenReturn(responseSender);

        when(exchange.getRequestMethod()).thenReturn(Methods.GET);
    }

    @Nested
    @DisplayName("GET " + ServerConstants.ENDPOINT_ISSUE_BY_ID)
    class GetIssueById {

        @Test
        @DisplayName("should return issue when found")
        void shouldReturnIssueWhenFound() throws Exception {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com")
                    .withHost("example.com")
                    .build();
            issues.add(issue);

            String issueId = issueService.generateIssueId(issue);
            setupPathParameter("id", issueId);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.get("id").getAsString()).isEqualTo(issueId);
            assertThat(response.get("name").getAsString()).isEqualTo("SQL Injection");
            assertThat(response.get("baseUrl").getAsString()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("should return 404 when issue not found")
        void shouldReturn404WhenIssueNotFound() throws Exception {
            String nonExistentId = "nonexistent123";
            setupPathParameter("id", nonExistentId);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(404);
            String response = responseSender.getSentData();
            assertThat(response).contains("Issue not found");
            assertThat(response).contains(nonExistentId);
        }

        @Test
        @DisplayName("should return 400 when ID is missing")
        void shouldReturn400WhenIdIsMissing() throws Exception {
            when(exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(null);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(400);
            assertThat(responseSender.getSentData()).contains("Issue ID required");
        }

        @Test
        @DisplayName("should return 400 when ID is empty")
        void shouldReturn400WhenIdIsEmpty() throws Exception {
            setupPathParameter("id", "");

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(400);
            assertThat(responseSender.getSentData()).contains("Issue ID required");
        }

        @Test
        @DisplayName("should set correct content type")
        void shouldSetCorrectContentType() throws Exception {
            AuditIssue issue = AuditIssueBuilder.anIssue().build();
            issues.add(issue);

            String issueId = issueService.generateIssueId(issue);
            setupPathParameter("id", issueId);

            handler.handleRequest(exchange);

            assertThat(responseHeaders.getFirst(Headers.CONTENT_TYPE))
                    .isEqualTo("application/json");
        }

        @Test
        @DisplayName("should find issue among multiple issues")
        void shouldFindIssueAmongMultipleIssues() throws Exception {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("Issue 1")
                    .withBaseUrl("https://example.com/1")
                    .withHost("example.com")
                    .build();
            AuditIssue issue2 = AuditIssueBuilder.anIssue()
                    .withName("Issue 2")
                    .withBaseUrl("https://example.com/2")
                    .withHost("example.com")
                    .build();
            AuditIssue issue3 = AuditIssueBuilder.anIssue()
                    .withName("Issue 3")
                    .withBaseUrl("https://example.com/3")
                    .withHost("example.com")
                    .build();

            issues.add(issue1);
            issues.add(issue2);
            issues.add(issue3);

            String targetId = issueService.generateIssueId(issue2);
            setupPathParameter("id", targetId);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.get("name").getAsString()).isEqualTo("Issue 2");
            assertThat(response.get("id").getAsString()).isEqualTo(targetId);
        }

        @Test
        @DisplayName("should handle issue with full details")
        void shouldHandleIssueWithFullDetails() throws Exception {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("Cross-Site Scripting")
                    .withBaseUrl("https://example.com/page")
                    .withHost("example.com")
                    .withPort(443)
                    .withSecure(true)
                    .withDetail("XSS vulnerability found in parameter")
                    .withRemediation("Sanitize user input")
                    .withBackground("XSS is a common vulnerability")
                    .build();
            issues.add(issue);

            String issueId = issueService.generateIssueId(issue);
            setupPathParameter("id", issueId);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.get("name").getAsString()).isEqualTo("Cross-Site Scripting");
            assertThat(response.get("detail").getAsString()).contains("XSS vulnerability");
            assertThat(response.get("remediation").getAsString()).contains("Sanitize");
            assertThat(response.has("service")).isTrue();
        }

        @Test
        @DisplayName("should distinguish between similar IDs")
        void shouldDistinguishBetweenSimilarIds() throws Exception {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("SQL")
                    .withBaseUrl("https://example.com/a")
                    .withHost("example.com")
                    .build();
            AuditIssue issue2 = AuditIssueBuilder.anIssue()
                    .withName("SQLi")
                    .withBaseUrl("https://example.com/a")
                    .withHost("example.com")
                    .build();

            issues.add(issue1);
            issues.add(issue2);

            String id1 = issueService.generateIssueId(issue1);
            String id2 = issueService.generateIssueId(issue2);

            assertThat(id1).isNotEqualTo(id2);

            setupPathParameter("id", id1);
            handler.handleRequest(exchange);

            JsonObject response1 = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response1.get("name").getAsString()).isEqualTo("SQL");

            responseSender = new TestResponseSender();
            when(exchange.getResponseSender()).thenReturn(responseSender);

            setupPathParameter("id", id2);
            handler.handleRequest(exchange);

            JsonObject response2 = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response2.get("name").getAsString()).isEqualTo("SQLi");
        }

        @Test
        @DisplayName("should handle empty issues list")
        void shouldHandleEmptyIssuesList() throws Exception {
            setupPathParameter("id", "any-id-123");

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(404);
            assertThat(responseSender.getSentData()).contains("Issue not found");
        }
    }

    @Nested
    @DisplayName("Unsupported Methods")
    class UnsupportedMethods {

        @Test
        @DisplayName("should return 405 for POST")
        void shouldReturn405ForPost() throws Exception {
            when(exchange.getRequestMethod()).thenReturn(Methods.POST);
            setupPathParameter("id", "some-id");

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(405);
            assertThat(responseSender.getSentData()).contains("Method Not Allowed");
        }

        @Test
        @DisplayName("should return 405 for PUT")
        void shouldReturn405ForPut() throws Exception {
            when(exchange.getRequestMethod()).thenReturn(Methods.PUT);
            setupPathParameter("id", "some-id");

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(405);
            assertThat(responseSender.getSentData()).contains("Method Not Allowed");
        }

        @Test
        @DisplayName("should return 405 for DELETE")
        void shouldReturn405ForDelete() throws Exception {
            when(exchange.getRequestMethod()).thenReturn(Methods.DELETE);
            setupPathParameter("id", "some-id");

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(405);
            assertThat(responseSender.getSentData()).contains("Method Not Allowed");
        }

        @Test
        @DisplayName("should check method before processing ID")
        void shouldCheckMethodBeforeProcessingId() throws Exception {
            when(exchange.getRequestMethod()).thenReturn(Methods.PATCH);
            when(exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(null);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(405);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle very long issue ID")
        void shouldHandleVeryLongIssueId() throws Exception {
            String longId = "a".repeat(1000);
            setupPathParameter("id", longId);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(404);
        }

        @Test
        @DisplayName("should handle special characters in ID")
        void shouldHandleSpecialCharactersInId() throws Exception {
            String specialId = "abc-123_def.456";
            setupPathParameter("id", specialId);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(404);
        }

        @Test
        @DisplayName("should handle whitespace in ID")
        void shouldHandleWhitespaceInId() throws Exception {
            AuditIssue issue = AuditIssueBuilder.anIssue().build();
            issues.add(issue);

            String issueId = issueService.generateIssueId(issue);
            setupPathParameter("id", "  " + issueId + "  ");

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(404);
        }

        @Test
        @DisplayName("should handle PathTemplateMatch with null parameters")
        void shouldHandlePathTemplateMatchWithNullParameters() throws Exception {
            PathTemplateMatch pathMatch = mock(PathTemplateMatch.class);
            when(pathMatch.getParameters()).thenReturn(null);
            when(exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(pathMatch);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(400);
        }

        @Test
        @DisplayName("should handle PathTemplateMatch with empty parameters map")
        void shouldHandlePathTemplateMatchWithEmptyParametersMap() throws Exception {
            PathTemplateMatch pathMatch = mock(PathTemplateMatch.class);
            when(pathMatch.getParameters()).thenReturn(new HashMap<>());
            when(exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(pathMatch);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(400);
        }
    }

    private void setupPathParameter(String key, String value) {
        PathTemplateMatch pathMatch = mock(PathTemplateMatch.class);
        Map<String, String> params = new HashMap<>();
        params.put(key, value);
        when(pathMatch.getParameters()).thenReturn(params);
        when(exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(pathMatch);
    }

    /**
     * Test implementation of HttpServerExchange.ResponseSender
     */
    private static class TestResponseSender implements Sender {
        private String sentData;

        @Override
        public void send(String data) {
            this.sentData = data;
        }

        @Override
        public void send(String data, Charset charset) {
            this.sentData = data;
        }

        @Override
        public void send(ByteBuffer buffer) {
        }

        @Override
        public void send(ByteBuffer[] buffers) {
        }

        @Override
        public void send(ByteBuffer buffer, IoCallback callback) {
        }

        @Override
        public void send(ByteBuffer[] buffers, IoCallback callback) {
        }

        @Override
        public void send(String data, IoCallback callback) {
            this.sentData = data;
            if (callback != null) {
                callback.onComplete(null, null);
            }
        }

        @Override
        public void send(String data, Charset charset, IoCallback callback) {
            this.sentData = data;
            if (callback != null) {
                callback.onComplete(null, null);
            }
        }

        @Override
        public void transferFrom(java.nio.channels.FileChannel source, IoCallback callback) {
        }

        @Override
        public void close() {
        }

        @Override
        public void close(IoCallback callback) {
        }

        public String getSentData() {
            return sentData;
        }
    }
}