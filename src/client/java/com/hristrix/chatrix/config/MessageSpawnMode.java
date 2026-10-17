package com.hristrix.chatrix.config;

/**
 * Message display modes.
 */
public enum MessageSpawnMode {
    /**
     * Spawns messages around the player in 3D space.
     */
    AROUND_PLAYER("around_player"),
    
    /**
     * Spawns messages only in front of the player in 3D space.
     */
    FRONT_OF_PLAYER("front_of_player"),
    
    /**
     * Displays messages as a HUD widget in the upper-right screen corner.
     */
    HUD_WIDGET("hud_widget");
    
    private final String key;
    
    MessageSpawnMode(String key) {
        this.key = key;
    }
    
    public String getKey() {
        return key;
    }
    
    /**
     * Gets a mode by key.
     */
    public static MessageSpawnMode fromKey(String key) {
        for (MessageSpawnMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return AROUND_PLAYER; // Default value
    }
    
    /**
     * Gets the next mode when cycling.
     */
    public MessageSpawnMode next() {
        MessageSpawnMode[] values = values();
        int nextIndex = (this.ordinal() + 1) % values.length;
        return values[nextIndex];
    }

    public boolean isHud() {
        return this == HUD_WIDGET;
    }

    /** Switches world and HUD display, restoring the previous 3D mode when returning to the world. */
    public MessageSpawnMode toggleDisplayKind(MessageSpawnMode lastWorldMode) {
        if (isHud()) {
            return lastWorldMode != null && !lastWorldMode.isHud() ? lastWorldMode : FRONT_OF_PLAYER;
        }
        return HUD_WIDGET;
    }

    /** Switches Around and In front modes; leaves HUD mode unchanged. */
    public MessageSpawnMode nextWorldPlacement() {
        if (this == AROUND_PLAYER) {
            return FRONT_OF_PLAYER;
        }
        if (this == FRONT_OF_PLAYER) {
            return AROUND_PLAYER;
        }
        return this;
    }
}