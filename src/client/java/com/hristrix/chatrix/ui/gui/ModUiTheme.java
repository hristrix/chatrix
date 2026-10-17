package com.hristrix.chatrix.ui.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Chatrix GUI: charcoal surfaces, white text, violet accents, and clipped geometric corners. */
public final class ModUiTheme {
    private static final Identifier BRAND_ICON = Identifier.of("chatrix", "textures/gui/icon_tab_messages.png");
    /** Primary accent color, purple like the message panels. */
    public static final int ACCENT = 0xFF9425FA;
    public static final int ACCENT_DARK = 0xFF5B1799;

    public static final int PANEL_BG = 0xFA0C0B10;
    public static final int PANEL_BORDER = 0xFF39333F;
    public static final int BUTTON_BG = 0xFF1A171F;
    public static final int BUTTON_BG_HOVER = 0xFF302337;
    public static final int BUTTON_BORDER_HOVER = 0xFFC99AFB;
    public static final int BUTTON_BG_SELECTED = 0xFF4B1677;
    public static final int ROW_BG = 0xB0141218;
    public static final int ROW_BG_HOVER = 0xE0251B2F;
    public static final int INPUT_BG = 0xFF09080D;
    public static final int DANGER_BG = 0x90604040;
    public static final int DANGER_BG_HOVER = 0xC0805050;
    public static final int ACCENT_BG = 0xFF4B1677;
    public static final int ACCENT_BG_HOVER = 0xFF6D20AD;
    /** Background of the chat-connected button. */
    public static final int CONNECTION_ON_BG = ACCENT_BG;
    public static final int CONNECTION_ON_BG_HOVER = ACCENT_BG_HOVER;
    public static final int CONNECTION_ON_BORDER = ACCENT;
    /** Background of the chat-disconnected button. */
    public static final int CONNECTION_OFF_BG = BUTTON_BG;
    public static final int CONNECTION_OFF_BG_HOVER = BUTTON_BG_HOVER;
    public static final int CONNECTION_OFF_BORDER = PANEL_BORDER;
    public static final int TEXT_PRIMARY = 0xFFFAFAFC;
    public static final int TEXT_SECONDARY = 0xFFE1DCE8;
    public static final int TEXT_MUTED = 0xFFB4AABE;
    public static final int TEXT_DIM = 0xFF666666;
    public static final int TEXT_HINT = 0xFF9C91A7;
    public static final int OVERLAY_DIM = 0x99040308;
    /** Fully opaque panel for modal popups (avoids batched text bleeding through on 1.21). */
    public static final int POPUP_PANEL_BG = 0xFF100D15;
    public static final int SCROLL_TRACK = 0xFF241B2E;
    public static final int SCROLL_THUMB = ACCENT;

    public enum ButtonVariant {
        NEUTRAL,
        DANGER,
        ACCENT
    }

    private ModUiTheme() {}

    public static void drawBorderedPanel(DrawContext context, int x, int y, int width, int height) {
        drawSurface(context, x, y, width, height, PANEL_BG, PANEL_BORDER, 4, false);
        if (width > 16) {
            context.fill(x + 8, y, x + Math.min(width - 8, 38), y + 1, ACCENT);
        }
    }

    /** Draws the clipped-corner silhouette used by Chatrix's logo and icons. */
    public static void drawSurface(DrawContext context, int x, int y, int width, int height,
        int background, int border, int corner, boolean overlay) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int cut = Math.max(0, Math.min(corner, (Math.min(width, height) - 1) / 2));
        fill(context, x, y + cut, x + width, y + height - cut, background, overlay);
        for (int row = 0; row < cut; row++) {
            int inset = cut - row;
            fill(context, x + inset, y + row, x + width - inset, y + row + 1, background, overlay);
            fill(context, x + inset, y + height - row - 1, x + width - inset, y + height - row, background, overlay);
        }
        fill(context, x + cut, y, x + width - cut, y + 1, border, overlay);
        fill(context, x + cut, y + height - 1, x + width - cut, y + height, border, overlay);
        fill(context, x, y + cut, x + 1, y + height - cut, border, overlay);
        fill(context, x + width - 1, y + cut, x + width, y + height - cut, border, overlay);
        for (int row = 1; row < cut; row++) {
            int inset = cut - row;
            fill(context, x + inset, y + row, x + inset + 1, y + row + 1, border, overlay);
            fill(context, x + width - inset - 1, y + row, x + width - inset, y + row + 1, border, overlay);
            fill(context, x + inset, y + height - row - 1, x + inset + 1, y + height - row, border, overlay);
            fill(context, x + width - inset - 1, y + height - row - 1, x + width - inset, y + height - row, border, overlay);
        }
    }

    private static void fill(DrawContext context, int left, int top, int right, int bottom, int color, boolean overlay) {
        if (right <= left || bottom <= top) {
            return;
        }
        if (overlay) {
            PopupGuiRenderer.fill(context, left, top, right, bottom, color);
        } else {
            context.fill(left, top, right, bottom, color);
        }
    }

    public static void drawButtonWidget(
        DrawContext context,
        TextRenderer textRenderer,
        ButtonWidget button,
        boolean selected
    ) {
        drawButton(
            context,
            textRenderer,
            button.getX(),
            button.getY(),
            button.getWidth(),
            button.getHeight(),
            button.getMessage(),
            button.isHovered() || button.isFocused(),
            button.active,
            selected,
            true
        );
    }

    public static void drawButton(
        DrawContext context,
        TextRenderer textRenderer,
        int x,
        int y,
        int width,
        int height,
        Text label,
        boolean hovered,
        boolean enabled,
        boolean selected
    ) {
        drawButton(context, textRenderer, x, y, width, height, label, hovered, enabled, selected, true);
    }

    public static void drawButton(
        DrawContext context,
        TextRenderer textRenderer,
        int x,
        int y,
        int width,
        int height,
        Text label,
        boolean hovered,
        boolean enabled,
        boolean selected,
        boolean hoverBorders
    ) {
        drawButtonInternal(context, textRenderer, x, y, width, height, label, hovered, enabled, selected, hoverBorders, false);
    }

    /** Popup menu button — uses overlay render layer on Minecraft 1.21. */
    public static void drawPopupButton(
        DrawContext context,
        TextRenderer textRenderer,
        int x,
        int y,
        int width,
        int height,
        Text label,
        boolean hovered,
        boolean enabled,
        boolean selected
    ) {
        drawButtonInternal(context, textRenderer, x, y, width, height, label, hovered, enabled, selected, true, true);
    }

    private static void drawButtonInternal(
        DrawContext context,
        TextRenderer textRenderer,
        int x,
        int y,
        int width,
        int height,
        Text label,
        boolean hovered,
        boolean enabled,
        boolean selected,
        boolean hoverBorders,
        boolean overlayLayer
    ) {
        int bg = selected ? BUTTON_BG_SELECTED : (hovered && enabled ? BUTTON_BG_HOVER : BUTTON_BG);
        if (!enabled) {
            bg = 0xFF141218;
        }
        int border = selected ? ACCENT : (hovered && enabled && hoverBorders ? BUTTON_BORDER_HOVER : PANEL_BORDER);
        drawSurface(context, x, y, width, height, bg, border, 3, overlayLayer);
        if (selected) {
            fill(context, x + 5, y + height - 2, x + width - 5, y + height - 1, ACCENT, overlayLayer);
        }
        int textColor = enabled ? (hovered || selected ? TEXT_PRIMARY : TEXT_SECONDARY) : TEXT_HINT;
        int labelWidth = textRenderer.getWidth(label);
        int textX = x + Math.max(6, (width - labelWidth) / 2);
        int textY = centeredTextY(y, height, textRenderer.fontHeight);
        if (label.getStyle().getColor() != null) {
            context.drawTextWithShadow(textRenderer, label, textX, textY, 0xFFFFFFFF);
        } else {
            context.drawTextWithShadow(textRenderer, label, textX, textY, textColor);
        }
    }

    public static void drawConnectionToggleButton(
        DrawContext context,
        TextRenderer textRenderer,
        ButtonWidget button,
        boolean connected,
        boolean hovered
    ) {
        drawConnectionToggleButton(
            context,
            textRenderer,
            button.getX(),
            button.getY(),
            button.getWidth(),
            button.getHeight(),
            button.getMessage(),
            connected,
            hovered,
            button.active
        );
    }

    public static void drawConnectionToggleButton(
        DrawContext context,
        TextRenderer textRenderer,
        int x,
        int y,
        int width,
        int height,
        Text label,
        boolean connected,
        boolean hovered,
        boolean enabled
    ) {
        int bg = connected
            ? (hovered && enabled ? CONNECTION_ON_BG_HOVER : CONNECTION_ON_BG)
            : (hovered && enabled ? CONNECTION_OFF_BG_HOVER : CONNECTION_OFF_BG);
        int border = connected ? CONNECTION_ON_BORDER : CONNECTION_OFF_BORDER;
        if (!enabled) {
            bg = 0xFF141218;
            border = PANEL_BORDER;
        }
        drawSurface(context, x, y, width, height, bg, border, 3, false);
        int textColor = enabled ? TEXT_PRIMARY : TEXT_HINT;
        int labelWidth = textRenderer.getWidth(label);
        int textX = x + Math.max(6, (width - labelWidth) / 2);
        int textY = centeredTextY(y, height, textRenderer.fontHeight);
        context.drawTextWithShadow(textRenderer, label, textX, textY, textColor);
    }

    public static void drawCompactButton(
        DrawContext context,
        TextRenderer textRenderer,
        int x,
        int y,
        int width,
        int height,
        Text label,
        boolean hovered,
        ButtonVariant variant
    ) {
        int bg = switch (variant) {
            case DANGER -> hovered ? DANGER_BG_HOVER : DANGER_BG;
            case ACCENT -> hovered ? ACCENT_BG_HOVER : ACCENT_BG;
            case NEUTRAL -> hovered ? BUTTON_BG_HOVER : BUTTON_BG;
        };
        int border = variant == ButtonVariant.ACCENT ? ACCENT : (hovered ? BUTTON_BORDER_HOVER : PANEL_BORDER);
        drawSurface(context, x, y, width, height, bg, border, 3, false);
        int labelWidth = textRenderer.getWidth(label);
        int textX = x + (width - labelWidth) / 2;
        int textY = centeredTextY(y, height, textRenderer.fontHeight);
        context.drawTextWithShadow(textRenderer, label, textX, textY, hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
    }

    public static void drawListRow(DrawContext context, int left, int top, int right, int bottom, boolean hovered) {
        context.fill(left, top, right, bottom, hovered ? ROW_BG_HOVER : ROW_BG);
        if (hovered) {
            context.fill(left, top + 2, left + 2, bottom - 2, ACCENT);
        }
    }

    public static void drawInputFrame(DrawContext context, int x, int y, int width, int height, boolean focused) {
        int border = focused ? ACCENT : PANEL_BORDER;
        drawSurface(context, x, y, width, height, INPUT_BG, border, 2, false);
    }

    public static void drawTitle(DrawContext context, TextRenderer textRenderer, Text title, int screenWidth) {
        drawTitle(context, textRenderer, title, screenWidth, 6);
    }

    public static void drawTitle(DrawContext context, TextRenderer textRenderer, Text title, int screenWidth, int y) {
        int titleWidth = textRenderer.getWidth(title);
        int left = (screenWidth - titleWidth - 16) / 2;
        MessageEmoteGuiRenderer.drawGuiIcon(context, BRAND_ICON, left, y - 1, 12);
        context.drawTextWithShadow(textRenderer, title, left + 16, y, TEXT_PRIMARY);
        int lineY = y + textRenderer.fontHeight + 2;
        context.fill(left, lineY, left + titleWidth + 16, lineY + 1, ACCENT);
        context.fill(left, lineY, left + 12, lineY + 1, TEXT_PRIMARY);
    }

    /** Two-line branded heading, with the author beneath the mod name. */
    public static void drawTitle(DrawContext context, TextRenderer textRenderer, Text title, Text subtitle, int screenWidth, int y) {
        int titleWidth = textRenderer.getWidth(title);
        int left = (screenWidth - titleWidth - 16) / 2;
        MessageEmoteGuiRenderer.drawGuiIcon(context, BRAND_ICON, left, y - 1, 12);
        context.drawTextWithShadow(textRenderer, title, left + 16, y, TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, subtitle, screenWidth / 2,
            y + textRenderer.fontHeight + 3, TEXT_SECONDARY);
        int lineY = y + textRenderer.fontHeight * 2 + 5;
        int lineWidth = Math.max(titleWidth + 16, textRenderer.getWidth(subtitle));
        int lineLeft = (screenWidth - lineWidth) / 2;
        context.fill(lineLeft, lineY, lineLeft + lineWidth, lineY + 1, ACCENT);
        context.fill(lineLeft, lineY, lineLeft + Math.min(12, lineWidth), lineY + 1, TEXT_PRIMARY);
    }

    /** Paints a slider while retaining its native keyboard, mouse, and narration behavior. */
    public static void drawSlider(DrawContext context, TextRenderer textRenderer,
        int x, int y, int width, int height, Text label, double value,
        boolean hovered, boolean focused, boolean enabled) {
        context.fill(x, y, x + width, y + height, 0xFF141218);
        drawSurface(context, x, y, width, height, INPUT_BG,
            hovered || focused ? BUTTON_BORDER_HOVER : PANEL_BORDER, 3, false);
        int trackLeft = x + 5;
        int trackRight = x + Math.max(5, width - 5);
        int trackY = y + height - 5;
        int thumb = trackLeft + (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * (trackRight - trackLeft));
        context.fill(trackLeft, trackY, trackRight, trackY + 2, SCROLL_TRACK);
        context.fill(trackLeft, trackY, thumb, trackY + 2, enabled ? ACCENT : PANEL_BORDER);
        context.fill(thumb - 1, trackY - 1, thumb + 2, trackY + 3, enabled ? TEXT_PRIMARY : TEXT_HINT);
        context.drawCenteredTextWithShadow(textRenderer, label, x + width / 2,
            y + Math.max(2, (height - textRenderer.fontHeight) / 2 - 2), enabled ? TEXT_PRIMARY : TEXT_HINT);
    }

    public static void drawScreenDim(DrawContext context, int screenWidth, int screenHeight) {
        context.fill(0, 0, screenWidth, screenHeight, OVERLAY_DIM);
    }

    public static int centeredTextY(int y, int height, int fontHeight) {
        return y + (height - fontHeight) / 2;
    }

    public static boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
