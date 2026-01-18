package com.arqsz.burpsense.api.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.constants.IssueConstants;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.IssueService;
import com.arqsz.burpsense.util.IssueJsonMapper;
import com.arqsz.burpsense.util.RegexValidator;
import com.arqsz.burpsense.util.RegexValidator.RegexValidationException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Handler for the issues endpoint
 */
public class IssuesHandler implements HttpHandler {

    private final BridgeServer server;
    private final IssueService issueService;

    public IssuesHandler(BridgeServer server) {
        this.server = server;
        this.issueService = server.getIssueService();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.getRequestMethod().equalToString("GET")) {
            processAndSendIssues(exchange, new HashSet<>());
        } else if (exchange.getRequestMethod().equalToString("POST")) {
            exchange.getRequestReceiver().receiveFullString((ex, message) -> {
                Set<String> knownIds = new HashSet<>();
                try {
                    JsonObject body = JsonParser.parseString(message).getAsJsonObject();
                    if (body.has(ServerConstants.JSON_KEY_KNOWN_IDS)) {
                        body.getAsJsonArray(ServerConstants.JSON_KEY_KNOWN_IDS)
                                .forEach(id -> knownIds.add(id.getAsString()));
                    }
                } catch (Exception e) {
                    server.getApi().logging().logToError("Invalid JSON body in /issues POST");
                }
                processAndSendIssues(ex, knownIds);
            });
        } else {
            exchange.setStatusCode(405);
            exchange.getResponseSender().send("Method Not Allowed");
        }
    }

    public void processAndSendIssues(HttpServerExchange exchange, Set<String> knownIds) {
        String minSeverity = getQueryParam(exchange, IssueConstants.QUERY_PARAM_MIN_SEVERITY)
                .orElse(IssueConstants.DEFAULT_MIN_SEVERITY);
        String minConfidence = getQueryParam(exchange, IssueConstants.QUERY_PARAM_MIN_CONFIDENCE)
                .orElse(IssueConstants.DEFAULT_MIN_CONFIDENCE);
        boolean inScopeOnly = getQueryParam(exchange, IssueConstants.QUERY_PARAM_IN_SCOPE)
                .map(Boolean::parseBoolean)
                .orElse(false);
        String nameRegex = getQueryParam(exchange, IssueConstants.QUERY_PARAM_NAME_REGEX)
                .orElse(IssueConstants.DEFAULT_NAME_REGEX);

        Pattern pattern;
        try {
            pattern = compilePattern(nameRegex);
        } catch (RegexValidationException e) {
            exchange.setStatusCode(400);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ServerConstants.CONTENT_TYPE_JSON);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid regex pattern.");
            exchange.getResponseSender().send(error.toString());
            return;
        }

        JsonArray newIssues = new JsonArray();
        JsonArray removedIds = new JsonArray();
        Set<String> currentIssueIds = new HashSet<>();

        List<AuditIssue> issues = server.getIssues();

        for (AuditIssue issue : issues) {
            String issueId = issueService.generateIssueId(issue);
            if (shouldIncludeIssue(issue, minSeverity, minConfidence, inScopeOnly, pattern)) {
                currentIssueIds.add(issueId);

                if (!knownIds.contains(issueId)) {
                    newIssues.add(IssueJsonMapper.toJson(issue, issueId));
                }
            }
        }

        for (String knownId : knownIds) {
            if (!currentIssueIds.contains(knownId)) {
                removedIds.add(knownId);
            }
        }

        JsonObject response = new JsonObject();
        response.add("newIssues", newIssues);
        response.add("removedIds", removedIds);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ServerConstants.CONTENT_TYPE_JSON);
        exchange.getResponseSender().send(response.toString());
    }

    /**
     * Determines if an issue should be included based on filters
     */
    private boolean shouldIncludeIssue(
            AuditIssue issue,
            String minSeverity,
            String minConfidence,
            boolean inScopeOnly,
            Pattern namePattern) {

        if (inScopeOnly && !server.getApi().scope().isInScope(issue.baseUrl())) {
            return false;
        }

        if (!issueService.meetsMinimumSeverity(issue.severity(), minSeverity)) {
            return false;
        }

        if (!issueService.meetsMinimumConfidence(issue.confidence(), minConfidence)) {
            return false;
        }

        return RegexValidator.findWithTimeout(namePattern, issue.name());
    }

    /**
     * Compiles a regex pattern with ReDoS protection
     * 
     * @throws RegexValidationException if pattern is invalid
     */
    private Pattern compilePattern(String regex) throws RegexValidationException {
        try {
            return RegexValidator.compileSafe(regex, Pattern.CASE_INSENSITIVE);
        } catch (RegexValidationException e) {
            server.getApi().logging().logToError("Rejected nameRegex: " + regex + " - " + e.getMessage());
            throw e;
        }
    }

    /**
     * Gets a query parameter value
     */
    private Optional<String> getQueryParam(HttpServerExchange exchange, String key) {
        return Optional.ofNullable(exchange.getQueryParameters().get(key))
                .map(java.util.Deque::getFirst);
    }
}