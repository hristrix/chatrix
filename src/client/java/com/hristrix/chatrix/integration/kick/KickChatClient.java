package com.hristrix.chatrix.integration.kick;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.Text;
import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.integration.chat.ChatAuthorRoles;
import com.hristrix.chatrix.integration.chat.ChatConnection;
import com.hristrix.chatrix.integration.chat.ChatEventType;
import com.hristrix.chatrix.integration.chat.ChatHttp;
import com.hristrix.chatrix.integration.chat.ChatMessagePipeline;
import com.hristrix.chatrix.integration.chat.ChatPlatform;
import com.hristrix.chatrix.integration.chat.IncomingChatMessage;
import com.hristrix.chatrix.integration.chat.SimpleWebSocket;

public class KickChatClient implements ChatConnection {
    private static final Gson GSON = new Gson();
    private final String channelSlug;
    private final ChatMessagePipeline pipeline;
    private volatile boolean running;
    private volatile Thread workerThread;
    private volatile SimpleWebSocket webSocket;
    private volatile boolean subscribed;
    private long channelId;
    private long chatroomId;
    private final String clientId = java.util.UUID.randomUUID().toString();

    public KickChatClient(String channelSlug, ChatMessagePipeline pipeline) {
        this.channelSlug = com.hristrix.chatrix.integration.chat.ChatChannelName.normalize(channelSlug, "kick");
        this.pipeline = pipeline;
        this.running = true;
        start();
    }

    private void start() {
        workerThread = new Thread(this::connectLoop, "KickChat-" + channelSlug);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void connectLoop() {
        while (running) {
            try {
                KickIds ids = resolveIds(channelSlug);
                channelId = ids.channelId();
                chatroomId = ids.chatroomId();
                if (!running) {
                    break;
                }
                connectWebSocket(resolveRealtimeUrl(channelId));
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
                while (running && webSocket != null && webSocket.isRunning()) {
                    if (!subscribed && System.nanoTime() > deadline) {
                        throw new java.io.IOException("Kick chat subscription timed out");
                    }
                    Thread.sleep(1000);
                }
                if (running && webSocket != null && webSocket.getFailure() != null) {
                    throw new java.io.IOException("Kick chat connection lost: " + webSocket.getFailure().getMessage(), webSocket.getFailure());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted() || isInterruption(e)) {
                    break;
                }
                ChatrixClient.LOGGER.warn("Kick chat error for {}: {}", channelSlug, e.getMessage());
                com.hristrix.chatrix.integration.chat.ChatErrorReporter.report(ChatPlatform.KICK, e);
            } finally {
                subscribed = false;
                if (webSocket != null) {
                    webSocket.disconnect();
                }
            }
            if (running) {
                sleepQuietly(5000);
            }
        }
    }

    private void connectWebSocket(String url) {
        subscribed = false;
        webSocket = new SimpleWebSocket(url, null, this::handleMessage);
        webSocket.connect();
    }

    private String resolveRealtimeUrl(long id) throws java.io.IOException {
        // Kick rotates chat application keys; request the channel's current public descriptor.
        String body = ChatHttp.postJson(
            "https://web.kick.com/api/v1/realtime/channels/" + id + "/chat/connection",
            GSON.toJson(java.util.Map.of(
                "client", java.util.Map.of("id", clientId, "type", "web"),
                "capabilities", java.util.Map.of("accepted_providers",
                    java.util.List.of(java.util.Map.of("provider", "pusher")))
            )),
            java.util.Map.of("Origin", "https://kick.com", "Referer", "https://kick.com/" + channelSlug)
        );
        JsonObject response = JsonParser.parseString(body).getAsJsonObject();
        if (response.has("data") && response.get("data").isJsonObject()) {
            JsonObject data = response.getAsJsonObject("data");
            if (data.has("connections") && data.get("connections").isJsonArray()) {
                for (JsonElement element : data.getAsJsonArray("connections")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject connection = element.getAsJsonObject();
                    if (!"pusher".equals(textOrEmpty(connection, "provider"))
                        || !connection.has("credentials") || !connection.get("credentials").isJsonObject()) {
                        continue;
                    }
                    JsonObject credentials = connection.getAsJsonObject("credentials");
                    String key = textOrEmpty(credentials, "app_key");
                    String cluster = textOrEmpty(credentials, "cluster");
                    if (key.matches("[a-zA-Z0-9]+") && cluster.matches("[a-z0-9-]+")) {
                        return "wss://ws-" + cluster + ".pusher.com/app/" + key
                            + "?protocol=7&client=js&version=8.4.0&flash=false";
                    }
                }
            }
        }
        throw new java.io.IOException("Kick did not provide a supported public chat connection");
    }

    private void subscribePusher(String channel) {
        webSocket.send(GSON.toJson(java.util.Map.of(
            "event", "pusher:subscribe",
            "data", java.util.Map.of("auth", "", "channel", channel)
        )));
    }

    private void handleMessage(String raw) {
        JsonObject envelope;
        try {
            envelope = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return;
        }

        String event = envelope.has("event") ? envelope.get("event").getAsString() : "";
        if ("pusher:ping".equals(event)) {
            webSocket.send("{\"event\":\"pusher:pong\",\"data\":{}}");
            return;
        }
        if ("pusher:connection_established".equals(event)) {
            subscribePusher("chatrooms." + chatroomId + ".v2");
            subscribePusher("chatrooms." + chatroomId);
            if (channelId > 0) {
                subscribePusher("channel." + channelId);
            }
            return;
        }
        if ("pusher_internal:subscription_succeeded".equals(event)
            && ("chatrooms." + chatroomId + ".v2").equals(textOrEmpty(envelope, "channel"))) {
            subscribed = true;
            ChatrixClient.LOGGER.info("Kick chat subscribed for {}", channelSlug);
            com.hristrix.chatrix.integration.chat.ChatErrorReporter.clear(ChatPlatform.KICK);
            com.hristrix.chatrix.integration.chat.ChatStatusNotifier.showTranslatableSuccess(
                "chatrix.status.connected_platform", "Kick", channelSlug);
            return;
        }
        if ("pusher:error".equals(event) || "pusher:subscription_error".equals(event)) {
            com.hristrix.chatrix.integration.chat.ChatErrorReporter.report(ChatPlatform.KICK,
                new java.io.IOException("Kick rejected the chat subscription: " + envelope.get("data")));
            webSocket.disconnect();
            return;
        }
        JsonObject data = parseData(envelope);
        if (data == null) {
            return;
        }

        String eventName = shortEventName(event);
        if ("ChatMessageEvent".equals(eventName)) {
            handleChatMessage(data);
            return;
        }
        handleLiveEvent(eventName, data);
    }

    private void handleChatMessage(JsonObject data) {
        String username = textOrEmpty(data, "username");
        if (username.isBlank()) {
            username = textOrEmpty(data, "sender", "username");
        }
        String message = textOrEmpty(data, "content");
        if (message.isBlank()) {
            message = textOrEmpty(data, "message");
        }
        if (message.isBlank()) {
            return;
        }

        String displayName = username;
        ChatAuthorRoles roles = ChatAuthorRoles.NONE;
        if (data.has("sender") && data.get("sender").isJsonObject()) {
            JsonObject sender = data.getAsJsonObject("sender");
            String senderName = textOrEmpty(sender, "username");
            if (!senderName.isBlank()) {
                username = senderName;
                displayName = senderName;
            }
            if (sender.has("identity") && sender.get("identity").isJsonObject()) {
                JsonObject identity = sender.getAsJsonObject("identity");
                String identityName = textOrEmpty(identity, "username");
                if (!identityName.isBlank()) {
                    displayName = identityName;
                }
                roles = rolesFromKickIdentity(identity);
            }
        }

        Integer rgb = null;
        if (data.has("sender") && data.get("sender").isJsonObject()) {
            JsonObject sender = data.getAsJsonObject("sender");
            if (sender.has("identity") && sender.get("identity").isJsonObject()) {
                String color = textOrEmpty(sender.getAsJsonObject("identity"), "color");
                rgb = parseHexColor(color);
            }
        }

        var builder = IncomingChatMessage.builder(ChatPlatform.KICK)
            .authorLogin(username)
            .displayName(displayName)
            .text(message)
            .emotes(KickEmoteParser.parse(message))
            .roles(roles);
        if (rgb != null) {
            builder.authorColorRgb(rgb);
        }
        pipeline.process(builder.build());
    }

    /**
     * Public Kick Pusher chat events: subscriptions and gifts on {@code chatrooms.{id}.v2},
     * hosts and follows sometimes arrive on {@code channel.{id}}.
     */
    private void handleLiveEvent(String eventName, JsonObject data) {
        String username = firstNonBlank(
            textOrEmpty(data, "username"),
            textOrEmpty(data, "gifter_username"),
            textOrEmpty(data, "host_username"),
            textOrEmpty(data, "user", "username"),
            textOrEmpty(data, "sender", "username")
        );
        String text;
        ChatEventType type;
        switch (eventName) {
            case "SubscriptionEvent", "ChannelSubscriptionEvent" -> {
                int months = intOrZero(data, "months");
                type = ChatEventType.SUBSCRIBE;
                text = months > 1
                    ? Text.translatable("chatrix.event.kick.resub", months).getString()
                    : Text.translatable("chatrix.event.kick.sub").getString();
            }
            case "GiftedSubscriptionsEvent" -> {
                type = ChatEventType.GIFT;
                int count = giftedCount(data);
                String recipient = firstGiftedUsername(data);
                text = count <= 1 && !recipient.isBlank()
                    ? Text.translatable("chatrix.event.kick.subgift_one", recipient).getString()
                    : Text.translatable("chatrix.event.kick.subgift", Math.max(count, 1)).getString();
            }
            case "StreamHostEvent" -> {
                type = ChatEventType.RAID;
                text = Text.translatable(
                    "chatrix.event.kick.host",
                    Math.max(intOrZero(data, "number_viewers"), 0)
                ).getString();
            }
            case "FollowersUpdated" -> {
                if (username.isBlank()) {
                    return;
                }
                type = ChatEventType.FOLLOW;
                text = Text.translatable("chatrix.event.kick.follow").getString();
            }
            default -> {
                return;
            }
        }
        if (username.isBlank()) {
            username = "Viewer";
        }
        pipeline.process(IncomingChatMessage.builder(ChatPlatform.KICK)
            .authorLogin(username)
            .displayName(username)
            .text(text)
            .eventType(type)
            .build());
    }

    /** Kick identity.badges: subscriber, og, vip, moderator. Messages do not expose follower status. */
    private static ChatAuthorRoles rolesFromKickIdentity(JsonObject identity) {
        boolean subscriber = false;
        boolean vip = false;
        boolean moderator = false;
        boolean broadcaster = false;
        if (!identity.has("badges") || !identity.get("badges").isJsonArray()) {
            return ChatAuthorRoles.NONE;
        }
        for (JsonElement element : identity.getAsJsonArray("badges")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject badge = element.getAsJsonObject();
            String type = badge.has("type") && !badge.get("type").isJsonNull()
                ? badge.get("type").getAsString().toLowerCase(java.util.Locale.ROOT)
                : "";
            switch (type) {
                case "subscriber", "og", "founder" -> subscriber = true;
                case "vip" -> vip = true;
                case "moderator" -> moderator = true;
                case "broadcaster", "host" -> broadcaster = true;
                default -> { }
            }
        }
        return new ChatAuthorRoles(false, subscriber, vip, moderator, broadcaster);
    }

    private record KickIds(long channelId, long chatroomId) {}

    private static KickIds resolveIds(String slug) throws java.io.IOException {
        String body = ChatHttp.get("https://kick.com/api/v2/channels/" + slug);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("chatroom") || !json.get("chatroom").isJsonObject()) {
            if (json.has("error")) {
                throw new com.hristrix.chatrix.integration.chat.ChatConnectException(
                    "chatrix.chat.error.blocked",
                    "Kick API blocked the request: " + json.get("error").getAsString(),
                    "Kick"
                );
            }
            throw new com.hristrix.chatrix.integration.chat.ChatConnectException(
                "chatrix.chat.error.user_not_found",
                "Kick channel not found or has no chatroom: " + slug,
                "Kick", slug
            );
        }
        long chatroomId = json.getAsJsonObject("chatroom").get("id").getAsLong();
        long channelId = json.has("id") && json.get("id").isJsonPrimitive()
            ? json.get("id").getAsLong()
            : 0L;
        return new KickIds(channelId, chatroomId);
    }

    private static JsonObject parseData(JsonObject envelope) {
        if (!envelope.has("data") || envelope.get("data").isJsonNull()) {
            return null;
        }
        JsonElement data = envelope.get("data");
        if (data.isJsonObject()) {
            return data.getAsJsonObject();
        }
        if (!data.isJsonPrimitive()) {
            return null;
        }
        String dataStr = data.getAsString();
        if (dataStr == null || dataStr.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(dataStr);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortEventName(String event) {
        if (event == null || event.isBlank()) {
            return "";
        }
        int slash = Math.max(event.lastIndexOf('\\'), event.lastIndexOf('.'));
        return slash >= 0 ? event.substring(slash + 1) : event;
    }

    private static int giftedCount(JsonObject data) {
        if (data.has("gifted_usernames") && data.get("gifted_usernames").isJsonArray()) {
            return data.getAsJsonArray("gifted_usernames").size();
        }
        int total = intOrZero(data, "gifter_total");
        return Math.max(total, 0);
    }

    private static String firstGiftedUsername(JsonObject data) {
        if (!data.has("gifted_usernames") || !data.get("gifted_usernames").isJsonArray()) {
            return "";
        }
        for (JsonElement element : data.getAsJsonArray("gifted_usernames")) {
            if (element.isJsonPrimitive()) {
                String name = element.getAsString();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        }
        return "";
    }

    private static int intOrZero(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
            return 0;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            try {
                return Integer.parseInt(obj.get(key).getAsString());
            } catch (NumberFormatException ignoredAgain) {
                return 0;
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String textOrEmpty(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String textOrEmpty(JsonObject obj, String parent, String key) {
        if (!obj.has(parent) || !obj.get(parent).isJsonObject()) {
            return "";
        }
        return textOrEmpty(obj.getAsJsonObject(parent), key);
    }

    private static Integer parseHexColor(String color) {
        if (color == null || !color.startsWith("#") || color.length() != 7) {
            return null;
        }
        try {
            return Integer.parseInt(color.substring(1), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Changing channels interrupts the HTTP request as an IOException; this is cancellation, not failure. */
    private static boolean isInterruption(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public ChatPlatform getPlatform() {
        return ChatPlatform.KICK;
    }

    @Override
    public void connect(ChatMessagePipeline pipeline) {
        // Connected in constructor
    }

    @Override
    public void disconnect() {
        running = false;
        subscribed = false;
        SimpleWebSocket socket = webSocket;
        if (socket != null) {
            socket.disconnect();
        }
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isConnected() {
        SimpleWebSocket socket = webSocket;
        return running && subscribed && socket != null && socket.isConnected();
    }
}
