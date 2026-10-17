package com.hristrix.chatrix.ui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen over the world without dimming or blur; clicks and drags outside widgets
 * are forwarded to version-independent {@code onPointer*} handlers.
 */
public abstract class PreviewPointerScreen extends Screen {
    protected static final int POINTER_PRIMARY = 0;
    protected static final int POINTER_SECONDARY = 1;
    protected static final int POINTER_OTHER = 2;

    protected PreviewPointerScreen(Text title) {
        super(title);
    }

    protected abstract boolean onPointerPressed(double mouseX, double mouseY, int pointer);

    protected abstract boolean onPointerDragged(double mouseX, double mouseY, int pointer, double deltaX, double deltaY);

    protected abstract void onPointerReleased(double mouseX, double mouseY, int pointer);

    /** Consumes clicks before widgets, for example in a clipped part of the panel. */
    protected boolean isPointerBlocked(double mouseX, double mouseY) {
        return false;
    }

    /** Handles clicks before child widgets, for a fixed button over scrolling content. */
    protected boolean onPriorityClicked(double mouseX, double mouseY, int pointer) {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (onPriorityClicked(mouseX, mouseY, toPointer(button))) {
            return true;
        }
        if (isPointerBlocked(mouseX, mouseY)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return onPointerPressed(mouseX, mouseY, toPointer(button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (onPointerDragged(mouseX, mouseY, toPointer(button), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        onPointerReleased(mouseX, mouseY, toPointer(button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private static int toPointer(int button) {
        if (button == 0) {
            return POINTER_PRIMARY;
        }
        return button == 1 ? POINTER_SECONDARY : POINTER_OTHER;
    }
}
