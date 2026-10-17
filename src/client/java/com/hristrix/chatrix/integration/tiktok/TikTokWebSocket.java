package com.hristrix.chatrix.integration.tiktok;

import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.integration.chat.ChatHttp;
import com.hristrix.chatrix.integration.tiktok.proto.TikTokProto;
import com.hristrix.chatrix.messages.MessageEmote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

final class TikTokWebSocket {
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    private static final long STALE_CHECK_INTERVAL_MS = 5_000L;
    private static final long STALE_TIMEOUT_MS = 60_000L;
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .build();

    interface ChatHandler {
        default void onConnected() {}
        void onChat(long msgId, String uniqueId, String displayName, String text,
            com.hristrix.chatrix.integration.chat.ChatAuthorRoles roles,
            List<MessageEmote> emotes);

        /** Gift event; {@code giftName} may be empty if TikTok supplies no details. */
        void onGift(long msgId, String uniqueId, String displayName, String giftName, int repeatCount);

        void onFollow(long msgId, String uniqueId, String displayName);

        void onSubscribe(long msgId, String uniqueId, String displayName);

        void onLiveEnded();
    }

    private final String roomId;
    private final String ttwid;
    private final ChatHandler handler;
    private volatile boolean running;
    private volatile WebSocket webSocket;
    private volatile boolean ready;
    private volatile Throwable failure;
    private java.util.concurrent.CompletableFuture<?> pendingSend = java.util.concurrent.CompletableFuture.completedFuture(null);
    private Thread maintenanceThread;

    TikTokWebSocket(String roomId, String ttwid, ChatHandler handler) {
        this.roomId = roomId;
        this.ttwid = ttwid;
        this.handler = handler;
    }

    void connect() {
        running = true;
        ready = false;
        failure = null;
        String url = TikTokWssUrl.build(roomId);
        HTTP_CLIENT.newWebSocketBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .header("Cookie", buildCookieHeader())
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://www.tiktok.com")
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .buildAsync(URI.create(url), new Listener())
            .whenComplete((ws, error) -> {
                if (error != null && running) {
                    fail(error);
                    ChatrixClient.LOGGER.warn("TikTok WSS handshake failed: {}", error.getMessage());
                }
            });
    }

    void disconnect() {
        running = false;
        ready = false;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
        Thread thread = maintenanceThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    boolean isRunning() {
        return running;
    }

    boolean isConnected() {
        return running && ready && webSocket != null;
    }

    Throwable getFailure() {
        return failure;
    }

    private void fail(Throwable error) {
        failure = error;
        ready = false;
        running = false;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.abort();
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();
        private final AtomicLong lastDataMs = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong lastHeartbeatSentMs = new AtomicLong(0L);

        @Override
        public void onOpen(WebSocket ws) {
            if (!running) {
                ws.abort();
                return;
            }
            webSocket = ws;
            sendBinary(ws, TikTokFrames.buildHeartbeat(roomId));
            sendBinary(ws, TikTokFrames.buildEnterRoom(roomId));
            lastHeartbeatSentMs.set(System.currentTimeMillis());
            startMaintenance(ws);
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            lastDataMs.set(System.currentTimeMillis());
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            if (binaryBuffer.size() + chunk.length > MAX_FRAME_BYTES) {
                binaryBuffer.reset();
                ChatrixClient.LOGGER.warn("TikTok WSS frame too large");
                fail(new IOException("TikTok chat frame exceeded the size limit"));
                ws.request(1);
                return null;
            }
            binaryBuffer.write(chunk, 0, chunk.length);

            if (last) {
                byte[] raw = binaryBuffer.toByteArray();
                binaryBuffer.reset();
                try {
                    processFrame(raw, ws);
                } catch (Exception e) {
                    ChatrixClient.LOGGER.debug("TikTok WSS frame skip: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            fail(new IOException("TikTok chat closed (" + statusCode + "): " + reason));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (running) {
                fail(error);
                ChatrixClient.LOGGER.warn("TikTok WSS error: {}", error.getMessage());
            }
        }

        private void startMaintenance(WebSocket ws) {
            maintenanceThread = Thread.ofVirtual().name("TikTokWss-" + roomId).start(() -> {
                while (running && webSocket == ws) {
                    try {
                        Thread.sleep(STALE_CHECK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (!running || webSocket != ws) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastDataMs.get() > STALE_TIMEOUT_MS) {
                        ChatrixClient.LOGGER.info("TikTok WSS stale for @{}, reconnecting", roomId);
                        fail(new IOException("TikTok chat stopped responding"));
                        return;
                    }
                    if (now - lastHeartbeatSentMs.get() >= HEARTBEAT_INTERVAL_MS) {
                        sendBinary(ws, TikTokFrames.buildHeartbeat(roomId));
                        lastHeartbeatSentMs.set(now);
                    }
                }
            });
        }
    }

    private void processFrame(byte[] raw, WebSocket ws) throws IOException {
        TikTokProto.ProtoMap frame;
        try {
            frame = TikTokProto.decode(raw);
        } catch (IllegalArgumentException e) {
            ChatrixClient.LOGGER.debug("TikTok WSS outer frame parse skip: {}", e.getMessage());
            return;
        }
        if (!"msg".equals(frame.getString(7))) {
            return;
        }

        byte[] payload = TikTokFrames.decompressIfGzipped(frame.getRawBytes(8));
        if (payload.length == 0) {
            return;
        }
        TikTokProto.ProtoMap response;
        try {
            response = TikTokProto.decode(payload);
        } catch (IllegalArgumentException e) {
            ChatrixClient.LOGGER.debug("TikTok WSS payload parse skip: {}", e.getMessage());
            return;
        }

        boolean needsAck = response.getBool(9);
        byte[] internalExt = response.getRawBytes(5);
        if (needsAck && internalExt.length > 0) {
            long logId = frame.getVarint(2);
            sendBinary(ws, TikTokFrames.buildAck(logId, internalExt));
        }

        for (TikTokProto.ProtoMap message : response.getRepeatedMessages(1)) {
            dispatchMessage(message);
        }
        if (running && !ready) {
            ready = true;
            handler.onConnected();
        }
    }

    private String buildCookieHeader() {
        String cookies = ChatHttp.cookieHeader("https://www.tiktok.com/");
        if (ttwid == null || ttwid.isBlank()) {
            return cookies;
        }
        if (cookies.contains("ttwid=")) {
            return cookies;
        }
        return cookies.isEmpty() ? "ttwid=" + ttwid : cookies + "; ttwid=" + ttwid;
    }

    private void dispatchMessage(TikTokProto.ProtoMap message) {
        try {
            String method = message.getString(1);
            byte[] payload = message.getRawBytes(2);
            if (methodEndsWith(method, "WebcastEmoteChatMessage")) {
                handleEmoteChat(payload, message.getVarint(3));
            } else if (methodEndsWith(method, "WebcastChatMessage")) {
                handleChat(payload, message.getVarint(3));
            } else if (methodEndsWith(method, "WebcastGiftMessage")) {
                handleGift(payload, message.getVarint(3));
            } else if (methodEndsWith(method, "WebcastSocialMessage")) {
                handleSocial(payload, message.getVarint(3));
            } else if (methodEndsWith(method, "WebcastControlMessage")) {
                TikTokProto.ProtoMap control = TikTokProto.decode(payload);
                if (control.getInt(2) == 3) {
                    handler.onLiveEnded();
                }
            }
        } catch (IllegalArgumentException e) {
            ChatrixClient.LOGGER.debug("TikTok WSS message parse skip: {}", e.getMessage());
        }
    }

    private void handleChat(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap chat = TikTokProto.decode(payload);
        String text = extractChatText(chat);
        if (text.isBlank()) {
            return;
        }

        UserIdentity user = extractUserSafe(chat);
        List<MessageEmote> emotes = TikTokEmoteParser.parseChatEmotes(chat, text);
        handler.onChat(
            resolveMsgId(chat, wrapperMsgId),
            user.uniqueId(),
            user.displayName(),
            text,
            user.roles(),
            emotes
        );
    }

    private void handleEmoteChat(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap chat = TikTokProto.decode(payload);
        TikTokProto.ProtoMap emoteDetails = chat.getMessage(3);
        MessageEmote emote = TikTokEmoteParser.parseStandaloneEmote(emoteDetails);
        if (emote == null) {
            return;
        }

        UserIdentity user = extractUserSafe(chat);
        handler.onChat(
            resolveMsgId(chat, wrapperMsgId),
            user.uniqueId(),
            user.displayName(),
            TikTokEmoteParser.standaloneEmoteText(),
            user.roles(),
            List.of(emote)
        );
    }

    private void handleGift(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap gift = TikTokProto.decode(payload);
        TikTokProto.ProtoMap details = gift.getMessage(15);
        // Gift streaks send an event per tap; show only the completed streak.
        if (details.getVarint(11) == 1L && gift.getVarint(9) == 0L) {
            return;
        }

        UserIdentity user = extractUserSafe(gift, 7);
        handler.onGift(
            resolveMsgId(gift, wrapperMsgId),
            user.uniqueId(),
            user.displayName(),
            readableText(details.getString(16)),
            (int) Math.max(1L, gift.getVarint(5))
        );
    }

    /** WebcastSocialMessage covers both follows and shares; distinguish them by displayType. */
    private void handleSocial(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap social = TikTokProto.decode(payload);
        TikTokProto.ProtoMap eventDetails = social.getMessage(1).getMessage(8);
        String hint = (eventDetails.getString(1) + ' ' + eventDetails.getString(2))
            .toLowerCase(java.util.Locale.ROOT);
        UserIdentity user = extractUserSafe(social, 2);
        long msgId = resolveMsgId(social, wrapperMsgId);
        if (hint.contains("follow")) {
            handler.onFollow(msgId, user.uniqueId(), user.displayName());
        } else if (hint.contains("subscribe")) {
            handler.onSubscribe(msgId, user.uniqueId(), user.displayName());
        }
    }

    private static long resolveMsgId(TikTokProto.ProtoMap chat, long wrapperMsgId) {
        try {
            return firstNonZero(chat.getMessage(1).getVarint(2), wrapperMsgId);
        } catch (RuntimeException ignored) {
            return wrapperMsgId;
        }
    }

    private static UserIdentity extractUserSafe(TikTokProto.ProtoMap chat) {
        return extractUserSafe(chat, 2);
    }

    private static UserIdentity extractUserSafe(TikTokProto.ProtoMap message, int userField) {
        try {
            return extractUser(message.getMessage(userField));
        } catch (RuntimeException ignored) {
            return new UserIdentity("Viewer", "Viewer", com.hristrix.chatrix.integration.chat.ChatAuthorRoles.NONE);
        }
    }

    private static boolean methodEndsWith(String method, String suffix) {
        return method != null && method.endsWith(suffix);
    }

    private static long firstNonZero(long... values) {
        for (long value : values) {
            if (value != 0L) {
                return value;
            }
        }
        return 0L;
    }

    /**
     * Comment text: field 3 (content) or nested CommentContent (field 29).
     * Newer TikTok clients sometimes store text outside string field 3.
     */
    private static String extractChatText(TikTokProto.ProtoMap chat) {
        String direct = readableText(chat.getString(3));
        if (!direct.isBlank()) {
            return direct;
        }
        String nestedFrom3 = "";
        try {
            nestedFrom3 = readableText(chat.getMessage(3).getString(1));
        } catch (RuntimeException ignored) {
            // Field 3 is a plain string, not a nested message.
        }
        if (!nestedFrom3.isBlank()) {
            return nestedFrom3;
        }
        try {
            return readableText(chat.getMessage(29).getString(1));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String readableText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int printable = 0;
        int length = value.length();
        for (int i = 0; i < length; ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isISOControl(cp) || cp == '\n' || cp == '\r' || cp == '\t') {
                printable++;
            }
        }
        if (printable * 4 < length * 3) {
            return "";
        }
        return value.trim();
    }

    private static UserIdentity extractUser(TikTokProto.ProtoMap user) {
        String displayName = firstNonBlank(user.getString(3), user.getString(38), "Viewer");
        String uniqueId = firstNonBlank(user.getString(38), user.getString(3));
        if (uniqueId.isBlank() && user.getVarint(1) != 0L) {
            uniqueId = Long.toString(user.getVarint(1));
        }
        if (uniqueId.isBlank()) {
            uniqueId = displayName;
        }
        return new UserIdentity(uniqueId, displayName, rolesFromTikTokUser(user));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** Uses a regular thread rather than a virtual thread because 1.20.1 runs on Java 17. */
    private Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private record UserIdentity(
        String uniqueId,
        String displayName,
        com.hristrix.chatrix.integration.chat.ChatAuthorRoles roles
    ) {}

    /**
     * Follow state: {@code FollowInfo.follow_status} (0 none, 1 follows, 2 friends).
     * LIVE subscriber and moderator roles come from the user's text badges.
     */
    private static com.hristrix.chatrix.integration.chat.ChatAuthorRoles rolesFromTikTokUser(
        TikTokProto.ProtoMap user
    ) {
        boolean follower = tikTokFollowStatus(user) >= 1L;
        String hints = collectTikTokUserRoleHints(user).toLowerCase(java.util.Locale.ROOT);
        boolean subscriber = containsAny(hints, "subscriber", "subscribe", "member", "fanclub", "fan_club");
        boolean moderator = containsAny(hints, "moderator", "admin");
        boolean broadcaster = containsAny(hints, "anchor", "host", "owner", "streamer");
        return new com.hristrix.chatrix.integration.chat.ChatAuthorRoles(
            follower,
            subscriber,
            false,
            moderator,
            broadcaster
        );
    }

    private static long tikTokFollowStatus(TikTokProto.ProtoMap user) {
        long status = user.getMessage(22).getVarint(3);
        if (status > 0L) {
            return status;
        }
        return user.getMessage(64).getVarint(3);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Collects textual role hints from the TikTok protobuf user. */
    private static String collectTikTokUserRoleHints(TikTokProto.ProtoMap user) {
        try {
            StringBuilder hints = new StringBuilder();
            for (int field : new int[] {9, 11, 22, 46, 61, 102}) {
                appendIfPresent(hints, user.getString(field));
                TikTokProto.ProtoMap nested = user.getMessage(field);
                appendIfPresent(hints, nested.getString(1));
                appendIfPresent(hints, nested.getString(2));
                appendIfPresent(hints, nested.getString(3));
                for (TikTokProto.ProtoMap badge : nested.getRepeatedMessages(1)) {
                    appendIfPresent(hints, badge.getString(1));
                    appendIfPresent(hints, badge.getString(2));
                    appendIfPresent(hints, badge.getString(3));
                }
            }
            return hints.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(value).append(' ');
        }
    }

    private synchronized void sendBinary(WebSocket ws, byte[] payload) {
        if (ws == null || ws.isOutputClosed()) {
            return;
        }
        pendingSend = pendingSend.thenCompose(ignored -> ws.sendBinary(ByteBuffer.wrap(payload), true)).exceptionally(error -> {
            if (running) {
                fail(error);
                ChatrixClient.LOGGER.warn("TikTok WSS send failed: {}", error.getMessage());
            }
            return null;
        });
    }
}
