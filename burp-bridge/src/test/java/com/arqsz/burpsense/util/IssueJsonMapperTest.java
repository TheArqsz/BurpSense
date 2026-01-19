package com.arqsz.burpsense.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.testutil.AuditIssueBuilder;
import com.google.gson.JsonObject;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Marker;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

@DisplayName("IssueJsonMapper")
class IssueJsonMapperTest {

    @Nested
    @DisplayName("toJson")
    class ToJson {

        @Test
        @DisplayName("should convert basic issue to JSON")
        void shouldConvertBasicIssueToJson() {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("SQL Injection")
                    .withBaseUrl("https://example.com")
                    .withHost("example.com")
                    .withPort(443)
                    .withSecure(true)
                    .withSeverity(AuditIssueSeverity.HIGH)
                    .withConfidence(AuditIssueConfidence.CERTAIN)
                    .withDetail("SQL injection found")
                    .withRemediation("Use prepared statements")
                    .build();

            String issueId = "abc123";

            JsonObject json = IssueJsonMapper.toJson(issue, issueId);

            assertThat(json.get("id").getAsString()).isEqualTo("abc123");
            assertThat(json.get("name").getAsString()).isEqualTo("SQL Injection");
            assertThat(json.get("severity").getAsString()).isEqualTo("HIGH");
            assertThat(json.get("confidence").getAsString()).isEqualTo("CERTAIN");
            assertThat(json.get("baseUrl").getAsString()).isEqualTo("https://example.com");
            assertThat(json.get("detail").getAsString()).isEqualTo("SQL injection found");
            assertThat(json.get("remediation").getAsString()).isEqualTo("Use prepared statements");
        }

        @Test
        @DisplayName("should include service information")
        void shouldIncludeServiceInformation() {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withHost("api.example.com")
                    .withPort(8443)
                    .withSecure(true)
                    .build();

            JsonObject json = IssueJsonMapper.toJson(issue, "id123");

            assertThat(json.has("service")).isTrue();
            JsonObject service = json.getAsJsonObject("service");
            assertThat(service.get("host").getAsString()).isEqualTo("api.example.com");
            assertThat(service.get("port").getAsInt()).isEqualTo(8443);
            assertThat(service.get("protocol").getAsString()).isEqualTo("https");
        }

        @Test
        @DisplayName("should handle HTTP (non-secure) protocol")
        void shouldHandleHttpProtocol() {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withHost("example.com")
                    .withPort(80)
                    .withSecure(false)
                    .build();

            JsonObject json = IssueJsonMapper.toJson(issue, "id123");

            JsonObject service = json.getAsJsonObject("service");
            assertThat(service.get("protocol").getAsString()).isEqualTo("http");
        }

        @Test
        @DisplayName("should include background when definition exists")
        void shouldIncludeBackgroundWhenDefinitionExists() {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withName("XSS")
                    .withBackground("Cross-site scripting background info")
                    .build();

            JsonObject json = IssueJsonMapper.toJson(issue, "id123");

            assertThat(json.has("background")).isTrue();
            assertThat(json.get("background").getAsString())
                    .isEqualTo("Cross-site scripting background info");
        }

        @Test
        @DisplayName("should handle null remediation")
        void shouldHandleNullRemediation() {
            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withRemediation(null)
                    .build();

            JsonObject json = IssueJsonMapper.toJson(issue, "id123");

            assertThat(json.has("remediation")).isTrue();
            assertThat(json.get("remediation").getAsString()).isEmpty();
        }

        @Test
        @DisplayName("should handle issue with request/response")
        void shouldHandleIssueWithRequestResponse() {
            HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);
            ByteArray requestBytes = mock(ByteArray.class);
            ByteArray responseBytes = mock(ByteArray.class);

            when(request.toByteArray()).thenReturn(requestBytes);
            when(response.toByteArray()).thenReturn(responseBytes);
            when(requestBytes.getBytes()).thenReturn("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes());
            when(responseBytes.getBytes()).thenReturn("HTTP/1.1 200 OK\r\n\r\n".getBytes());
            when(requestResponse.request()).thenReturn(request);
            when(requestResponse.response()).thenReturn(response);
            when(requestResponse.responseMarkers()).thenReturn(List.of());

            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withRequestResponse(requestResponse)
                    .build();

            JsonObject json = IssueJsonMapper.toJson(issue, "id123");

            assertThat(json.has("request")).isTrue();
            assertThat(json.has("response")).isTrue();

            String requestB64 = json.get("request").getAsString();
            String responseB64 = json.get("response").getAsString();

            assertThat(new String(Base64.getDecoder().decode(requestB64)))
                    .contains("GET / HTTP/1.1");
            assertThat(new String(Base64.getDecoder().decode(responseB64)))
                    .contains("HTTP/1.1 200 OK");
        }

        @Test
        @DisplayName("should include response markers")
        void shouldIncludeResponseMarkers() {
            Marker marker1 = mock(Marker.class);
            Marker marker2 = mock(Marker.class);
            Range range1 = mock(Range.class);
            Range range2 = mock(Range.class);

            when(marker1.range()).thenReturn(range1);
            when(marker2.range()).thenReturn(range2);
            when(range1.startIndexInclusive()).thenReturn(10);
            when(range1.endIndexExclusive()).thenReturn(20);
            when(range2.startIndexInclusive()).thenReturn(30);
            when(range2.endIndexExclusive()).thenReturn(40);

            HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
            when(requestResponse.responseMarkers()).thenReturn(List.of(marker1, marker2));
            when(requestResponse.request()).thenReturn(null);
            when(requestResponse.response()).thenReturn(null);

            AuditIssue issue = AuditIssueBuilder.anIssue()
                    .withRequestResponse(requestResponse)
                    .build();

            JsonObject json = IssueJsonMapper.toJson(issue, "id123");

            assertThat(json.has("responseMarkers")).isTrue();
            assertThat(json.getAsJsonArray("responseMarkers")).hasSize(2);

            JsonObject marker1Json = json.getAsJsonArray("responseMarkers").get(0).getAsJsonObject();
            assertThat(marker1Json.get("start").getAsInt()).isEqualTo(10);
            assertThat(marker1Json.get("end").getAsInt()).isEqualTo(20);

            JsonObject marker2Json = json.getAsJsonArray("responseMarkers").get(1).getAsJsonObject();
            assertThat(marker2Json.get("start").getAsInt()).isEqualTo(30);
            assertThat(marker2Json.get("end").getAsInt()).isEqualTo(40);
        }

        @Test
        @DisplayName("should handle issue without request/response")
        void shouldHandleIssueWithoutRequestResponse() {
            AuditIssue issue = AuditIssueBuilder.anIssue().build();

            JsonObject json = IssueJsonMapper.toJson(issue, "id123");

            assertThat(json.has("request")).isFalse();
            assertThat(json.has("response")).isFalse();
        }
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("should not be instantiable")
        void shouldNotBeInstantiable() {
            assertThatThrownBy(() -> {
                var constructor = IssueJsonMapper.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructor.newInstance();
            })
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Utility class should not be instantiated");
        }
    }
}