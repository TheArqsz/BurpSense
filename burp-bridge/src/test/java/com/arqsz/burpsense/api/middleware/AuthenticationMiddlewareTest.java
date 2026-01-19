package com.arqsz.burpsense.api.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.config.ApiKey;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.AuthenticationService;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

@DisplayName("AuthenticationMiddleware")
class AuthenticationMiddlewareTest {

    private AuthenticationMiddleware middleware;
    private AuthenticationService authService;
    private MontoyaApi api;
    private Logging logging;
    private HttpHandler nextHandler;

    @BeforeEach
    void setUp() {
        authService = mock(AuthenticationService.class);
        api = mock(MontoyaApi.class);
        logging = mock(Logging.class);
        when(api.logging()).thenReturn(logging);

        nextHandler = mock(HttpHandler.class);
        middleware = new AuthenticationMiddleware(authService, api);
    }

    @Nested
    @DisplayName("wrap")
    class Wrap {

        @Nested
        @DisplayName("Authentication")
        class Authentication {

            @Test
            @DisplayName("should allow valid token and proceed to next handler")
            void shouldAllowValidToken() throws Exception {
                String authHeader = "Bearer valid-token";
                ApiKey apiKey = ApiKey.create("test-key");
                HttpServerExchange exchange = mockExchange("127.0.0.1");
                exchange.getRequestHeaders().put(Headers.AUTHORIZATION, authHeader);

                when(authService.validateBearerToken(authHeader)).thenReturn(Optional.of(apiKey));

                middleware.wrap(nextHandler).handleRequest(exchange);

                verify(nextHandler).handleRequest(exchange);
                verify(authService).recordKeyUsage(apiKey);
                assertThat(exchange.getResponseHeaders().contains(ServerConstants.X_RATE_LIMIT_LIMIT))
                        .as("Response should include rate limit headers")
                        .isTrue();
            }

            @Test
            @DisplayName("should return 401 when token is missing")
            void shouldReturn401WhenTokenMissing() throws Exception {
                HttpServerExchange exchange = mockExchange("127.0.0.1");
                when(authService.validateBearerToken(null)).thenReturn(Optional.empty());

                middleware.wrap(nextHandler).handleRequest(exchange);

                verify(exchange).setStatusCode(StatusCodes.UNAUTHORIZED);
                verify(nextHandler, never()).handleRequest(any());
            }
        }

        @Nested
        @DisplayName("Rate Limiting")
        class RateLimiting {

            @Test
            @DisplayName("should return 429 when rate limit is exceeded")
            void shouldReturn429WhenLimitExceeded() throws Exception {
                String clientIp = "10.0.0.1";
                HttpHandler wrapped = middleware.wrap(nextHandler);
                when(authService.validateBearerToken(any())).thenReturn(Optional.of(ApiKey.create("test")));

                for (int i = 0; i < 60; i++) {
                    wrapped.handleRequest(mockExchange(clientIp));
                }

                Sender mockSender = mock(Sender.class);
                HttpServerExchange limitExchange = mockExchange(clientIp);
                when(limitExchange.getResponseSender()).thenReturn(mockSender);

                wrapped.handleRequest(limitExchange);

                verify(limitExchange).setStatusCode(StatusCodes.TOO_MANY_REQUESTS);
                verify(mockSender).send(contains("Too many requests"));
                verify(nextHandler, never()).handleRequest(limitExchange);
            }
        }
    }

    /**
     * Helper to create a mocked HttpServerExchange with necessary networking stubs.
     */
    private HttpServerExchange mockExchange(String ip) {
        HttpServerExchange exchange = mock(HttpServerExchange.class);
        HeaderMap reqHeaders = new HeaderMap();
        HeaderMap respHeaders = new HeaderMap();

        when(exchange.getRequestHeaders()).thenReturn(reqHeaders);
        when(exchange.getResponseHeaders()).thenReturn(respHeaders);
        when(exchange.getSourceAddress()).thenReturn(new InetSocketAddress(ip, 12345));
        when(exchange.getRequestMethod()).thenReturn(io.undertow.util.Methods.GET);

        when(exchange.getResponseSender()).thenReturn(mock(Sender.class));

        return exchange;
    }
}