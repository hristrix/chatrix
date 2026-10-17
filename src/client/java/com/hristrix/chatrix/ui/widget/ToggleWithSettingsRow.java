package com.hristrix.chatrix.ui.widget;

import net.minecraft.client.gui.widget.ButtonWidget;

/** Setting row with an enable toggle and a detailed settings button. */
public final class ToggleWithSettingsRow {
    public final ButtonWidget toggle;
    public final ButtonWidget settings;

    public ToggleWithSettingsRow(ButtonWidget toggle, ButtonWidget settings) {
        this.toggle = toggle;
        this.settings = settings;
    }
}
