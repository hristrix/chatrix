package com.hristrix.chatrix.integration.twitch;

import com.hristrix.chatrix.integration.chat.ChatConnectionManager;
import com.hristrix.chatrix.interfaces.IChatConnectionManager;
import com.hristrix.chatrix.interfaces.IConfigManager;
import com.hristrix.chatrix.interfaces.ITwitchManager;
import com.hristrix.chatrix.messages.MessageSpawner;

/**
 * @deprecated Use {@link ChatConnectionManager} directly.
 */
@Deprecated
@SuppressWarnings("dep-ann")
public class TwitchManager implements ITwitchManager {
    private static TwitchManager instance;
    private final ChatConnectionManager delegate;

    private TwitchManager(IConfigManager configManager) {
        this.delegate = ChatConnectionManager.getInstance(configManager);
    }

    public static TwitchManager getInstance(IConfigManager configManager) {
        if (instance == null) {
            instance = new TwitchManager(configManager);
        }
        return instance;
    }

    public static ChatConnectionManager getChatManager(IConfigManager configManager) {
        return ChatConnectionManager.getInstance(configManager);
    }

    @Override
    public void connect(MessageSpawner spawner) {
        delegate.connect(spawner);
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public void onChannelNameChanged(String newChannelName) {
        delegate.reconnectChangedPlatforms();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    public IChatConnectionManager getDelegate() {
        return delegate;
    }
}
