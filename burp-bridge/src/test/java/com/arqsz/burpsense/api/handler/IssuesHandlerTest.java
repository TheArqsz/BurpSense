package com.arqsz.burpsense.api.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.constants.IssueConstants;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.IssueService;
import com.arqsz.burpsense.testutil.AuditIssueBuilder;
import com.arqsz.burpsense.testutil.MockMontoyaApiFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import burp.api.montoya.scope.Scope;
import burp.api.montoya.sitemap.SiteMap;
import io.undertow.io.Receiver;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.Methods;

@DisplayName("IssuesHandler")
class IssuesHandlerTest {

    private IssuesHandler handler;
    private BridgeServer server;
    private MontoyaApi api;
    private SiteMap siteMap;
    private Scope scope;
    private List<AuditIssue> issues;
    private HttpServerExchange exchange;
    private TestResponseSender responseSender;
    private HeaderMap responseHeaders;

    @BeforeEach
    void setUp() {
        api = MockMontoyaApiFactory.createBasicMock();
        siteMap = api.siteMap();
        scope = mock(Scope.class);
        when(api.scope()).thenReturn(scope);

        issues = new ArrayList<>();
        when(siteMap.issues()).thenReturn(issues);

        server = mock(BridgeServer.class);
        when(server.getApi()).thenReturn(api);
        when(server.getIssues()).thenReturn(issues);
        when(server.getIssueService()).thenReturn(new IssueService());

        handler = new IssuesHandler(server);

        exchange = mock(HttpServerExchange.class);
        ServerConnection connection = mock(ServerConnection.class);
        when(exchange.getConnection()).thenReturn(connection);

        responseHeaders = new HeaderMap();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        responseSender = new TestResponseSender();
        when(exchange.getResponseSender()).thenReturn(responseSender);

        when(exchange.getQueryParameters()).thenReturn(new HashMap<>());
    }

    @Nested
    @DisplayName("GET " + ServerConstants.ENDPOINT_ISSUES)
    class GetIssues {

        @BeforeEach
        void setUp() {
            when(exchange.getRequestMethod()).thenReturn(Methods.GET);
        }

        @Test
        @DisplayName("should return empty arrays when no issues")
        void shouldReturnEmptyArraysWhenNoIssues() throws Exception {
            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).isEmpty();
            assertThat(response.getAsJsonArray("removedIds")).isEmpty();
        }

        @Test
        @DisplayName("should return all issues when no filters")
        void shouldReturnAllIssuesWhenNoFilters() throws Exception {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withSeverity(AuditIssueSeverity.HIGH)
                    .build();
            AuditIssue issue2 = AuditIssueBuilder.anIssue()
                    .withName("XSS")
                    .withSeverity(AuditIssueSeverity.MEDIUM)
                    .build();

            issues.add(issue1);
            issues.add(issue2);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(2);
        }

        @Test
        @DisplayName("should filter by severity")
        void shouldFilterBySeverity() throws Exception {
            AuditIssue highIssue = AuditIssueBuilder.anIssue()
                    .withName("Critical")
                    .withSeverity(AuditIssueSeverity.HIGH)
                    .build();
            AuditIssue lowIssue = AuditIssueBuilder.anIssue()
                    .withName("Minor")
                    .withSeverity(AuditIssueSeverity.LOW)
                    .build();

            issues.add(highIssue);
            issues.add(lowIssue);

            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_MIN_SEVERITY,
                    new ArrayDeque<>(Collections.singletonList("HIGH")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("newIssues").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("Critical");
        }

        @Test
        @DisplayName("should filter by confidence")
        void shouldFilterByConfidence() throws Exception {
            AuditIssue certainIssue = AuditIssueBuilder.anIssue()
                    .withName("Certain")
                    .withConfidence(AuditIssueConfidence.CERTAIN)
                    .build();
            AuditIssue tentativeIssue = AuditIssueBuilder.anIssue()
                    .withName("Tentative")
                    .withConfidence(AuditIssueConfidence.TENTATIVE)
                    .build();

            issues.add(certainIssue);
            issues.add(tentativeIssue);

            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_MIN_CONFIDENCE,
                    new ArrayDeque<>(Collections.singletonList("CERTAIN")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("newIssues").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("Certain");
        }

        @Test
        @DisplayName("should filter by scope when inScopeOnly=true")
        void shouldFilterByScopeWhenInScopeOnly() throws Exception {
            AuditIssue inScopeIssue = AuditIssueBuilder.anIssue()
                    .withName("In Scope")
                    .withBaseUrl("https://inscope.com")
                    .build();
            AuditIssue outOfScopeIssue = AuditIssueBuilder.anIssue()
                    .withName("Out of Scope")
                    .withBaseUrl("https://outofscope.com")
                    .build();

            issues.add(inScopeIssue);
            issues.add(outOfScopeIssue);

            when(scope.isInScope("https://inscope.com")).thenReturn(true);
            when(scope.isInScope("https://outofscope.com")).thenReturn(false);

            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_IN_SCOPE,
                    new ArrayDeque<>(Collections.singletonList("true")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("newIssues").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("In Scope");
        }

        @Test
        @DisplayName("should filter by name regex")
        void shouldFilterByNameRegex() throws Exception {
            AuditIssue sqlIssue = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .build();
            AuditIssue xssIssue = AuditIssueBuilder.anIssue()
                    .withName("Cross-site Scripting")
                    .build();

            issues.add(sqlIssue);
            issues.add(xssIssue);

            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_NAME_REGEX,
                    new ArrayDeque<>(Collections.singletonList("SQL")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("newIssues").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("SQL Injection");
        }

        @Test
        @DisplayName("should return 400 for invalid regex")
        void shouldReturn400ForInvalidRegex() throws Exception {
            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_NAME_REGEX,
                    new ArrayDeque<>(Collections.singletonList("[")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(400);
            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").getAsString()).contains("Invalid regex pattern");
        }

        @Test
        @DisplayName("should combine multiple filters")
        void shouldCombineMultipleFilters() throws Exception {
            AuditIssue matchingIssue = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withSeverity(AuditIssueSeverity.HIGH)
                    .withConfidence(AuditIssueConfidence.CERTAIN)
                    .build();
            AuditIssue nonMatchingIssue = AuditIssueBuilder.anIssue()
                    .withName("XSS")
                    .withSeverity(AuditIssueSeverity.LOW)
                    .withConfidence(AuditIssueConfidence.TENTATIVE)
                    .build();

            issues.add(matchingIssue);
            issues.add(nonMatchingIssue);

            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_MIN_SEVERITY,
                    new ArrayDeque<>(Collections.singletonList("HIGH")));
            queryParams.put(IssueConstants.QUERY_PARAM_MIN_CONFIDENCE,
                    new ArrayDeque<>(Collections.singletonList("CERTAIN")));
            queryParams.put(IssueConstants.QUERY_PARAM_NAME_REGEX,
                    new ArrayDeque<>(Collections.singletonList("SQL")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("newIssues").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("SQL Injection");
        }
    }

    @Nested
    @DisplayName("POST " + ServerConstants.ENDPOINT_ISSUES)
    class PostIssues {

        @BeforeEach
        void setUp() {
            when(exchange.getRequestMethod()).thenReturn(Methods.POST);
        }

        @Test
        @DisplayName("should filter out known issues")
        void shouldFilterOutKnownIssues() throws Exception {
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

            issues.add(issue1);
            issues.add(issue2);

            IssueService issueService = new IssueService();
            String issue1Id = issueService.generateIssueId(issue1);

            JsonObject body = new JsonObject();
            JsonArray knownIds = new JsonArray();
            knownIds.add(issue1Id);
            body.add("knownIds", knownIds);

            setupPostRequest(body.toString());

            handler.handleRequest(exchange);
            waitForAsyncCompletion();

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("newIssues").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("Issue 2");
        }

        @Test
        @DisplayName("should identify removed issues")
        void shouldIdentifyRemovedIssues() throws Exception {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("Issue 1")
                    .withBaseUrl("https://example.com/1")
                    .withHost("example.com")
                    .build();

            issues.add(issue1);

            JsonObject body = new JsonObject();
            JsonArray knownIds = new JsonArray();
            knownIds.add("existing-id-123");
            knownIds.add("removed-id-456");
            body.add("knownIds", knownIds);

            setupPostRequest(body.toString());

            handler.handleRequest(exchange);
            waitForAsyncCompletion();

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("removedIds")).hasSize(2);
        }

        @Test
        @DisplayName("should handle empty knownIds array")
        void shouldHandleEmptyKnownIdsArray() throws Exception {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("New Issue")
                    .build();
            issues.add(issue);

            JsonObject body = new JsonObject();
            body.add("knownIds", new JsonArray());

            setupPostRequest(body.toString());

            handler.handleRequest(exchange);
            waitForAsyncCompletion();

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("removedIds")).isEmpty();
        }

        @Test
        @DisplayName("should handle missing knownIds field")
        void shouldHandleMissingKnownIdsField() throws Exception {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("New Issue")
                    .build();
            issues.add(issue);

            JsonObject body = new JsonObject();

            setupPostRequest(body.toString());

            handler.handleRequest(exchange);
            waitForAsyncCompletion();

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
        }

        @Test
        @DisplayName("should handle invalid JSON gracefully")
        void shouldHandleInvalidJsonGracefully() throws Exception {
            setupPostRequest("{invalid json");

            handler.handleRequest(exchange);
            waitForAsyncCompletion();

            assertThat(responseSender.getSentData()).isNotNull();
            verify(api.logging()).logToError(anyString());
        }

        @Test
        @DisplayName("should handle POST with query parameters")
        void shouldHandlePostWithQueryParameters() throws Exception {
            AuditIssue highIssue = AuditIssueBuilder.anIssue()
                    .withName("High Severity")
                    .withSeverity(AuditIssueSeverity.HIGH)
                    .build();
            AuditIssue lowIssue = AuditIssueBuilder.anIssue()
                    .withName("Low Severity")
                    .withSeverity(AuditIssueSeverity.LOW)
                    .build();

            issues.add(highIssue);
            issues.add(lowIssue);

            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_MIN_SEVERITY,
                    new ArrayDeque<>(Collections.singletonList("HIGH")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            JsonObject body = new JsonObject();
            body.add("knownIds", new JsonArray());

            setupPostRequest(body.toString());

            handler.handleRequest(exchange);
            waitForAsyncCompletion();

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
            assertThat(response.getAsJsonArray("newIssues").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("High Severity");
        }

        private void setupPostRequest(String body) {
            Receiver receiver = mock(Receiver.class);
            when(exchange.getRequestReceiver()).thenReturn(receiver);

            doAnswer(invocation -> {
                Receiver.FullStringCallback callback = invocation.getArgument(0);
                callback.handle(exchange, body);
                return null;
            }).when(receiver).receiveFullString(any(Receiver.FullStringCallback.class));
        }

        private void waitForAsyncCompletion() throws InterruptedException {
            Thread.sleep(100);
        }
    }

    @Nested
    @DisplayName("Unsupported Methods")
    class UnsupportedMethods {

        @Test
        @DisplayName("should return 405 for PUT")
        void shouldReturn405ForPut() throws Exception {
            when(exchange.getRequestMethod()).thenReturn(Methods.PUT);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(405);
            assertThat(responseSender.getSentData()).contains("Method Not Allowed");
        }

        @Test
        @DisplayName("should return 405 for DELETE")
        void shouldReturn405ForDelete() throws Exception {
            when(exchange.getRequestMethod()).thenReturn(Methods.DELETE);

            handler.handleRequest(exchange);

            verify(exchange).setStatusCode(405);
            assertThat(responseSender.getSentData()).contains("Method Not Allowed");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @BeforeEach
        void setUp() {
            when(exchange.getRequestMethod()).thenReturn(Methods.GET);
        }

        @Test
        @DisplayName("should handle case-insensitive regex")
        void shouldHandleCaseInsensitiveRegex() throws Exception {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("sql injection")
                    .build();
            issues.add(issue);

            Map<String, Deque<String>> queryParams = new HashMap<>();
            queryParams.put(IssueConstants.QUERY_PARAM_NAME_REGEX,
                    new ArrayDeque<>(Collections.singletonList("SQL")));
            when(exchange.getQueryParameters()).thenReturn(queryParams);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(1);
        }

        @Test
        @DisplayName("should handle large number of issues")
        void shouldHandleLargeNumberOfIssues() throws Exception {
            for (int i = 0; i < 100; i++) {
                issues.add(AuditIssueBuilder.anIssue()
                        .withName("Issue " + i)
                        .withBaseUrl("https://example.com/" + i)
                        .build());
            }

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(100);
        }

        @Test
        @DisplayName("should handle issues with identical names but different URLs")
        void shouldHandleIssuesWithIdenticalNamesButDifferentUrls() throws Exception {
            AuditIssue issue1 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com/page1")
                    .withHost("example.com")
                    .build();
            AuditIssue issue2 = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com/page2")
                    .withHost("example.com")
                    .build();

            issues.add(issue1);
            issues.add(issue2);

            handler.handleRequest(exchange);

            JsonObject response = JsonParser.parseString(responseSender.getSentData()).getAsJsonObject();
            assertThat(response.getAsJsonArray("newIssues")).hasSize(2);

            String id1 = response.getAsJsonArray("newIssues").get(0).getAsJsonObject().get("id").getAsString();
            String id2 = response.getAsJsonArray("newIssues").get(1).getAsJsonObject().get("id").getAsString();
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    /**
     * Test implementation of HttpServerExchange.ResponseSender
     */
    private static class TestResponseSender implements io.undertow.io.Sender {
        private String sentData;

        @Override
        public void send(String data) {
            this.sentData = data;
        }

        @Override
        public void send(String data, java.nio.charset.Charset charset) {
            this.sentData = data;
        }

        @Override
        public void send(java.nio.ByteBuffer buffer) {
        }

        @Override
        public void send(java.nio.ByteBuffer[] buffers) {
        }

        @Override
        public void send(java.nio.ByteBuffer buffer, io.undertow.io.IoCallback callback) {
        }

        @Override
        public void send(java.nio.ByteBuffer[] buffers, io.undertow.io.IoCallback callback) {
        }

        @Override
        public void send(String data, io.undertow.io.IoCallback callback) {
            this.sentData = data;
            if (callback != null) {
                callback.onComplete(null, null);
            }
        }

        @Override
        public void send(String data, java.nio.charset.Charset charset, io.undertow.io.IoCallback callback) {
            this.sentData = data;
            if (callback != null) {
                callback.onComplete(null, null);
            }
        }

        @Override
        public void transferFrom(java.nio.channels.FileChannel source, io.undertow.io.IoCallback callback) {
        }

        @Override
        public void close() {
        }

        @Override
        public void close(io.undertow.io.IoCallback callback) {
        }

        public String getSentData() {
            return sentData;
        }
    }
}
