package com.hristrix.chatrix.ui.gui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.config.ConfigManager;
import com.hristrix.chatrix.integration.chat.ChatConnectionManager;
import com.hristrix.chatrix.messages.MessageSpawner;

/** Button and logic for connecting all enabled platforms. */
public final class ChatConnectToggleHelper {
    private ChatConnectToggleHelper() {}

    public static Text buttonLabel() {
        ChatConnectionManager manager = ChatConnectionManager.getInstance(ConfigManager.getInstance());
        boolean connected = manager.isParserEnabled();
        int count = manager.getConnectedCount();

        String key = connected
            ? "chatrix.config.chat_toggle_on"
            : "chatrix.config.chat_toggle_off";
        MutableText label = Text.translatable(key);
        if (connected && count > 0) {
            label.append(Text.literal(" (" + count + ")"));
        }
        label.append(Text.literal(" ●").formatted(connected ? Formatting.GREEN : Formatting.RED));
        return label;
    }

    public static void toggle() {
        try {
            ChatConnectionManager manager = ChatConnectionManager.getInstance(ConfigManager.getInstance());
            MessageSpawner spawner = ChatrixClient.getStaticMessageSpawner();
            if (manager.isParserEnabled()) {
                manager.disconnect();
            } else if (spawner != null) {
                manager.connect(spawner);
            }
        } catch (Exception e) {
            ChatrixClient.LOGGER.error("Chat toggle error", e);
        }
    }
}
