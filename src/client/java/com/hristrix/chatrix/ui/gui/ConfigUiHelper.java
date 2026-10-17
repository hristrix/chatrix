package com.hristrix.chatrix.ui.gui;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ConfigUiHelper {
    private ConfigUiHelper() {}

    public static Text onOffText(boolean enabled) {
        return Text.translatable(enabled ? "chatrix.config.on" : "chatrix.config.off")
            .formatted(enabled ? Formatting.WHITE : Formatting.GRAY);
    }
}
