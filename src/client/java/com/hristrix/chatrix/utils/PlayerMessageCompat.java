package com.hristrix.chatrix.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Sends a short player chat message across Minecraft 1.21 and 26.2. */
public final class PlayerMessageCompat {
    private PlayerMessageCompat() {}

    public static void send(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.of(message), false);
        }
    }
}
