package com.hristrix.chatrix.ui.widget;

import net.minecraft.client.gui.widget.ButtonWidget;
import com.hristrix.chatrix.integration.chat.ChatPlatform;

/** Home-page platform card with an enable toggle and a settings button. */
public final class PlatformSettingsCard {
    public final ChatPlatform platform;
    public final ButtonWidget toggle;
    public final ButtonWidget settings;

    public PlatformSettingsCard(ChatPlatform platform, ButtonWidget toggle, ButtonWidget settings) {
        this.platform = platform;
        this.toggle = toggle;
        this.settings = settings;
    }
}
