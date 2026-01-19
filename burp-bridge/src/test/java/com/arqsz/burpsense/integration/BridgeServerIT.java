package com.arqsz.burpsense.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.config.ApiKey;
import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.IssueConstants;
import com.arqsz.burpsense.constants.PreferenceConstants;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.testutil.AuditIssueBuilder;
import com.arqsz.burpsense.testutil.MockMontoyaApiFactory;
import com.arqsz.burpsense.testutil.TestConstants;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@DisplayName("BridgeServer Integration Tests")
@TestInstance(Lifecycle.PER_CLASS)
class BridgeServerIT {

    private BridgeServer server;
    private MontoyaApi api;
    private BridgeSettings settings;
    private String validToken;

    @BeforeEach
    void resetMocks() {
        when(api.siteMap().issues()).thenReturn(List.of());
    }

    @BeforeAll
    void setupSuite() throws Exception {
        api = MockMontoyaApiFactory.createBasicMock();

        ApiKey key = ApiKey.create("IntegrationTest");
        this.validToken = key.token();

        var prefs = api.persistence().preferences();
        when(prefs.getInteger(PreferenceConstants.PREF_PORT)).thenReturn(TestConstants.TEST_PORT);
        when(prefs.getString(PreferenceConstants.PREF_BIND_ADDRESS)).thenReturn(TestConstants.TEST_HOST);

        settings = new BridgeSettings(prefs, api);
        settings.addKey(key);
        settings.setAllowedOrigins(List.of("http://localhost:3000"));

        RestAssured.baseURI = "http://" + TestConstants.TEST_HOST;
        RestAssured.port = TestConstants.TEST_PORT;

        server = new BridgeServer(api, settings);
        server.start();

        await().atMost(5, TimeUnit.SECONDS).until(server::isRunning);
    }

    @AfterAll
    void tearDownSuite() {
        if (server != null) {
            server.stop();
        }
        RestAssured.reset();
    }

    @Nested
    @DisplayName("Health Endpoint")
    class HealthEndpoint {

        @Test
        @DisplayName("should return OK status")
        void shouldReturnOkStatus() {
            given()
                    .header("Authorization", "Bearer " + validToken)
                    .when()
                    .get(ServerConstants.ENDPOINT_HEALTH)
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("ok"));
        }

        @Test
        @DisplayName("should reject request without API key")
        void shouldRejectRequestWithoutApiKey() {
            given()
                    .when()
                    .get(ServerConstants.ENDPOINT_HEALTH)
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("should reject request with invalid API key")
        void shouldRejectRequestWithInvalidApiKey() {
            given()
                    .header("Authorization", "Bearer invalid-token")
                    .when()
                    .get(ServerConstants.ENDPOINT_HEALTH)
                    .then()
                    .statusCode(401);
        }
    }

    @Nested
    @DisplayName("Issues Endpoint")
    class IssuesEndpoint {

        @Test
        @DisplayName("should return empty array when no issues")
        void shouldReturnEmptyArrayWhenNoIssues() {
            given()
                    .header("Authorization", "Bearer " + validToken)
                    .contentType(ContentType.JSON)
                    .when()
                    .get(ServerConstants.ENDPOINT_ISSUES)
                    .then()
                    .statusCode(200)
                    .body("newIssues", hasSize(0));
        }

        @Test
        @DisplayName("should return issues with correct structure")
        void shouldReturnIssuesWithCorrectStructure() {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com")
                    .build();

            when(api.siteMap().issues()).thenReturn(List.of(issue));

            given()
                    .header("Authorization", "Bearer " + validToken)
                    .contentType(ContentType.JSON)
                    .when()
                    .get(ServerConstants.ENDPOINT_ISSUES)
                    .then()
                    .statusCode(200)
                    .body("newIssues", hasSize(1))
                    .body("newIssues[0].name", equalTo("SQL Injection"))
                    .body("newIssues[0].baseUrl", equalTo("https://example.com"));
        }

        @Test
        @DisplayName("should filter by severity")
        void shouldFilterBySeverity() {
            AuditIssue highIssue = AuditIssueBuilder.anIssue()
                    .withName("Critical SQLi")
                    .withSeverity(AuditIssueSeverity.HIGH)
                    .build();
            AuditIssue infoIssue = AuditIssueBuilder.anIssue()
                    .withName("Info Leak")
                    .withSeverity(AuditIssueSeverity.INFORMATION)
                    .build();

            when(api.siteMap().issues()).thenReturn(List.of(highIssue, infoIssue));

            given()
                    .header("Authorization", "Bearer " + validToken)
                    .queryParam(IssueConstants.QUERY_PARAM_MIN_SEVERITY, "HIGH")
                    .contentType(ContentType.JSON)
                    .when()
                    .get(ServerConstants.ENDPOINT_ISSUES)
                    .then()
                    .statusCode(200)
                    .body("newIssues", hasSize(1))
                    .body("newIssues[0].name", equalTo("Critical SQLi"));
        }
    }

    @Nested
    @DisplayName("WebSocket Endpoint")
    class WebSocketEndpoint {

        @BeforeEach
        void clearWsClients() {
            java.util.Set<?> clients = getWsClientsFromServer();
            for (Object channel : clients) {
                try {
                    java.lang.reflect.Method sendClose = channel.getClass().getMethod("sendClose");
                    sendClose.invoke(channel);
                } catch (Exception ignored) {
                }
            }
            clients.clear();
            server.broadcastUpdate();
            server.resetMonitoringState();
        }

        @Test
        @DisplayName("should accept authenticated WebSocket connections")
        void shouldAcceptWebSocketConnections() throws Exception {
            String wsUrl = "ws://" + TestConstants.TEST_HOST + ":" + TestConstants.TEST_PORT
                    + ServerConstants.ENDPOINT_WS;

            WebSocket webSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .header("Authorization", "Bearer " + validToken)
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    })
                    .get(5, TimeUnit.SECONDS);

            assertThat(webSocket).isNotNull();

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(getWsClientsFromServer()).hasSize(1);
            });

            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Done").get(2, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("should handle multiple WebSocket clients")
        void shouldHandleMultipleWebSocketClients() throws Exception {
            String wsUrl = "ws://" + TestConstants.TEST_HOST + ":" + TestConstants.TEST_PORT
                    + ServerConstants.ENDPOINT_WS;

            WebSocket client1 = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .header("Authorization", "Bearer " + validToken)
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    })
                    .get(5, TimeUnit.SECONDS);

            WebSocket client2 = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .header("Authorization", "Bearer " + validToken)
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    })
                    .get(5, TimeUnit.SECONDS);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(getWsClientsFromServer()).hasSize(2);
            });

            client1.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
            client2.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
        }

        @Test
        @DisplayName("should remove disconnected WebSocket clients")
        void shouldRemoveDisconnectedWebSocketClients() throws Exception {
            String wsUrl = "ws://" + TestConstants.TEST_HOST + ":" + TestConstants.TEST_PORT
                    + ServerConstants.ENDPOINT_WS;

            WebSocket webSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .header("Authorization", "Bearer " + validToken)
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    })
                    .get(5, TimeUnit.SECONDS);

            await().atMost(5, TimeUnit.SECONDS).until(() -> getWsClientsFromServer().size() == 1);

            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Test disconnect")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .alias("WebSocket client was not removed from server registry")
                    .untilAsserted(() -> {
                        server.broadcastUpdate();
                        assertThat(getWsClientsFromServer())
                                .as("Registry should be empty after close handshake completes")
                                .isEmpty();
                    });
        }

        @Test
        @DisplayName("should receive refresh message on issue changes")
        void shouldReceiveRefreshMessageOnIssueChanges() throws Exception {
            String wsUrl = "ws://" + TestConstants.TEST_HOST + ":" + TestConstants.TEST_PORT
                    + ServerConstants.ENDPOINT_WS;
            CompletableFuture<String> messageFuture = new CompletableFuture<>();

            WebSocket webSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .header("Authorization", "Bearer " + validToken)
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            messageFuture.complete(data.toString());
                            return WebSocket.Listener.super.onText(ws, data, last);
                        }
                    }).get(5, TimeUnit.SECONDS);

            await().atMost(5, TimeUnit.SECONDS).until(() -> getWsClientsFromServer().size() == 1);

            server.resetMonitoringState();
            AuditIssue issue = AuditIssueBuilder.anIssue().build();
            when(api.siteMap().issues()).thenReturn(List.of(issue));

            server.checkForIssueChanges();

            await().atMost(5, TimeUnit.SECONDS)
                    .alias("WebSocket client did not receive refresh message")
                    .untilAsserted(() -> {
                        assertThat(messageFuture).isCompletedWithValue("refresh");
                    });

            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Done").get(2, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        @DisplayName("should include CORS headers in response")
        void shouldIncludeCorsHeaders() {
            settings.setAllowedOrigins(List.of("http://localhost:3000"));

            given()
                    .header("Authorization", "Bearer " + validToken)
                    .header("Origin", "http://localhost:3000")
                    .when()
                    .get(ServerConstants.ENDPOINT_HEALTH)
                    .then()
                    .statusCode(200)
                    .header("Access-Control-Allow-Origin", "http://localhost:3000")
                    .header("Access-Control-Allow-Credentials", "true");
        }

        @Test
        @DisplayName("should handle OPTIONS preflight request")
        void shouldHandleOptionsPreflightRequest() {
            given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .when()
                    .options(ServerConstants.ENDPOINT_ISSUES)
                    .then()
                    .statusCode(204);
        }
    }

    private java.util.Set<?> getWsClientsFromServer() {
        try {
            java.lang.reflect.Field field = com.arqsz.burpsense.api.BridgeServer.class.getDeclaredField("wsClients");
            field.setAccessible(true);
            return (java.util.Set<?>) field.get(server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access shaded wsClients field", e);
        }
    }
}
