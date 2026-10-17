package com.hristrix.chatrix.interfaces;

import com.hristrix.chatrix.messages.MessageSpawner;

/**
 * Interface for managing the Twitch connection.
 */
public interface ITwitchManager {
    /**
     * Connects to a Twitch channel.
     * @param spawner message spawner
     */
    void connect(MessageSpawner spawner);
    
    /**
     * Disconnects from the Twitch channel.
     */
    void disconnect();
    
    /**
     * Checks whether Twitch is connected.
     * @return true if connected
     */
    boolean isConnected();
    
    /**
     * Handles a channel name change.
     * @param newChannelName new channel name
     */
    void onChannelNameChanged(String newChannelName);
} 