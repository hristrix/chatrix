package com.hristrix.chatrix.integration.tiktok;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.Text;
import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.integration.chat.ChatConnection;
import com.hristrix.chatrix.integration.chat.ChatEventType;
import com.hristrix.chatrix.integration.chat.ChatHttp;
import com.hristrix.chatrix.integration.chat.ChatMessagePipeline;
import com.hristrix.chatrix.integration.chat.ChatPlatform;
import com.hristrix.chatrix.integration.chat.IncomingChatMessage;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TikTokChatClient implements ChatConnection {
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("\"roomId\"\\s*:\\s*\"?(\\d+)\"?");
    private static final Pattern ROOM_ID_ALT = Pattern.compile("room_id=(\\d+)");

    private static final int MAX_SEEN_MESSAGE_IDS = 5000;

    private final String username;
    private final ChatMessagePipeline pipeline;
    private volatile boolean running;
    private volatile Thread workerThread;
    private volatile TikTokWebSocket webSocket;
    private String roomId;
    private final Set<Long> seenMessageIds = new LinkedHashSet<>();

    public TikTokChatClient(String username, ChatMessagePipeline pipeline) {
        this.username = com.hristrix.chatrix.integration.chat.ChatChannelName.normalize(username, "tiktok");
        this.pipeline = pipeline;
        this.running = true;
        start();
    }

    private void start() {
        workerThread = new Thread(this::connectLoop, "TikTokChat-" + username);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void connectLoop() {
        while (running) {
            try {
                if (roomId == null) {
                    roomId = resolveRoomId(username);
                    ChatrixClient.LOGGER.info("TikTok room resolved for @{}: {}", username, roomId);
                }

                String ttwid = ChatHttp.fetchTtwidCookie(username);
                if (!running) {
                    break;
                }
                connectWebSocket(ttwid);

                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
                while (running && webSocket != null && webSocket.isRunning()) {
                    if (!webSocket.isConnected() && System.nanoTime() > deadline) {
                        throw new IOException("TikTok did not confirm the chat connection");
                    }
                    Thread.sleep(1000);
                }
                TikTokWebSocket ws = webSocket;
                if (running && ws != null && ws.getFailure() != null) {
                    throw new IOException("TikTok chat connection failed: " + ws.getFailure().getMessage(), ws.getFailure());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted() || isInterruption(e)) {
                    break;
                }
                ChatrixClient.LOGGER.warn("TikTok chat error for @{}: {}", username, e.getMessage());
                com.hristrix.chatrix.integration.chat.ChatErrorReporter.report(ChatPlatform.TIKTOK, e);
            } finally {
                disconnectWebSocket();
                roomId = null;
                seenMessageIds.clear();
            }
            if (running) {
                sleepQuietly(10000);
            }
        }
    }

    private void connectWebSocket(String ttwid) {
        String currentRoomId = roomId;
        webSocket = new TikTokWebSocket(currentRoomId, ttwid, new TikTokWebSocket.ChatHandler() {
            @Override
            public void onConnected() {
                if (running) {
                    com.hristrix.chatrix.integration.chat.ChatErrorReporter.clear(ChatPlatform.TIKTOK);
                    com.hristrix.chatrix.integration.chat.ChatStatusNotifier.showTranslatableSuccess(
                        "chatrix.status.connected_platform", "TikTok", "@" + username);
                    ChatrixClient.LOGGER.info("TikTok chat feed confirmed for @{}", username);
                }
            }
            @Override
            public void onChat(long msgId, String uniqueId, String displayName, String text,
                com.hristrix.chatrix.integration.chat.ChatAuthorRoles roles,
                java.util.List<com.hristrix.chatrix.messages.MessageEmote> emotes) {
                if (!markSeen(msgId)) {
                    return;
                }
                pipeline.process(IncomingChatMessage.builder(ChatPlatform.TIKTOK)
                    .authorLogin(uniqueId)
                    .displayName(displayName)
                    .text(text)
                    .roles(roles)
                    .emotes(emotes)
                    .build());
            }

            @Override
            public void onGift(long msgId, String uniqueId, String displayName, String giftName, int repeatCount) {
                if (!markSeen(msgId)) {
                    return;
                }
                Text text = giftName == null || giftName.isBlank()
                    ? Text.translatable("chatrix.event.tiktok.gift_unknown")
                    : Text.translatable("chatrix.event.tiktok.gift", giftName, repeatCount);
                submitEvent(uniqueId, displayName, text, ChatEventType.GIFT);
            }

            @Override
            public void onFollow(long msgId, String uniqueId, String displayName) {
                if (!markSeen(msgId)) {
                    return;
                }
                submitEvent(
                    uniqueId,
                    displayName,
                    Text.translatable("chatrix.event.tiktok.follow"),
                    ChatEventType.FOLLOW
                );
            }

            @Override
            public void onSubscribe(long msgId, String uniqueId, String displayName) {
                if (!markSeen(msgId)) {
                    return;
                }
                submitEvent(
                    uniqueId,
                    displayName,
                    Text.translatable("chatrix.event.tiktok.subscribe"),
                    ChatEventType.SUBSCRIBE
                );
            }

            private void submitEvent(String uniqueId, String displayName, Text text, ChatEventType eventType) {
                pipeline.process(IncomingChatMessage.builder(ChatPlatform.TIKTOK)
                    .authorLogin(uniqueId)
                    .displayName(displayName)
                    .text(text.getString())
                    .eventType(eventType)
                    .build());
            }

            @Override
            public void onLiveEnded() {
                ChatrixClient.LOGGER.info("TikTok live ended for @{}", username);
                roomId = null;
                seenMessageIds.clear();
                disconnectWebSocket();
            }
        });
        webSocket.connect();
    }

    /** Deduplicates messages and events by msgId; false means already seen. */
    private boolean markSeen(long msgId) {
        if (msgId != 0 && !seenMessageIds.add(msgId)) {
            return false;
        }
        while (seenMessageIds.size() > MAX_SEEN_MESSAGE_IDS) {
            var iterator = seenMessageIds.iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    private void disconnectWebSocket() {
        TikTokWebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.disconnect();
        }
    }

    private static String resolveRoomId(String user) throws IOException {
        // Primary method: api-live; the /live page is now protected by a WAF interstitial.
        // sourceType=54 is required; otherwise the API returns params_error.
        String apiUrl = "https://www.tiktok.com/api-live/user/room/?aid=1988&sourceType=54&uniqueId="
            + URLEncoder.encode(user, StandardCharsets.UTF_8);
        String apiBody = ChatHttp.get(apiUrl, "https://www.tiktok.com/");
        String roomId = null;
        Integer status = null;
        try {
            JsonObject json = JsonParser.parseString(apiBody).getAsJsonObject();
            if (json.has("data") && json.get("data").isJsonObject()) {
                JsonObject data = json.getAsJsonObject("data");
                if (data.has("user") && data.get("user").isJsonObject()) {
                    JsonObject userObj = data.getAsJsonObject("user");
                    if (userObj.has("roomId") && !userObj.get("roomId").isJsonNull()) {
                        roomId = userObj.get("roomId").getAsString();
                    }
                    if (userObj.has("status") && !userObj.get("status").isJsonNull()) {
                        status = userObj.get("status").getAsInt();
                    }
                }
            }
        } catch (Exception ignored) {
            // Non-JSON response: try the fallback below.
        }

        // Status 2 means live; status 4 means ended or offline.
        if (status != null && status != 2) {
            throw new com.hristrix.chatrix.integration.chat.ChatConnectException(
                "chatrix.chat.error.not_live",
                "@" + user + " is not live right now (status " + status + ")",
                "TikTok", "@" + user
            );
        }
        if (roomId != null && !roomId.isBlank() && !"0".equals(roomId)) {
            return roomId;
        }

        // Fallback: parse the /live HTML page, which may be protected by a WAF.
        String liveUrl = "https://www.tiktok.com/@" + URLEncoder.encode(user, StandardCharsets.UTF_8) + "/live";
        String html = ChatHttp.get(liveUrl, "https://www.tiktok.com/");
        roomId = firstMatch(ROOM_ID_PATTERN, html);
        if (roomId == null) {
            roomId = firstMatch(ROOM_ID_ALT, html);
        }
        if (roomId == null || roomId.isBlank()) {
            throw new com.hristrix.chatrix.integration.chat.ChatConnectException(
                "chatrix.chat.error.no_live_room",
                "No live room found for @" + user + " (user not found or not live)",
                "TikTok", "@" + user
            );
        }
        return roomId;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Changing usernames interrupts the HTTP request as an IOException; this is cancellation, not failure. */
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
        return ChatPlatform.TIKTOK;
    }

    @Override
    public void connect(ChatMessagePipeline pipeline) {
        // Connected in constructor
    }

    @Override
    public void disconnect() {
        running = false;
        disconnectWebSocket();
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isConnected() {
        TikTokWebSocket ws = webSocket;
        return running && ws != null && ws.isConnected();
    }
}
