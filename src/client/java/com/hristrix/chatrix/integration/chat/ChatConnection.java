package com.hristrix.chatrix.integration.chat;

public interface ChatConnection {
    ChatPlatform getPlatform();

    void connect(ChatMessagePipeline pipeline);

    void disconnect();

    boolean isConnected();
}
