package com.hristrix.chatrix.integration.chat;

import java.util.List;

/**
 * Roles actually exposed by each source and available for filtering.
 * {@link #ALL} is the show-everyone mode, rather than a role.
 */
public enum PlatformRoleKind {
    ALL("all"),
    FOLLOWERS("followers"),
    SUBSCRIBERS("subs"),
    VIP("vip"),
    MODS("mods");

    private final String key;

    PlatformRoleKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean isAll() {
        return this == ALL;
    }

    /** Source-page order: everyone first, followed by available roles. */
    public static List<PlatformRoleKind> filtersFor(ChatPlatform platform) {
        return switch (platform) {
            case TWITCH -> List.of(ALL, SUBSCRIBERS, VIP, MODS);
            case YOUTUBE -> List.of(ALL, SUBSCRIBERS, MODS);
            case KICK -> List.of(ALL, SUBSCRIBERS, VIP, MODS);
            case TIKTOK -> List.of(ALL, FOLLOWERS, SUBSCRIBERS, MODS);
        };
    }

    public String labelKey(ChatPlatform platform) {
        if (this == ALL) {
            return "chatrix.config.role_filter_all";
        }
        return "chatrix.config.role_filter." + platform.getIconKey() + "." + key;
    }
}
