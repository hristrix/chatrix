package com.hristrix.chatrix.core;

import com.hristrix.chatrix.config.ModConfig;

/** Custom message panel fill and border colors. */
public final class MessagePanelColors {
    private MessagePanelColors() {}

    /** Fill tint for {@code message_panel.png}; 0x000000 is the default black panel. */
    public static int baseRgb() {
        return ModConfig.getPANEL_BASE_COLOR_RGB();
    }

    /** Border color: the platform brand color or a manual selection. */
    public static int borderRgb(int platformRgb) {
        return ModConfig.isPANEL_BORDER_FROM_PLATFORM()
            ? platformRgb & 0xFFFFFF
            : ModConfig.getPANEL_BORDER_COLOR_RGB();
    }
}
