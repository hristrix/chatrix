package com.hristrix.chatrix.ui.gui;

import net.minecraft.client.gui.DrawContext;

/** 1.20.1 draws the GUI immediately; DrawContext has no separate flush operation. */
public final class GuiLayerFlush {
    private GuiLayerFlush() {
    }

    public static void flushPending(DrawContext context) {
    }

    public static void renderOverlay(DrawContext context, Runnable overlay, int mouseX, int mouseY, float delta) {
        overlay.run();
    }
}
