package com.arqsz.burpsense.api.middleware;

import java.util.Optional;

import com.arqsz.burpsense.config.ApiKey;
import com.arqsz.burpsense.constants.SecurityConstants;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.service.AuthenticationService;

import burp.api.montoya.MontoyaApi;
import io.undertow.server.HttpHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * Middleware for API authentication using Bearer tokens
 */
public class AuthenticationMiddleware {

    private final AuthenticationService authenticationService;
    private final MontoyaApi api;
    private final RateLimiter rateLimiter;

    public AuthenticationMiddleware(AuthenticationService authenticationService, MontoyaApi api) {
        this.authenticationService = authenticationService;
        this.api = api;
        this.rateLimiter = new RateLimiter(SecurityConstants.MAX_REQUESTS_PER_MINUTE, SecurityConstants.WINDOW_SECONDS);
    }

    /**
     * Wraps a handler with authentication and rate limiting
     * 
     * @param next The next handler in the chain
     * @return A handler with authentication and rate limiting
     */
    public HttpHandler wrap(HttpHandler next) {
        return exchange -> {
            String clientIp = exchange.getSourceAddress().getAddress().getHostAddress();

            if (!rateLimiter.allowRequest(clientIp)) {
                api.logging().logToOutput(
                        "Rate limit exceeded for IP: " + clientIp);

                exchange.setStatusCode(StatusCodes.TOO_MANY_REQUESTS);
                exchange.getResponseHeaders().put(
                        Headers.RETRY_AFTER,
                        String.valueOf(rateLimiter.getResetTime(clientIp)));
                exchange.getResponseSender().send(
                        "{\"error\": \"Too many requests. Please try again later.\"}");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);

            Optional<ApiKey> apiKey = authenticationService.validateBearerToken(authHeader);

            if (apiKey.isEmpty()) {
                api.logging().logToOutput(
                        "Failed authentication attempt from IP: " + clientIp);
                exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
                return;
            }

            exchange.getResponseHeaders().put(
                    ServerConstants.X_RATE_LIMIT_LIMIT,
                    String.valueOf(SecurityConstants.MAX_REQUESTS_PER_MINUTE));
            exchange.getResponseHeaders().put(
                    ServerConstants.X_RATE_LIMIT_REMAINING,
                    String.valueOf(rateLimiter.getRemainingRequests(clientIp)));

            authenticationService.recordKeyUsage(apiKey.get());

            next.handleRequest(exchange);
        };
    }
}