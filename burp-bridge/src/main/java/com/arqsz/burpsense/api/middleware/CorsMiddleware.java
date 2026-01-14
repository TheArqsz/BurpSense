package com.arqsz.burpsense.api.middleware;

import java.util.List;

import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.ServerConstants;

import io.undertow.server.HttpHandler;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * Middleware for handling CORS (Cross-Origin Resource Sharing)
 */
public class CorsMiddleware {

    private final BridgeSettings settings;

    public CorsMiddleware(BridgeSettings settings) {
        this.settings = settings;
    }

    /**
     * Wraps a handler with CORS support
     * 
     * @param next The next handler in the chain
     * @return A handler with CORS support
     */
    public HttpHandler wrap(HttpHandler next) {
        return exchange -> {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            List<String> allowedOrigins = settings.getAllowedOrigins();

            if (origin != null && allowedOrigins.contains(origin)) {
                exchange.getResponseHeaders().put(
                        new HttpString(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN),
                        origin);
                exchange.getResponseHeaders().put(
                        new HttpString(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS),
                        "true");
            } else if (origin != null) {
                exchange.setStatusCode(StatusCodes.FORBIDDEN);
                return;
            }

            exchange.getResponseHeaders().put(
                    new HttpString(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_METHODS),
                    ServerConstants.HTTP_METHODS_ALLOWED);
            exchange.getResponseHeaders().put(
                    new HttpString(ServerConstants.HEADER_ACCESS_CONTROL_ALLOW_HEADERS),
                    ServerConstants.HTTP_HEADERS_ALLOWED);
            exchange.getResponseHeaders().put(
                    new HttpString(ServerConstants.HEADER_ACCESS_CONTROL_MAX_AGE),
                    ServerConstants.CORS_MAX_AGE);

            if (exchange.getRequestMethod().equalToString(ServerConstants.HTTP_METHOD_OPTIONS)) {
                exchange.setStatusCode(StatusCodes.NO_CONTENT);
                return;
            }

            next.handleRequest(exchange);
        };
    }
}