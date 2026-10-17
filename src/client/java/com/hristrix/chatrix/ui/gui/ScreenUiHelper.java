package com.hristrix.chatrix.ui.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import java.util.HashSet;
import java.util.Set;

/** Draws screen buttons in ModUiTheme style over vanilla ButtonWidget behavior. */
public final class ScreenUiHelper {
    private ScreenUiHelper() {}

    public static Set<ButtonWidget> hideButtons(Screen screen) {
        Set<ButtonWidget> hidden = new HashSet<>();
        for (Element child : screen.children()) {
            if (child instanceof ButtonWidget button && button.visible) {
                button.visible = false;
                hidden.add(button);
            }
        }
        return hidden;
    }

    public static void restoreButtons(Set<ButtonWidget> hidden) {
        for (ButtonWidget button : hidden) {
            button.visible = true;
        }
    }

    public static void renderButtons(
        DrawContext context,
        int mouseX,
        int mouseY,
        Iterable<ButtonWidget> buttons,
        ButtonWidget selectedTab
    ) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        for (ButtonWidget button : buttons) {
            if (!button.visible) {
                continue;
            }
            boolean hovered = button.active && (button.isFocused() || ModUiTheme.isHovered(
                mouseX,
                mouseY,
                button.getX(),
                button.getY(),
                button.getWidth(),
                button.getHeight()
            ));
            ModUiTheme.drawButton(
                context,
                textRenderer,
                button.getX(),
                button.getY(),
                button.getWidth(),
                button.getHeight(),
                button.getMessage(),
                hovered,
                button.active,
                button == selectedTab,
                true
            );
        }
    }

    public static void renderAllButtons(DrawContext context, int mouseX, int mouseY, Screen screen) {
        renderButtons(context, mouseX, mouseY, screen.children().stream()
            .filter(ButtonWidget.class::isInstance)
            .map(ButtonWidget.class::cast)
            .toList(), null);
    }

}
