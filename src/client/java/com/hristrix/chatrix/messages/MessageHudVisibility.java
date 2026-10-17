package com.hristrix.chatrix.messages;

import net.minecraft.client.MinecraftClient;
import com.hristrix.chatrix.utils.ScreenNavigationCompat;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * Controls when HUD messages appear over a screen.
 * Enabled for inventories, containers, and the visual editor; disabled for pause, chat, and settings screens.
 */
public final class MessageHudVisibility {
    private MessageHudVisibility() {}

    public static boolean shouldRender(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        Screen screen = ScreenNavigationCompat.current(client);
        if (screen == null) {
            return true;
        }
        return screen instanceof HandledScreen
            || screen instanceof com.hristrix.chatrix.ui.screen.MessageEditorScreen;
    }
}
