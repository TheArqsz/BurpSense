package com.arqsz.burpsense.api.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arqsz.burpsense.constants.ServerConstants;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

@DisplayName("HealthHandler")
class HealthHandlerTest {

    private HealthHandler handler;
    private HttpServerExchange exchange;
    private TestResponseSender responseSender;
    private HeaderMap responseHeaders;

    @BeforeEach
    void setUp() {
        handler = new HealthHandler();

        exchange = mock(HttpServerExchange.class);
        ServerConnection connection = mock(ServerConnection.class);

        when(exchange.getConnection()).thenReturn(connection);

        responseHeaders = new HeaderMap();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        responseSender = new TestResponseSender();
        when(exchange.getResponseSender()).thenReturn(responseSender);
    }

    @Test
    @DisplayName("should return OK status with correct content type")
    void shouldReturnOkStatusWithCorrectContentType() throws Exception {
        handler.handleRequest(exchange);

        assertThat(responseHeaders.getFirst(Headers.CONTENT_TYPE))
                .isEqualTo(ServerConstants.CONTENT_TYPE_JSON);
        assertThat(responseSender.getSentData())
                .isEqualTo(ServerConstants.HEALTH_RESPONSE_OK);
    }

    @Test
    @DisplayName("should handle multiple requests")
    void shouldHandleMultipleRequests() throws Exception {
        TestResponseSender sender1 = new TestResponseSender();
        when(exchange.getResponseSender()).thenReturn(sender1);

        handler.handleRequest(exchange);

        assertThat(sender1.getSentData()).isEqualTo(ServerConstants.HEALTH_RESPONSE_OK);

        TestResponseSender sender2 = new TestResponseSender();
        when(exchange.getResponseSender()).thenReturn(sender2);

        handler.handleRequest(exchange);

        assertThat(sender2.getSentData()).isEqualTo(ServerConstants.HEALTH_RESPONSE_OK);
    }

    /**
     * Test implementation of HttpServerExchange.ResponseSender
     */
    private static class TestResponseSender implements Sender {
        private String sentData;

        @Override
        public void send(String data) {
            this.sentData = data;
        }

        @Override
        public void send(String data, Charset charset) {
            this.sentData = data;
        }

        @Override
        public void send(ByteBuffer buffer) {
        }

        @Override
        public void send(ByteBuffer[] buffers) {
        }

        @Override
        public void send(ByteBuffer buffer, IoCallback callback) {
        }

        @Override
        public void send(java.nio.ByteBuffer[] buffers, IoCallback callback) {
        }

        @Override
        public void send(String data, IoCallback callback) {
            this.sentData = data;
            if (callback != null) {
                callback.onComplete(null, null);
            }
        }

        @Override
        public void send(String data, Charset charset, IoCallback callback) {
            this.sentData = data;
            if (callback != null) {
                callback.onComplete(null, null);
            }
        }

        @Override
        public void transferFrom(FileChannel source, IoCallback callback) {
        }

        @Override
        public void close() {
        }

        @Override
        public void close(IoCallback callback) {
        }

        public String getSentData() {
            return sentData;
        }
    }
}