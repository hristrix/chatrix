package com.hristrix.chatrix.config;

/**
 * Message size presets from before 2.2.0, retained for migration to {@code messageSizePercent}.
 */
public enum MessageScale {
    /**
     * Very small messages
     */
    TINY("tiny", 0.35f),
    
    /**
     * Small messages
     */
    SMALL("small", 0.55f),
    
    /**
     * Normal messages, with a base scale of 1.0
     */
    NORMAL("normal", 0.75f),
    
    /**
     * Large messages
     */
    LARGE("large", 1.0f),
    
    /**
     * Huge messages, formerly Large before the scale was reduced
     */
    HUGE("huge", 1.25f);
    
    private final String key;
    private final float scale;
    
    MessageScale(String key, float scale) {
        this.key = key;
        this.scale = scale;
    }
    
    public String getKey() {
        return key;
    }
    
    public float getScale() {
        return scale;
    }

    /** Percentage relative to {@link #NORMAL}, rounded to the slider step. */
    public int toSizePercent() {
        int percent = Math.round(scale / NORMAL.scale * 100f);
        int step = ModConfigData.SIZE_PERCENT_STEP;
        return Math.round(percent / (float) step) * step;
    }
    
    /**
     * Gets a size by key.
     */
    public static MessageScale fromKey(String key) {
        for (MessageScale scale : values()) {
            if (scale.key.equals(key)) {
                return scale;
            }
        }
        return NORMAL; // Default value
    }
    
    /**
     * Gets the next size when cycling.
     */
    public MessageScale next() {
        MessageScale[] values = values();
        int nextIndex = (this.ordinal() + 1) % values.length;
        return values[nextIndex];
    }
    
    /**
     * Gets the previous size when cycling.
     */
    public MessageScale previous() {
        MessageScale[] values = values();
        int prevIndex = (this.ordinal() - 1 + values.length) % values.length;
        return values[prevIndex];
    }
}