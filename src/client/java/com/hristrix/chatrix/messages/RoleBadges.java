package com.hristrix.chatrix.messages;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Pixel role badges for the author. Like the platform icon, they move with the message
 * as {@link MessageEmote} with provider {@link #PROVIDER} and indices set to -1.
 */
public final class RoleBadges {
    /** Marker emote provider: assets/chatrix/textures/badge/&lt;key&gt;.png */
    public static final String PROVIDER = "badge";
    public static final int ICON_SIZE = 8;
    /** Spacing between badges in 3D panels, matching emote spacing. */
    public static final int WORLD_SPACING = 1;
    /** Spacing between GUI badges, matching {@code drawGuiIcon}. */
    public static final int GUI_SPACING = 2;

    private RoleBadges() {}

    /** Badge keys ordered from highest to lowest role; empty if no roles are present. */
    public static List<String> keysFor(boolean subscriber, boolean vip, boolean moderator, boolean broadcaster) {
        List<String> keys = new ArrayList<>(4);
        if (broadcaster) {
            keys.add("host");
        }
        if (moderator) {
            keys.add("mod");
        }
        if (vip) {
            keys.add("vip");
        }
        if (subscriber) {
            keys.add("sub");
        }
        return keys;
    }

    public static Identifier iconTexture(String key) {
        return Identifier.of("chatrix", "textures/badge/" + key + ".png");
    }

    /** Width of the badge row in a 3D panel, including trailing spacing. */
    public static int worldRowWidth(int badgeCount) {
        return badgeCount <= 0 ? 0 : badgeCount * (ICON_SIZE + WORLD_SPACING);
    }

    /** Width of the GUI badge row, including trailing spacing. */
    public static int guiRowWidth(int badgeCount) {
        return badgeCount <= 0 ? 0 : badgeCount * (ICON_SIZE + GUI_SPACING);
    }
}
