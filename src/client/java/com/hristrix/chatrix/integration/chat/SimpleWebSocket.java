package com.hristrix.chatrix.integration.chat;

import com.hristrix.chatrix.ChatrixClient;

public final class SimpleWebSocket {
    private final String url;
    private final MessageHandler messageHandler;
    private final Runnable onOpen;
    private volatile java.net.http.WebSocket webSocket;
    private volatile boolean running;
    private volatile Throwable failure;
    private java.util.concurrent.CompletableFuture<?> pendingSend = java.util.concurrent.CompletableFuture.completedFuture(null);

    public interface MessageHandler {
        void onMessage(String message);
    }

    public SimpleWebSocket(String url, Runnable onOpen, MessageHandler messageHandler) {
        this.url = url;
        this.onOpen = onOpen;
        this.messageHandler = messageHandler;
    }

    public void connect() {
        running = true;
        failure = null;
        java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15)).build()
            .newWebSocketBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .buildAsync(java.net.URI.create(url), new java.net.http.WebSocket.Listener() {
                private final StringBuilder buffer = new StringBuilder();

                @Override
                public void onOpen(java.net.http.WebSocket ws) {
                    if (!running) {
                        ws.abort();
                        return;
                    }
                    webSocket = ws;
                    if (onOpen != null) {
                        onOpen.run();
                    }
                    ws.request(1);
                }

                @Override
                public java.util.concurrent.CompletionStage<?> onText(
                    java.net.http.WebSocket ws,
                    CharSequence data,
                    boolean last
                ) {
                    buffer.append(data);
                    if (last) {
                        String message = buffer.toString();
                        buffer.setLength(0);
                        try {
                            messageHandler.onMessage(message);
                        } catch (Exception e) {
                            ChatrixClient.LOGGER.warn("WebSocket message handler failed", e);
                        }
                    }
                    ws.request(1);
                    return null;
                }

                @Override
                public java.util.concurrent.CompletionStage<?> onClose(
                    java.net.http.WebSocket ws,
                    int statusCode,
                    String reason
                ) {
                    webSocket = null;
                    running = false;
                    failure = new java.io.IOException("WebSocket closed (" + statusCode + "): " + reason);
                    return null;
                }

                @Override
                public void onError(java.net.http.WebSocket ws, Throwable error) {
                    failure = error;
                    running = false;
                    webSocket = null;
                    ChatrixClient.LOGGER.warn("WebSocket error: {}", error.getMessage());
                }
            }).whenComplete((ws, error) -> {
                if (error != null) {
                    failure = error.getCause() != null ? error.getCause() : error;
                    running = false;
                }
            });
    }

    public synchronized void send(String text) {
        java.net.http.WebSocket ws = webSocket;
        if (ws != null) {
            // Java's WebSocket requires text sends to complete before another starts.
            pendingSend = pendingSend.thenCompose(ignored -> ws.sendText(text, true))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        failure = error;
                        running = false;
                        ws.abort();
                    }
                });
        }
    }

    public void disconnect() {
        running = false;
        java.net.http.WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isConnected() {
        return running && webSocket != null;
    }

    public Throwable getFailure() {
        return failure;
    }
}
