package com.arqsz.burpsense.api.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.ServerConstants;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

@DisplayName("CorsMiddleware")
class CorsMiddlewareTest {

    private CorsMiddleware middleware;
    private BridgeSettings settings;
    private HttpHandler nextHandler;
    private HttpServerExchange exchange;
    private HeaderMap requestHeaders;
    private HeaderMap responseHeaders;

    @BeforeEach
    void setUp() {
        settings = mock(BridgeSettings.class);
        middleware = new CorsMiddleware(settings);
        nextHandler = mock(HttpHandler.class);

        exchange = mock(HttpServerExchange.class);
        requestHeaders = new HeaderMap();
        responseHeaders = new HeaderMap();

        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        when(exchange.getRequestMethod()).thenReturn(Methods.GET);
    }

    @Nested
    @DisplayName("Preflight Requests (OPTIONS)")
    class PreflightRequests {

        @BeforeEach
        void setupOptions() {
            when(exchange.getRequestMethod()).thenReturn(Methods.OPTIONS);
        }

        @Test
        @DisplayName("should handle allowed origin preflight")
        void shouldHandleAllowedOriginPreflight() throws Exception {
            String origin = "http://localhost:3000";
            requestHeaders.put(new HttpString("Origin"), origin);
            when(settings.getAllowedOrigins()).thenReturn(List.of(origin));

            middleware.wrap(nextHandler).handleRequest(exchange);

            verify(exchange).setStatusCode(StatusCodes.NO_CONTENT);
            assertThat(responseHeaders.getFirst(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(origin);
            assertThat(responseHeaders.getFirst(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS))
                    .isEqualTo("true");
            verify(nextHandler, never()).handleRequest(any());
        }

        @Test
        @DisplayName("should handle wildcard origin preflight")
        void shouldHandleWildcardOriginPreflight() throws Exception {
            requestHeaders.put(new HttpString("Origin"), "https://any-site.com");
            when(settings.getAllowedOrigins()).thenReturn(List.of("*"));

            middleware.wrap(nextHandler).handleRequest(exchange);

            assertThat(responseHeaders.getFirst(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN))
                    .isEqualTo("https://any-site.com");
            verify(exchange).setStatusCode(StatusCodes.NO_CONTENT);
        }

        @Test
        @DisplayName("should set mandatory CORS headers even for disallowed origins in preflight")
        void shouldSetMandatoryHeaders() throws Exception {
            middleware.wrap(nextHandler).handleRequest(exchange);

            assertThat(responseHeaders.contains(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_METHODS)).isTrue();
            assertThat(responseHeaders.contains(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_HEADERS)).isTrue();
            assertThat(responseHeaders.getFirst(ServerConstants.HEADER_ACCESS_CONTROL_MAX_AGE))
                    .isEqualTo(ServerConstants.CORS_MAX_AGE);
        }
    }

    @Nested
    @DisplayName("Actual Requests (GET/POST)")
    class ActualRequests {

        @Test
        @DisplayName("should allow request from authorized origin")
        void shouldAllowAuthorizedOrigin() throws Exception {
            String origin = "http://trusted.com";
            requestHeaders.put(new HttpString("Origin"), origin);
            when(settings.getAllowedOrigins()).thenReturn(List.of(origin));

            middleware.wrap(nextHandler).handleRequest(exchange);

            assertThat(responseHeaders.getFirst(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(origin);
            verify(nextHandler).handleRequest(exchange);
        }

        @Test
        @DisplayName("should block request from unauthorized origin")
        void shouldBlockUnauthorizedOrigin() throws Exception {
            requestHeaders.put(new HttpString("Origin"), "http://hacker.com");
            when(settings.getAllowedOrigins()).thenReturn(List.of("http://trusted.com"));

            middleware.wrap(nextHandler).handleRequest(exchange);

            verify(exchange).setStatusCode(StatusCodes.FORBIDDEN);
            verify(nextHandler, never()).handleRequest(any());
        }

        @Test
        @DisplayName("should allow requests with no origin header")
        void shouldAllowNoOriginHeader() throws Exception {
            middleware.wrap(nextHandler).handleRequest(exchange);

            verify(nextHandler).handleRequest(exchange);
            assertThat(responseHeaders.contains(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN)).isFalse();
        }

        @Test
        @DisplayName("should allow any origin when settings use wildcard")
        void shouldAllowWildcardActualRequest() throws Exception {
            requestHeaders.put(new HttpString("Origin"), "http://some-origin.com");
            when(settings.getAllowedOrigins()).thenReturn(List.of("*"));

            middleware.wrap(nextHandler).handleRequest(exchange);

            assertThat(responseHeaders.getFirst(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN))
                    .isEqualTo("http://some-origin.com");
            verify(nextHandler).handleRequest(exchange);
        }
    }
}