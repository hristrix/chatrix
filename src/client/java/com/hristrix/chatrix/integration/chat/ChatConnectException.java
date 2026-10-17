package com.hristrix.chatrix.integration.chat;

import java.io.IOException;

/** Platform connection failure with a localizable reason shown to the player. */
public class ChatConnectException extends IOException {
    private final String translationKey;
    private final Object[] args;

    public ChatConnectException(String translationKey, String logMessage, Object... args) {
        super(logMessage);
        this.translationKey = translationKey;
        this.args = args;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
