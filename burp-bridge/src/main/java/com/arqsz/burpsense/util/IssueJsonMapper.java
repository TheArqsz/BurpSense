package com.arqsz.burpsense.util;

import java.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import burp.api.montoya.scanner.audit.issues.AuditIssue;

/**
 * Utility for converting Burp Suite AuditIssue objects to JSON format.
 */
public class IssueJsonMapper {

    /**
     * Converts an AuditIssue to JSON representation.
     * 
     * @param issue   The audit issue to convert
     * @param issueId The unique identifier for this issue
     * @return JsonObject containing all issue details
     */
    public static JsonObject toJson(AuditIssue issue, String issueId) {
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
            var requestResponse = issue.requestResponses().get(0);

            JsonArray markersArray = new JsonArray();
            requestResponse.responseMarkers().forEach(marker -> {
                JsonObject markerObj = new JsonObject();
                markerObj.addProperty("start", marker.range().startIndexInclusive());
                markerObj.addProperty("end", marker.range().endIndexExclusive());
                markersArray.add(markerObj);
            });
            json.add("responseMarkers", markersArray);

            if (requestResponse.request() != null) {
                json.addProperty("request",
                        Base64.getEncoder().encodeToString(
                                requestResponse.request().toByteArray().getBytes()));
            }

            if (requestResponse.response() != null) {
                json.addProperty("response",
                        Base64.getEncoder().encodeToString(
                                requestResponse.response().toByteArray().getBytes()));
            }
        }

        return json;
    }

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private IssueJsonMapper() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}