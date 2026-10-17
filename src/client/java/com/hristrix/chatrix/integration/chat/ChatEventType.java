package com.hristrix.chatrix.integration.chat;

/** Incoming message type: regular chat or a platform event. */
public enum ChatEventType {
    CHAT,
    GIFT,
    FOLLOW,
    SUBSCRIBE,
    RAID,
    BITS;

    public boolean isEvent() {
        return this != CHAT;
    }
}
