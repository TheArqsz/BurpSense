package com.arqsz.burpsense.api.handler;

import java.util.Base64;
import java.util.List;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.IssueService;
import com.google.gson.JsonArray;
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

        JsonObject json = mapIssueToJson(foundIssue, issueId);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ServerConstants.CONTENT_TYPE_JSON);
        exchange.getResponseSender().send(json.toString());
    }

    /**
     * Converts an AuditIssue to JSON format (same as IssuesHandler)
     */
    private JsonObject mapIssueToJson(AuditIssue issue, String issueId) {
        JsonObject json = new JsonObject();

        json.addProperty("id", issueId);
        json.addProperty("name", issue.name());
        json.addProperty("severity", issue.severity().name());
        json.addProperty("confidence", issue.confidence().name());
        json.addProperty("baseUrl", issue.baseUrl());
        json.addProperty("detail", issue.detail());
        json.addProperty("remediation", issue.remediation());

        if (issue.definition() != null) {
            json.addProperty("background", issue.definition().background());
        }

        JsonObject service = new JsonObject();
        service.addProperty("host", issue.httpService().host());
        service.addProperty("port", issue.httpService().port());
        service.addProperty("protocol", issue.httpService().secure() ? "https" : "http");
        json.add("service", service);

        if (!issue.requestResponses().isEmpty()) {
            var rr = issue.requestResponses().get(0);

            JsonArray markersArray = new JsonArray();
            rr.responseMarkers().forEach(m -> {
                JsonObject marker = new JsonObject();
                marker.addProperty("start", m.range().startIndexInclusive());
                marker.addProperty("end", m.range().endIndexExclusive());
                markersArray.add(marker);
            });
            json.add("responseMarkers", markersArray);

            if (rr.request() != null) {
                json.addProperty("request",
                        Base64.getEncoder().encodeToString(rr.request().toByteArray().getBytes()));
            }
            if (rr.response() != null) {
                json.addProperty("response",
                        Base64.getEncoder().encodeToString(rr.response().toByteArray().getBytes()));
            }
        }

        return json;
    }
}