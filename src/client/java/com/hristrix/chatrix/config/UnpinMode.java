package com.hristrix.chatrix.config;

/** How a pinned world message is unpinned with the right mouse button. */
public enum UnpinMode {
    /** Unpin only when the pin icon is clicked. */
    PIN_ICON("pin_icon"),

    /** Unpin when any point on the message panel is clicked. */
    WHOLE_MESSAGE("whole_message");

    private final String key;

    UnpinMode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static UnpinMode fromKey(String key) {
        if (key == null) {
            return PIN_ICON;
        }
        for (UnpinMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return PIN_ICON;
    }

    public UnpinMode next() {
        UnpinMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
