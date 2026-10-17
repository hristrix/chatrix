package com.hristrix.chatrix.ui.widget;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

/** Color row with a HEX field and a button opening the color picker. */
public final class ColorFieldRow {
    public final TextFieldWidget field;
    public final ButtonWidget picker;

    public ColorFieldRow(TextFieldWidget field, ButtonWidget picker) {
        this.field = field;
        this.picker = picker;
    }
}
