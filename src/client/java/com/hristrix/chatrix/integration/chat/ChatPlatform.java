package com.hristrix.chatrix.integration.chat;

public enum ChatPlatform {
    TWITCH("Twitch", "§5", com.hristrix.chatrix.core.MessagePanelConstants.DEFAULT_BORDER_RGB),
    YOUTUBE("YouTube", "§c", 0xFF0000),
    KICK("Kick", "§a", 0x53FC18),
    TIKTOK("TikTok", "§b", 0x25F4EE);

    private final String displayName;
    private final String prefixColor;
    private final int accentColorRgb;

    ChatPlatform(String displayName, String prefixColor, int accentColorRgb) {
        this.displayName = displayName;
        this.prefixColor = prefixColor;
        this.accentColorRgb = accentColorRgb;
    }

    /** Platform brand color for the message panel border. */
    public int getAccentColorRgb() {
        return accentColorRgb;
    }

    /** Border color by platform icon key; unknown keys use the default purple. */
    public static int accentColorForIconKey(String iconKey) {
        if (iconKey != null) {
            for (ChatPlatform platform : values()) {
                if (platform.getIconKey().equals(iconKey)) {
                    return platform.accentColorRgb;
                }
            }
        }
        return com.hristrix.chatrix.core.MessagePanelConstants.DEFAULT_BORDER_RGB;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrefixColor() {
        return prefixColor;
    }

    /** Platform pixel icon key: assets/chatrix/textures/platform/<key>.png */
    public String getIconKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
