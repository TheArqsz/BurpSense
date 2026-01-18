package com.arqsz.burpsense.api.middleware;

import java.util.Optional;

import com.arqsz.burpsense.config.ApiKey;
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
    @SuppressWarnings("unused")
    private final MontoyaApi api;

    public AuthenticationMiddleware(AuthenticationService authenticationService, MontoyaApi api) {
        this.authenticationService = authenticationService;
        this.api = api;
    }

    /**
     * Wraps a handler with authentication
     * 
     * @param next The next handler in the chain
     * @return A handler with authentication
     */
    public HttpHandler wrap(HttpHandler next) {
        return exchange -> {
            String authHeader = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);

            Optional<ApiKey> apiKey = authenticationService.validateBearerToken(authHeader);

            if (apiKey.isEmpty()) {
                exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
                return;
            }

            authenticationService.recordKeyUsage(apiKey.get());

            next.handleRequest(exchange);
        };
    }
}