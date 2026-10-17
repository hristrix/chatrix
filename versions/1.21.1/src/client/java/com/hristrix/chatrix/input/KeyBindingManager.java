package com.hristrix.chatrix.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.interfaces.IChatConnectionManager;
import com.hristrix.chatrix.ui.screen.MessageHistoryScreen;
import com.hristrix.chatrix.ui.screen.ModConfigScreen;
import com.hristrix.chatrix.messages.MessageSpawner;
import com.hristrix.chatrix.utils.Logger;

public class KeyBindingManager {
    private static final String KEY_CATEGORY = "key.categories.chatrix";
    private final IChatConnectionManager chatConnectionManager;
    private final MessageSpawner messageSpawner;
    private KeyBinding openConfigScreenKeyBinding;
    private KeyBinding startAndStopMessagesKeyBinding;
    private KeyBinding openMessageHistoryKeyBinding;

    public KeyBindingManager(IChatConnectionManager chatConnectionManager, MessageSpawner messageSpawner) {
        this.chatConnectionManager = chatConnectionManager;
        this.messageSpawner = messageSpawner;
    }

    public void registerKeyBindings() {
        openConfigScreenKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chatrix.openconfig",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            KEY_CATEGORY
        ));

        startAndStopMessagesKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chatrix.startandstop",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            KEY_CATEGORY
        ));

        openMessageHistoryKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chatrix.openhistory",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigScreenKeyBinding.wasPressed()) {
                handleOpenConfigScreen();
            }
            while (startAndStopMessagesKeyBinding.wasPressed()) {
                handleChatToggle();
            }
            while (openMessageHistoryKeyBinding.wasPressed()) {
                handleOpenMessageHistory();
            }
        });
    }

    private void handleOpenConfigScreen() {
        try {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new ModConfigScreen());
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
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.currentScreen instanceof MessageHistoryScreen) {
                return;
            }
            var lifecycle = spawner.getLifecycleManager();
            if (client.textRenderer != null) {
                lifecycle.warmHistoryLayouts(client.textRenderer);
            }
            client.setScreen(new MessageHistoryScreen(client.currentScreen, lifecycle));
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

    public KeyBinding getOpenConfigScreenKeyBinding() {
        return openConfigScreenKeyBinding;
    }
}
