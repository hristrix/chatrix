package com.hristrix.chatrix.ui.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.hristrix.chatrix.integration.chat.ChatPlatform;
import com.hristrix.chatrix.messages.TwitchEmoteTextureCache;

/**
 * Branded charcoal source banner with a white-and-violet platform icon.
 * Shared appearance for home-page cards and the source-page header.
 */
public final class PlatformBanner {
    private static final int ICON_PADDING = 10;
    private static final int MAX_ICON_SIZE = 22;
    private static final int ICON_TO_TEXT_GAP = 4;

    private PlatformBanner() {}

    public static void draw(
        DrawContext context,
        TextRenderer textRenderer,
        ChatPlatform platform,
        int x,
        int y,
        int width,
        int height,
        boolean hovered
    ) {
        ModUiTheme.drawSurface(context, x, y, width, height,
            hovered ? ModUiTheme.BUTTON_BG_HOVER : ModUiTheme.BUTTON_BG,
            hovered ? ModUiTheme.ACCENT : ModUiTheme.PANEL_BORDER, 3, false);
        context.fill(x + 4, y + 6, x + 6, y + height - 6, ModUiTheme.ACCENT);

        int iconSize = Math.min(MAX_ICON_SIZE, height - ICON_PADDING);
        int iconX = x + ICON_PADDING;
        int textX = iconX;
        Identifier icon = TwitchEmoteTextureCache.getTextureIdentifier("platform", platform.getIconKey());
        if (icon != null) {
            textX = MessageEmoteGuiRenderer.drawGuiIcon(
                context,
                icon,
                iconX,
                y + (height - iconSize) / 2,
                iconSize
            ) + ICON_TO_TEXT_GAP;
        }
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(platform.getDisplayName()),
            textX,
            ModUiTheme.centeredTextY(y, height, textRenderer.fontHeight),
            ModUiTheme.TEXT_PRIMARY
        );
    }
}
