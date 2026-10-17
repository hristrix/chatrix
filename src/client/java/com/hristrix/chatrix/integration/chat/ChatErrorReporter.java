package com.hristrix.chatrix.integration.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import com.hristrix.chatrix.utils.PlayerMessageCompat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reports localized connection errors in player chat without repeating them on retries. */
public final class ChatErrorReporter {
    private static final Map<ChatPlatform, String> lastReported = new ConcurrentHashMap<>();

    private ChatErrorReporter() {}

    /** Reports the failure reason to the player without repeating identical reasons. */
    public static void report(ChatPlatform platform, Exception error) {
        if (error instanceof ChatConnectException cce && isOfflineStatus(cce)) {
            ChatStatusNotifier.setOfflineWarning(platform, cce.getTranslationKey(), cce.getArgs());
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            Text reason = error instanceof ChatConnectException cce
                ? Text.translatable(cce.getTranslationKey(), cce.getArgs())
                : Text.translatable(
                    "chatrix.chat.error.generic",
                    platform.getDisplayName(),
                    String.valueOf(error.getMessage())
                );
            String message = "§c" + reason.getString();
            String previous = lastReported.put(platform, message);
            if (message.equals(previous)) {
                return;
            }
            PlayerMessageCompat.send(client, message);
        });
    }

    /** Resets deduplication after successful connection or platform disconnection. */
    public static void clear(ChatPlatform platform) {
        lastReported.remove(platform);
        ChatStatusNotifier.clearOfflineWarning(platform);
    }

    private static boolean isOfflineStatus(ChatConnectException error) {
        String key = error.getTranslationKey();
        return "chatrix.chat.error.no_live_room".equals(key)
            || "chatrix.chat.error.not_live".equals(key);
    }
}
