package com.hristrix.chatrix.ui.gui;

import net.minecraft.client.gui.DrawContext;

/** Reusable vertical scrollbar for mod screens. */
public final class GuiScrollbar {
    private GuiScrollbar() {}

    public static void draw(DrawContext context, int x, int top, int bottom, int scrollOffset, int totalContentHeight) {
        int trackHeight = bottom - top;
        context.fill(x, top, x + 6, bottom, ModUiTheme.SCROLL_TRACK);

        if (totalContentHeight <= trackHeight) {
            return;
        }

        int thumbHeight = Math.max(16, (trackHeight * trackHeight) / totalContentHeight);
        int maxThumbY = bottom - thumbHeight;
        int thumbY = top + (scrollOffset * (trackHeight - thumbHeight)) / (totalContentHeight - trackHeight);
        thumbY = Math.max(top, Math.min(maxThumbY, thumbY));
        ModUiTheme.drawSurface(context, x, thumbY, 6, thumbHeight,
            ModUiTheme.SCROLL_THUMB, ModUiTheme.SCROLL_THUMB, 1, false);
    }

    public static int clampScroll(int scrollOffset, int totalContentHeight, int viewportHeight) {
        return Math.max(0, Math.min(Math.max(0, totalContentHeight - viewportHeight), scrollOffset));
    }
}
