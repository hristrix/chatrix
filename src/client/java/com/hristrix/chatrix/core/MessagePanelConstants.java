package com.hristrix.chatrix.core;

import net.minecraft.util.Identifier;

/** Shared 9-slice panel constants for GUI and 3D rendering. */
public final class MessagePanelConstants {
    /** Panel fill: white texture tinted with {@code panelBaseColorRgb}. */
    public static final Identifier PANEL_TEXTURE = Identifier.of("chatrix", "textures/gui/message_panel.png");
    /** Panel border: white texture tinted with the platform color or a custom HEX color. */
    public static final Identifier PANEL_BORDER_TEXTURE = Identifier.of("chatrix", "textures/gui/message_panel_border.png");
    public static final Identifier PIN_TEXTURE = Identifier.of("chatrix", "textures/gui/pin.png");

    /** Default border color, matching the original texture's purple. */
    public static final int DEFAULT_BORDER_RGB = 0x6B00E3;

    public static final int TEX_SIZE = 32;
    public static final int CORNER = 8;
    public static final int MIN = CORNER * 2;
    public static final int PADDING_X = 6;
    public static final int PADDING_Y = 4;
    /** Message text wrapping width, matching 3D mode. */
    public static final int MESSAGE_WRAP_WIDTH = 120;
    /** Maximum distance in blocks for clicking and dragging 3D messages. */
    public static final double MESSAGE_INTERACTION_DISTANCE = 16.0;
    /** Fixed fall and fade duration: 0.5 seconds at 20 TPS. */
    public static final int MESSAGE_FALL_TICKS = 10;
    public static final int PIN_ICON_SIZE = 8;
    public static final int PIN_ICON_MARGIN = 1;
    /** Pin icon overhang above and beside the panel: half its size plus padding. */
    public static final int PIN_ICON_OVERFLOW = (PIN_ICON_SIZE / 2) + PIN_ICON_MARGIN;

    private MessagePanelConstants() {}
}
