package com.hristrix.chatrix.integration.chat;

import net.minecraft.text.Text;
import com.hristrix.chatrix.config.ConfigManager;
import com.hristrix.chatrix.config.ModConfigData;
import com.hristrix.chatrix.interfaces.IChatConnectionManager;
import com.hristrix.chatrix.interfaces.IConfigManager;
import com.hristrix.chatrix.integration.kick.KickChatClient;
import com.hristrix.chatrix.integration.tiktok.TikTokChatClient;
import com.hristrix.chatrix.integration.twitch.TwitchChatClient;
import com.hristrix.chatrix.integration.youtube.YouTubeChatClient;
import com.hristrix.chatrix.messages.MessageSpawner;
import com.hristrix.chatrix.utils.PlayerMessageCompat;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

public class ChatConnectionManager implements IChatConnectionManager {
    private static final Logger LOGGER = Logger.getLogger(ChatConnectionManager.class.getName());
    private static ChatConnectionManager instance;

    private final ConfigManager configManager;
    private final Map<ChatPlatform, ChatConnection> connections = new EnumMap<>(ChatPlatform.class);
    private final Map<ChatPlatform, String> activeChannelIds = new EnumMap<>(ChatPlatform.class);
    private ChatMessagePipeline pipeline;
    private MessageSpawner messageSpawner;
    /**
     * The chat parser is running. Enabling a source only adds it to the set being parsed:
     * connection only occurs when the parser is running.
     */
    private boolean parserEnabled;

    private ChatConnectionManager(IConfigManager configManager) {
        this.configManager = configManager instanceof ConfigManager cm
            ? cm
            : ConfigManager.getInstance();
    }

    public static ChatConnectionManager getInstance(IConfigManager configManager) {
        if (instance == null) {
            instance = new ChatConnectionManager(configManager);
        }
        return instance;
    }

    @Override
    public void connect(MessageSpawner spawner) {
        parserEnabled = true;
        this.messageSpawner = spawner;
        if (this.pipeline == null) {
            this.pipeline = new ChatMessagePipeline(spawner);
        }

        ModConfigData config = configManager.getConfigData();
        int before = connections.size();

        connectPlatform(ChatPlatform.TWITCH, config.isTwitchEnabled(), config.getTwitchChannelName(),
            () -> new TwitchChatClient(config.getTwitchChannelName(), pipeline));
        connectPlatform(ChatPlatform.YOUTUBE, config.isYoutubeEnabled(), config.getYoutubeChannel(),
            () -> new YouTubeChatClient(config.getYoutubeChannel(), pipeline));
        connectPlatform(ChatPlatform.KICK, config.isKickEnabled(), config.getKickChannel(),
            () -> new KickChatClient(config.getKickChannel(), pipeline));
        connectPlatform(ChatPlatform.TIKTOK, config.isTiktokEnabled(), config.getTiktokUsername(),
            () -> new TikTokChatClient(config.getTiktokUsername(), pipeline));

        if (connections.isEmpty()) {
            ChatStatusNotifier.showTranslatableWarning("chatrix.status.no_platforms");
        } else if (connections.size() > before) {
            ChatStatusNotifier.showTranslatableSuccess(
                "chatrix.status.starting_count",
                connections.size()
            );
        }
    }

    private void connectPlatform(
        ChatPlatform platform,
        boolean enabled,
        String channel,
        java.util.function.Supplier<ChatConnection> factory
    ) {
        String normalized = normalizeChannel(channel);
        if (!enabled || normalized.isEmpty()) {
            return;
        }
        String activeChannel = activeChannelIds.get(platform);
        ChatConnection existing = connections.get(platform);
        if (existing != null && existing.isConnected() && normalized.equals(activeChannel)) {
            return;
        }
        if (existing != null) {
            stopConnection(platform, existing);
        }
        try {
            ChatStatusNotifier.showTranslatable(
                "chatrix.status.connecting",
                platform.getDisplayName(),
                normalized
            );
            ChatConnection connection = factory.get();
            connections.put(platform, connection);
            activeChannelIds.put(platform, normalized);
            LOGGER.info("Started " + platform + " client for channel: " + normalized);
        } catch (Exception e) {
            LOGGER.severe("Failed to start " + platform + " client: " + e.getMessage());
            reportConnectError(platform, e);
        }
    }

    @Override
    public void disconnect() {
        if (connections.isEmpty()) {
            parserEnabled = false;
            ChatStatusNotifier.showTranslatableWarning("chatrix.status.not_connected");
            return;
        }
        for (ChatPlatform platform : ChatPlatform.values()) {
            ChatConnection connection = connections.get(platform);
            if (connection != null) {
                stopConnection(platform, connection);
            }
        }
        connections.clear();
        activeChannelIds.clear();
        parserEnabled = false;
        ChatStatusNotifier.showTranslatable("chatrix.status.disconnected");
        LOGGER.info("Disconnected from all chat platforms");
    }

    @Override
    public boolean isParserEnabled() {
        return parserEnabled;
    }

    @Override
    public void reconnectChangedPlatforms() {
        if (messageSpawner == null) {
            messageSpawner = com.hristrix.chatrix.ChatrixClient.getStaticMessageSpawner();
        }
        if (messageSpawner == null) {
            return;
        }
        if (this.pipeline == null) {
            this.pipeline = new ChatMessagePipeline(messageSpawner);
        }
        ModConfigData config = configManager.getConfigData();
        reconnectIfChanged(ChatPlatform.TWITCH, config.isTwitchEnabled(), config.getTwitchChannelName(),
            () -> new TwitchChatClient(config.getTwitchChannelName(), pipeline));
        reconnectIfChanged(ChatPlatform.YOUTUBE, config.isYoutubeEnabled(), config.getYoutubeChannel(),
            () -> new YouTubeChatClient(config.getYoutubeChannel(), pipeline));
        reconnectIfChanged(ChatPlatform.KICK, config.isKickEnabled(), config.getKickChannel(),
            () -> new KickChatClient(config.getKickChannel(), pipeline));
        reconnectIfChanged(ChatPlatform.TIKTOK, config.isTiktokEnabled(), config.getTiktokUsername(),
            () -> new TikTokChatClient(config.getTiktokUsername(), pipeline));
    }

    private void reconnectIfChanged(
        ChatPlatform platform,
        boolean enabled,
        String channel,
        java.util.function.Supplier<ChatConnection> factory
    ) {
        String normalized = normalizeChannel(channel);
        ChatConnection existing = connections.get(platform);

        if (!enabled || normalized.isEmpty()) {
            if (existing != null) {
                stopConnection(platform, existing);
            }
            return;
        }

        // Source enabled, but parser stopped; connect when it starts.
        if (!parserEnabled) {
            return;
        }

        String activeChannel = activeChannelIds.get(platform);
        if (existing != null && existing.isConnected() && normalized.equals(activeChannel)) {
            return;
        }

        if (existing != null) {
            stopConnection(platform, existing);
        }
        connectPlatform(platform, true, normalized, factory);
    }

    private void stopConnection(ChatPlatform platform, ChatConnection connection) {
        try {
            connection.disconnect();
        } catch (Exception e) {
            LOGGER.warning("Disconnect error for " + platform + ": " + e.getMessage());
        }
        connections.remove(platform);
        activeChannelIds.remove(platform);
        ChatErrorReporter.clear(platform);
    }

    private static void reportConnectError(ChatPlatform platform, Exception error) {
        Text reason = error instanceof ChatConnectException cce
            ? Text.translatable(cce.getTranslationKey(), cce.getArgs())
            : Text.translatable(
                "chatrix.chat.error.connect",
                platform.getDisplayName(),
                String.valueOf(error.getMessage())
            );
        PlayerMessageCompat.send(
            net.minecraft.client.MinecraftClient.getInstance(),
            "§c" + reason.getString()
        );
    }

    private static String normalizeChannel(String channel) {
        return channel != null ? channel.trim() : "";
    }

    @Override
    public boolean isConnected() {
        return getConnectedCount() > 0;
    }

    @Override
    public boolean isPlatformConnected(ChatPlatform platform) {
        ChatConnection connection = connections.get(platform);
        return connection != null && connection.isConnected();
    }

    @Override
    public int getConnectedCount() {
        return (int) connections.values().stream().filter(ChatConnection::isConnected).count();
    }
}
