package com.arqsz.burpsense.api.handler;

import java.util.List;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.IssueService;
import com.arqsz.burpsense.util.IssueJsonMapper;
import com.google.gson.JsonObject;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;

/**
 * Handler for fetching a single issue by ID
 * Endpoint: GET /issues/{id}
 */
public class SingleIssueHandler implements HttpHandler {

    private final BridgeServer server;
    private final IssueService issueService;

    public SingleIssueHandler(BridgeServer server) {
        this.server = server;
        this.issueService = server.getIssueService();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (!exchange.getRequestMethod().equalToString("GET")) {
            exchange.setStatusCode(405);
            exchange.getResponseSender().send("{\"error\": \"Method Not Allowed\"}");
            return;
        }

        PathTemplateMatch pathMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        String issueId = pathMatch != null ? pathMatch.getParameters().get("id") : null;

        if (issueId == null || issueId.isEmpty()) {
            exchange.setStatusCode(400);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ServerConstants.CONTENT_TYPE_JSON);
            exchange.getResponseSender().send("{\"error\": \"Issue ID required in path (/issues/{id})\"}");
            return;
        }

        List<AuditIssue> allIssues = server.getIssues();
        AuditIssue foundIssue = null;

        for (AuditIssue issue : allIssues) {
            String currentId = issueService.generateIssueId(issue);
            if (currentId.equals(issueId)) {
                foundIssue = issue;
                break;
            }
        }

        if (foundIssue == null) {
            exchange.setStatusCode(404);
            exchange.getResponseSender().send(
                    String.format("{\"error\": \"Issue not found\", \"id\": \"%s\"}", issueId));
            return;
        }

        JsonObject json = IssueJsonMapper.toJson(foundIssue, issueId);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ServerConstants.CONTENT_TYPE_JSON);
        exchange.getResponseSender().send(json.toString());
    }
}