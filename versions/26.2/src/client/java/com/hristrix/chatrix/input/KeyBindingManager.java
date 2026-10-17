package com.hristrix.chatrix.input;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.interfaces.IChatConnectionManager;
import com.hristrix.chatrix.utils.ScreenNavigationCompat;
import com.hristrix.chatrix.ui.screen.MessageHistoryScreen;
import com.hristrix.chatrix.ui.screen.ModConfigScreen;
import com.hristrix.chatrix.messages.MessageSpawner;
import com.hristrix.chatrix.utils.Logger;

public class KeyBindingManager {
    private static final KeyMapping.Category KEY_CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chatrix", "key_category"));
    private final IChatConnectionManager chatConnectionManager;
    private final MessageSpawner messageSpawner;
    private KeyMapping openConfigScreenKeyBinding;
    private KeyMapping startAndStopMessagesKeyBinding;
    private KeyMapping openMessageHistoryKeyBinding;

    public KeyBindingManager(IChatConnectionManager chatConnectionManager, MessageSpawner messageSpawner) {
        this.chatConnectionManager = chatConnectionManager;
        this.messageSpawner = messageSpawner;
    }

    public void registerKeyBindings() {
        openConfigScreenKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.chatrix.openconfig",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            KEY_CATEGORY
        ));

        startAndStopMessagesKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.chatrix.startandstop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            KEY_CATEGORY
        ));

        openMessageHistoryKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.chatrix.openhistory",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigScreenKeyBinding.consumeClick()) {
                handleOpenConfigScreen();
            }
            while (startAndStopMessagesKeyBinding.consumeClick()) {
                handleChatToggle();
            }
            while (openMessageHistoryKeyBinding.consumeClick()) {
                handleOpenMessageHistory();
            }
        });
    }

    private void handleOpenConfigScreen() {
        try {
            ScreenNavigationCompat.open(net.minecraft.client.Minecraft.getInstance(), new ModConfigScreen());
        } catch (Exception e) {
            Logger.error("Failed to open settings screen", e);
            Logger.sendErrorToPlayer("Failed to open settings screen");
        }
    }

    private void handleOpenMessageHistory() {
        try {
            MessageSpawner spawner = this.messageSpawner != null
                ? this.messageSpawner
                : ChatrixClient.getStaticMessageSpawner();
            if (spawner == null) {
                return;
            }
            var client = net.minecraft.client.Minecraft.getInstance();
            if (ScreenNavigationCompat.current(client) instanceof MessageHistoryScreen) {
                return;
            }
            var lifecycle = spawner.getLifecycleManager();
            if (client.font != null) {
                lifecycle.warmHistoryLayouts(client.font);
            }
            ScreenNavigationCompat.open(client, new MessageHistoryScreen(
                ScreenNavigationCompat.current(client),
                lifecycle
            ));
        } catch (Exception e) {
            Logger.error("Failed to open message history", e);
            Logger.sendErrorToPlayer("Failed to open message history");
        }
    }

    private void handleChatToggle() {
        try {
            MessageSpawner spawner = this.messageSpawner != null
                ? this.messageSpawner
                : ChatrixClient.getStaticMessageSpawner();
            if (chatConnectionManager.isParserEnabled()) {
                chatConnectionManager.disconnect();
            } else if (spawner != null) {
                chatConnectionManager.connect(spawner);
            }
        } catch (Exception e) {
            ChatrixClient.LOGGER.error("Chat connection error: ", e);
        }
    }

    public KeyMapping getOpenConfigScreenKeyBinding() {
        return openConfigScreenKeyBinding;
    }
}
