package com.arqsz.burpsense.api.handler;

import com.arqsz.burpsense.constants.ServerConstants;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Handler for the health check endpoint
 */
public class HealthHandler implements HttpHandler {

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ServerConstants.CONTENT_TYPE_JSON);
        exchange.getResponseSender().send(ServerConstants.HEALTH_RESPONSE_OK);
    }
}