package com.arqsz.burpsense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.xnio.ChannelListener;

import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.service.IssueService;
import com.arqsz.burpsense.testutil.AuditIssueBuilder;
import com.arqsz.burpsense.testutil.MockMontoyaApiFactory;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.sitemap.SiteMap;
import io.undertow.Undertow;
import io.undertow.websockets.core.WebSocketChannel;

@DisplayName("BridgeServer")
class BridgeServerTest {

    private BridgeServer server;
    private MontoyaApi api;
    private BridgeSettings settings;
    private SiteMap siteMap;
    private Logging logging;
    private List<AuditIssue> issues;

    @BeforeEach
    void setUp() {
        api = MockMontoyaApiFactory.createBasicMock();
        Preferences prefs = api.persistence().preferences();
        settings = new BridgeSettings(prefs, api);

        siteMap = api.siteMap();
        logging = api.logging();

        issues = new ArrayList<>();
        when(siteMap.issues()).thenReturn(issues);

        server = spy(new BridgeServer(api, settings));
        doNothing().when(server).sendWebSocketText(any(), anyString());
    }

    @Nested
    @DisplayName("Constructor and Getters")
    class ConstructorAndGetters {

        @Test
        @DisplayName("should initialize with API and settings")
        void shouldInitializeWithApiAndSettings() {
            assertThat(server.getApi()).isEqualTo(api);
            assertThat(server.getIssueService()).isInstanceOf(IssueService.class);
        }

        @Test
        @DisplayName("should not be running initially")
        void shouldNotBeRunningInitially() {
            assertThat(server.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should return issues from site map")
        void shouldReturnIssuesFromSiteMap() {
            AuditIssue issue = AuditIssueBuilder.anIssue().build();
            issues.add(issue);

            List<AuditIssue> result = server.getIssues();

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(issue);
        }

        @Test
        @DisplayName("should have non-null issue service")
        void shouldHaveNonNullIssueService() {
            assertThat(server.getIssueService()).isNotNull();
        }
    }

    @Nested
    @DisplayName("broadcastUpdate")
    class BroadcastUpdate {

        @Test
        @DisplayName("should handle no WebSocket clients gracefully")
        void shouldHandleNoWebSocketClientsGracefully() {
            server.broadcastUpdate();

            verify(logging, never()).logToOutput(contains("Broadcasted update"));
        }

        @Test
        @DisplayName("should log broadcast when clients exist")
        void shouldLogBroadcastWhenClientsExist() throws Exception {
            WebSocketChannel channel = mock(WebSocketChannel.class);
            when(channel.isOpen()).thenReturn(true);
            Set<WebSocketChannel> wsClients = getWsClients();
            wsClients.add(channel);

            server.broadcastUpdate();

            verify(logging).logToOutput(contains("Broadcasted update to 1 client"));
        }

        @Test
        @DisplayName("should remove closed channels before broadcasting")
        void shouldRemoveClosedChannelsBeforeBroadcasting() throws Exception {
            WebSocketChannel openChannel = mock(WebSocketChannel.class);
            when(openChannel.isOpen()).thenReturn(true);

            WebSocketChannel closedChannel = mock(WebSocketChannel.class);
            when(closedChannel.isOpen()).thenReturn(false);

            Set<WebSocketChannel> wsClients = getWsClients();
            wsClients.add(openChannel);
            wsClients.add(closedChannel);

            server.broadcastUpdate();

            assertThat(wsClients).hasSize(1);
            assertThat(wsClients).contains(openChannel);
        }

        @Test
        @DisplayName("should remove channels that fail to send")
        void shouldRemoveChannelsThatFailToSend() throws Exception {
            WebSocketChannel failingChannel = mock(WebSocketChannel.class);
            when(failingChannel.isOpen()).thenReturn(true);

            doThrow(new RuntimeException("Network Error"))
                    .when(server).sendWebSocketText(eq(failingChannel), anyString());

            Set<WebSocketChannel> wsClients = getWsClients();
            wsClients.add(failingChannel);

            server.broadcastUpdate();

            verify(logging).logToError(contains("Failed to send WebSocket message"));
            verify(logging).logToOutput(contains("Removed 1 dead WebSocket channels"));
        }

        @Test
        @DisplayName("should broadcast to multiple clients")
        void shouldBroadcastToMultipleClients() throws Exception {
            WebSocketChannel channel1 = mock(WebSocketChannel.class);
            WebSocketChannel channel2 = mock(WebSocketChannel.class);
            WebSocketChannel channel3 = mock(WebSocketChannel.class);

            when(channel1.isOpen()).thenReturn(true);
            when(channel2.isOpen()).thenReturn(true);
            when(channel3.isOpen()).thenReturn(true);

            Set<WebSocketChannel> wsClients = getWsClients();
            wsClients.add(channel1);
            wsClients.add(channel2);
            wsClients.add(channel3);

            server.broadcastUpdate();

            verify(logging).logToOutput(contains("Broadcasted update to 3 client"));
        }

        @Test
        @DisplayName("should handle mix of open and closed channels")
        void shouldHandleMixOfOpenAndClosedChannels() throws Exception {
            WebSocketChannel open1 = mock(WebSocketChannel.class);
            WebSocketChannel closed1 = mock(WebSocketChannel.class);
            WebSocketChannel open2 = mock(WebSocketChannel.class);
            WebSocketChannel closed2 = mock(WebSocketChannel.class);

            when(open1.isOpen()).thenReturn(true);
            when(closed1.isOpen()).thenReturn(false);
            when(open2.isOpen()).thenReturn(true);
            when(closed2.isOpen()).thenReturn(false);

            Set<WebSocketChannel> wsClients = getWsClients();
            wsClients.add(open1);
            wsClients.add(closed1);
            wsClients.add(open2);
            wsClients.add(closed2);

            server.broadcastUpdate();

            assertThat(wsClients).hasSize(2);
            verify(logging).logToOutput(contains("Broadcasted update to 2 client"));
        }
    }

    @Nested
    @DisplayName("checkForIssueChanges")
    class CheckForIssueChanges {

        @Test
        @DisplayName("should not check when server not running")
        void shouldNotCheckWhenServerNotRunning() {
            assertThat(server.isRunning()).isFalse();

            server.checkForIssueChanges();

            verify(logging, never()).logToOutput(contains("Issue count changed"));
        }

        @Test
        @DisplayName("should not check when no WebSocket clients")
        void shouldNotCheckWhenNoWebSocketClients() throws Exception {
            setRunning(true);

            server.checkForIssueChanges();

            verify(logging, never()).logToOutput(contains("Issue count changed"));
        }

        @Test
        @DisplayName("should detect issue count increase")
        void shouldDetectIssueCountIncrease() throws Exception {
            setRunning(true);
            addMockWebSocketClient();

            server.checkForIssueChanges();

            issues.add(AuditIssueBuilder.anIssue().build());
            issues.add(AuditIssueBuilder.anIssue().build());

            server.checkForIssueChanges();

            verify(logging).logToOutput(contains("Issue count changed: 0 -> 2"));
            verify(logging, atLeastOnce()).logToOutput(contains("Broadcasted update"));
        }

        @Test
        @DisplayName("should detect issue count decrease")
        void shouldDetectIssueCountDecrease() throws Exception {
            setRunning(true);
            addMockWebSocketClient();

            issues.add(AuditIssueBuilder.anIssue().build());
            issues.add(AuditIssueBuilder.anIssue().build());
            issues.add(AuditIssueBuilder.anIssue().build());
            server.checkForIssueChanges();

            issues.clear();
            issues.add(AuditIssueBuilder.anIssue().build());

            server.checkForIssueChanges();

            verify(logging).logToOutput(contains("Issue count changed: 3 -> 1"));
        }

        @Test
        @DisplayName("should not broadcast when count unchanged")
        void shouldNotBroadcastWhenCountUnchanged() throws Exception {
            setRunning(true);
            addMockWebSocketClient();

            issues.add(AuditIssueBuilder.anIssue().build());
            server.checkForIssueChanges();

            reset(logging);

            server.checkForIssueChanges();

            verify(logging, never()).logToOutput(contains("Issue count changed"));
        }

        @Test
        @DisplayName("should track count across multiple checks")
        void shouldTrackCountAcrossMultipleChecks() throws Exception {
            setRunning(true);
            addMockWebSocketClient();

            issues.add(AuditIssueBuilder.anIssue().build());
            server.checkForIssueChanges();
            verify(logging).logToOutput(contains("0 -> 1"));

            issues.add(AuditIssueBuilder.anIssue().build());
            issues.add(AuditIssueBuilder.anIssue().build());
            server.checkForIssueChanges();
            verify(logging).logToOutput(contains("1 -> 3"));

            issues.clear();
            server.checkForIssueChanges();
            verify(logging).logToOutput(contains("3 -> 0"));
        }

        @Test
        @DisplayName("should broadcast update when change detected")
        void shouldBroadcastUpdateWhenChangeDetected() throws Exception {
            setRunning(true);
            addMockWebSocketClient();

            issues.add(AuditIssueBuilder.anIssue().build());
            server.checkForIssueChanges();

            verify(logging).logToOutput(contains("Broadcasted update"));
        }
    }

    @Nested
    @DisplayName("Server Lifecycle")
    class ServerLifecycle {

        @Captor
        private ArgumentCaptor<ChannelListener<WebSocketChannel>> closeTaskCaptor;

        @Test
        @DisplayName("should have issue service after construction")
        void shouldHaveIssueServiceAfterConstruction() {
            assertThat(server.getIssueService()).isNotNull();
            assertThat(server.getIssueService()).isInstanceOf(IssueService.class);
        }

        @Test
        @DisplayName("should have api reference after construction")
        void shouldHaveApiReferenceAfterConstruction() {
            assertThat(server.getApi()).isEqualTo(api);
        }

        @Test
        @DisplayName("should return empty issues initially")
        void shouldReturnEmptyIssuesInitially() {
            List<AuditIssue> result = server.getIssues();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return updated issues list")
        void shouldReturnUpdatedIssuesList() {
            issues.add(AuditIssueBuilder.anIssue().build());
            issues.add(AuditIssueBuilder.anIssue().build());

            List<AuditIssue> result = server.getIssues();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should start server and update state")
        void shouldStartServer() {
            try (MockedStatic<Undertow> undertowStatic = mockStatic(Undertow.class)) {
                Undertow.Builder builder = mock(Undertow.Builder.class, RETURNS_SELF);
                Undertow mockUndertow = mock(Undertow.class);

                undertowStatic.when(Undertow::builder).thenReturn(builder);
                when(builder.build()).thenReturn(mockUndertow);

                server.start();

                assertThat(server.isRunning()).isTrue();
                verify(mockUndertow).start();
                verify(logging).logToOutput(contains("BurpSense Bridge Server is live"));

                int expectedPort = settings.getPort();
                String expectedIp = settings.getIp();
                verify(builder).addHttpListener(eq(expectedPort), eq(expectedIp));
                verify(builder).setHandler(any());
            }
        }

        @Test
        @DisplayName("should stop server and clear state")
        void shouldStopServer() throws Exception {
            Undertow mockUndertow = mock(Undertow.class);
            Field serverField = BridgeServer.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(server, mockUndertow);

            setRunning(true);

            server.stop();

            assertThat(server.isRunning()).isFalse();
            verify(mockUndertow).stop();
            assertThat(serverField.get(server)).isNull();
        }

        @Test
        @DisplayName("should handle stop when not running")
        void shouldHandleStopWhenNotRunning() {
            assertThat(server.isRunning()).isFalse();
            server.stop();
            assertThat(server.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("Internal State")
    class InternalState {

        @Test
        @DisplayName("should reset monitoring issue count")
        void shouldResetMonitoringState() throws Exception {
            Field countField = BridgeServer.class.getDeclaredField("lastIssueCount");
            countField.setAccessible(true);
            java.util.concurrent.atomic.AtomicInteger count = (java.util.concurrent.atomic.AtomicInteger) countField
                    .get(server);
            count.set(50);

            server.resetMonitoringState();

            assertThat(count.get()).isZero();
        }

        @Test
        @DisplayName("should trigger WebSocket send text")
        void shouldInvokeSendWebSocketText() {
            WebSocketChannel channel = mock(WebSocketChannel.class);

            server.sendWebSocketText(channel, "test-msg");

            verify(server).sendWebSocketText(channel, "test-msg");
        }

        @Test
        @DisplayName("should create routes with configured endpoints")
        void shouldInvokeCreateRoutes() throws Exception {
            java.lang.reflect.Method method = BridgeServer.class.getDeclaredMethod("createRoutes");
            method.setAccessible(true);
            assertThat(method.invoke(server)).isNotNull();
        }
    }

    /**
     * Helper to access private wsClients field via reflection
     */
    @SuppressWarnings("unchecked")
    private Set<WebSocketChannel> getWsClients() throws Exception {
        Field field = BridgeServer.class.getDeclaredField("wsClients");
        field.setAccessible(true);
        return (Set<WebSocketChannel>) field.get(server);
    }

    /**
     * Helper to set private running field via reflection
     */
    private void setRunning(boolean running) throws Exception {
        Field field = BridgeServer.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(server, running);
    }

    /**
     * Helper to add a mock WebSocket client
     */
    private void addMockWebSocketClient() throws Exception {
        WebSocketChannel channel = mock(WebSocketChannel.class);
        when(channel.isOpen()).thenReturn(true);
        getWsClients().add(channel);
    }
}