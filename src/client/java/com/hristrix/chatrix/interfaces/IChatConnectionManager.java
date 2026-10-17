package com.hristrix.chatrix.interfaces;

import com.hristrix.chatrix.integration.chat.ChatPlatform;
import com.hristrix.chatrix.messages.MessageSpawner;

/**
 * Manages streaming-platform chat connections.
 */
public interface IChatConnectionManager {
    void connect(MessageSpawner spawner);

    void disconnect();

    boolean isConnected();

    /** Whether the chat parser is running; enabled sources connect only while it runs. */
    boolean isParserEnabled();

    boolean isPlatformConnected(ChatPlatform platform);

    int getConnectedCount();

    void reconnectChangedPlatforms();
}
