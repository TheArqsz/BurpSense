package com.arqsz.burpsense.api;

import static io.undertow.Handlers.websocket;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.arqsz.burpsense.api.handler.HealthHandler;
import com.arqsz.burpsense.api.handler.IssuesHandler;
import com.arqsz.burpsense.api.handler.SingleIssueHandler;
import com.arqsz.burpsense.api.middleware.AuthenticationMiddleware;
import com.arqsz.burpsense.api.middleware.CorsMiddleware;
import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.AuthenticationService;
import com.arqsz.burpsense.service.IssueService;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

/**
 * HTTP server providing the bridge API
 */
public class BridgeServer {

    private final MontoyaApi api;
    private final BridgeSettings settings;
    private final IssueService issueService;
    private final AuthenticationService authenticationService;

    private Undertow server;
    private volatile boolean running = false;

    private final Set<WebSocketChannel> wsClients = ConcurrentHashMap.newKeySet();
    private AtomicInteger lastIssueCount = new AtomicInteger(0);

    public BridgeServer(MontoyaApi api, BridgeSettings settings) {
        this.api = api;
        this.settings = settings;
        this.issueService = new IssueService();
        this.authenticationService = new AuthenticationService(settings);
    }

    /**
     * Starts the bridge server
     */
    public void start() {
        HttpHandler routes = createRoutes();

        HttpHandler withAuth = new AuthenticationMiddleware(authenticationService, api).wrap(routes);
        HttpHandler withCors = new CorsMiddleware(settings).wrap(withAuth);

        server = Undertow.builder()
                .addHttpListener(settings.getPort(), settings.getIp())
                .setHandler(withCors)
                .setServerOption(UndertowOptions.MAX_HEADER_SIZE, ServerConstants.DEFAULT_MAX_HEADER_SIZE)
                .setServerOption(UndertowOptions.MAX_PARAMETERS, ServerConstants.DEFAULT_MAX_PARAMETERS)
                .setServerOption(UndertowOptions.MAX_HEADERS, ServerConstants.DEFAULT_MAX_HEADERS)
                .setServerOption(UndertowOptions.ENABLE_STATISTICS, false)
                .build();

        server.start();
        running = true;
        api.logging().logToOutput(
                String.format("BurpSense Bridge Server is live at %s:%d", settings.getIp(), settings.getPort()));
    }

    /**
     * Stops the bridge server
     */
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
            running = false;
        }
    }

    /**
     * Checks if the server is currently running
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets all issues from the site map (used by handlers)
     * 
     * @return List of audit issues
     */
    public List<AuditIssue> getIssues() {
        return api.siteMap().issues();
    }

    /**
     * Gets the issue service instance
     * 
     * @return The issue service
     */
    public IssueService getIssueService() {
        return issueService;
    }

    /**
     * Gets the Montoya API instance
     * 
     * @return The API instance
     */
    public MontoyaApi getApi() {
        return api;
    }

    public void broadcastUpdate() {
        String message = "refresh";

        Set<WebSocketChannel> failedChannels = new HashSet<>();

        wsClients.removeIf(channel -> !channel.isOpen());

        for (WebSocketChannel channel : wsClients) {
            try {
                sendWebSocketText(channel, message);
            } catch (Exception e) {
                api.logging().logToError("Failed to send WebSocket message: " + e.getMessage());
                failedChannels.add(channel);
            }
        }

        if (!wsClients.isEmpty()) {
            api.logging().logToOutput(
                    String.format("Broadcasted update to %d client(s)", wsClients.size()));
        }

        if (!failedChannels.isEmpty()) {
            wsClients.removeAll(failedChannels);
            api.logging().logToOutput(
                    "Removed " + failedChannels.size() + " dead WebSocket channels");
        }
    }

    protected void sendWebSocketText(WebSocketChannel channel, String message) {
        WebSockets.sendText(message, channel, new WebSocketCallback<Void>() {
            @Override
            public void complete(WebSocketChannel channel, Void context) {
            }

            @Override
            public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
                api.logging().logToError("WebSocket send failed, removing client: " + throwable.getMessage());
                wsClients.remove(channel);
            }
        });
    }

    /**
     * Checks if issue count changed and broadcasts update if needed
     * Called periodically by monitoring thread
     */
    public void checkForIssueChanges() {
        if (!running || wsClients.isEmpty()) {
            return;
        }

        List<AuditIssue> currentIssues = getIssues();
        int currentCount = currentIssues.size();
        int oldCount = lastIssueCount.getAndSet(currentCount);

        if (oldCount != currentCount) {
            api.logging().logToOutput(
                    String.format("Issue count changed: %d -> %d", oldCount, currentCount));
            broadcastUpdate();
        }
    }

    public void resetMonitoringState() {
        lastIssueCount.set(0);
    }

    /**
     * Creates the routing handler with all endpoints
     * 
     * @return Configured routing handler
     */
    private HttpHandler createRoutes() {
        return new RoutingHandler()
                .get(ServerConstants.ENDPOINT_HEALTH, new HealthHandler())
                .get(ServerConstants.ENDPOINT_ISSUES, new IssuesHandler(this))
                .post(ServerConstants.ENDPOINT_ISSUES, new IssuesHandler(this))
                .get(ServerConstants.ENDPOINT_ISSUE_BY_ID, new SingleIssueHandler(this))
                .get(ServerConstants.ENDPOINT_WS, websocket((exchange, channel) -> {
                    wsClients.add(channel);

                    api.logging().logToOutput(
                            String.format("WebSocket client connected (total: %d)", wsClients.size()));

                    channel.addCloseTask(c -> {
                        wsClients.remove(c);
                        api.logging().logToOutput(
                                String.format("WebSocket client disconnected (total: %d)", wsClients.size()));
                    });

                    channel.resumeReceives();
                }));
    }
}