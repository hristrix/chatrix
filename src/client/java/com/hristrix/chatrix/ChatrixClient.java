package com.hristrix.chatrix;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.LoggerFactory;
import com.hristrix.chatrix.filtering.BanwordManager;
import com.hristrix.chatrix.filtering.BlockedUsernameManager;
import com.hristrix.chatrix.filtering.FilteringManager;
import com.hristrix.chatrix.messages.MessageSpawner;
import com.hristrix.chatrix.messages.MessageSystemFactory;
import com.hristrix.chatrix.messages.TwitchEmoteTextureCache;
import com.hristrix.chatrix.interfaces.IConfigManager;
import com.hristrix.chatrix.interfaces.IChatConnectionManager;
import com.hristrix.chatrix.interfaces.IBanwordManager;
import com.hristrix.chatrix.config.ConfigManager;
import com.hristrix.chatrix.integration.chat.ChatConnectionManager;
import com.hristrix.chatrix.commands.CommandManager;
import com.hristrix.chatrix.input.KeyBindingManager;
import com.hristrix.chatrix.utils.Logger;

/**
 * Main client entrypoint for Chatrix.
 */
public class ChatrixClient implements ClientModInitializer {
    public static final String MOD_ID = "chatrix";
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ChatrixClient instance;
    private IConfigManager configManager;
    private IChatConnectionManager chatConnectionManager;
    private IBanwordManager banwordManager;
    private BlockedUsernameManager blockedUsernameManager;
    private FilteringManager filteringManager;
    private MessageSpawner messageSpawner;
    private CommandManager commandManager;
    private KeyBindingManager keyBindingManager;

    @Override
    public void onInitializeClient() {
        instance = this;
        try {
            initializeMod();
            Logger.info("Chatrix initialized");
        } catch (Exception e) {
            Logger.error("Failed to initialize mod", e);
        }
    }

    private void initializeMod() {
        // Initialize managers.
        configManager = ConfigManager.getInstance();
        banwordManager = BanwordManager.getInstance();
        blockedUsernameManager = BlockedUsernameManager.getInstance();
        filteringManager = FilteringManager.getInstance();
        chatConnectionManager = ChatConnectionManager.getInstance(configManager);
        
        // Initialize the message system.
        messageSpawner = MessageSystemFactory.createMessageSystem();
        
        // Initialize command and key binding managers.
        commandManager = new CommandManager(chatConnectionManager, banwordManager, blockedUsernameManager, messageSpawner);
        keyBindingManager = new KeyBindingManager(chatConnectionManager, messageSpawner);
        
        // Register commands and key bindings.
        commandManager.registerCommands();
        keyBindingManager.registerKeyBindings();
        
        // Register world event handlers.
        WorldEventHandler.register(messageSpawner);
        TwitchEmoteTextureCache.registerClientTick();

        LOGGER.info("All mod components initialized");
    }

    public static ChatrixClient getInstance() {
        return instance;
    }

    public IConfigManager getConfigManager() {
        return configManager;
    }

    public IChatConnectionManager getChatConnectionManager() {
        return chatConnectionManager;
    }

    /** @deprecated Use {@link #getChatConnectionManager()} */
    @Deprecated
    public IChatConnectionManager getTwitchManager() {
        return chatConnectionManager;
    }

    public IBanwordManager getBanwordManager() {
        return banwordManager;
    }

    public BlockedUsernameManager getBlockedUsernameManager() {
        return blockedUsernameManager;
    }

    public FilteringManager getFilteringManager() {return filteringManager;}

    public MessageSpawner getMessageSpawner() {
        return messageSpawner;
    }

    public static MessageSpawner getStaticMessageSpawner() {
        return instance != null ? instance.messageSpawner : null;
    }
}