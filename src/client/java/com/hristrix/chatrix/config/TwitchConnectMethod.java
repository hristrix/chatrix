package com.hristrix.chatrix.config;

/** Twitch channel input method: username or stream key from the Creator Dashboard. */
public enum TwitchConnectMethod {
    CHANNEL,
    STREAM_KEY;

    public TwitchConnectMethod toggle() {
        return this == CHANNEL ? STREAM_KEY : CHANNEL;
    }

    public boolean usesStreamKey() {
        return this == STREAM_KEY;
    }

    public String getKey() {
        return this == STREAM_KEY ? "stream_key" : "channel";
    }
}
