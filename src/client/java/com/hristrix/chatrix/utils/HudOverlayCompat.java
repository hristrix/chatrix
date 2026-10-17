package com.hristrix.chatrix.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Action bar and overlay messages across Minecraft versions. */
public final class HudOverlayCompat {
    private HudOverlayCompat() {}

    public static void setOverlayMessage(MinecraftClient client, Text text, boolean tinted) {
        if (client != null && client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(text, tinted);
        }
    }
}
