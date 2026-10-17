package com.hristrix.chatrix.integration.youtube;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hristrix.chatrix.ChatrixClient;
import net.minecraft.text.Text;
import com.hristrix.chatrix.integration.chat.ChatAuthorRoles;
import com.hristrix.chatrix.integration.chat.ChatConnection;
import com.hristrix.chatrix.integration.chat.ChatEventType;
import com.hristrix.chatrix.integration.chat.ChatHttp;
import com.hristrix.chatrix.integration.chat.ChatMessagePipeline;
import com.hristrix.chatrix.integration.chat.ChatPlatform;
import com.hristrix.chatrix.integration.chat.IncomingChatMessage;
import com.hristrix.chatrix.messages.MessageEmote;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeChatClient implements ChatConnection {
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"");
    private static final Pattern LIVE_CHAT_ID_PATTERN = Pattern.compile("\"liveChatId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile(
        "(?:youtube\\.com/watch\\?.*?v=|youtu\\.be/|youtube\\.com/live/)([a-zA-Z0-9_-]{11})"
    );
    private static final Pattern BARE_VIDEO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");
    private static final Pattern LIVE_CHAT_RENDERER = Pattern.compile("\"liveChatRenderer\"\\s*:\\s*\\{");
    private static final Pattern LIVE_CHAT_REPLAY = Pattern.compile("\"isReplay\"\\s*:\\s*true");
    private static final Pattern RELOAD_CONTINUATION = Pattern.compile(
        "\"reloadContinuationData\"\\s*:\\s*\\{\\s*\"continuation\"\\s*:\\s*\"([^\"]+)\""
    );
    private static final Pattern JSON_TRUE_FLAG = Pattern.compile(
        "\"(?:isLiveNow|isLiveContent|isLive|isLiveBroadcast)\"\\s*:\\s*true"
    );
    private static final Pattern JSON_FALSE_LIVE_FLAG = Pattern.compile(
        "\"(?:isLiveNow|isLiveContent|isLive|isLiveBroadcast)\"\\s*:\\s*false"
    );
    private static final Pattern JSON_UPCOMING = Pattern.compile("\"isUpcoming\"\\s*:\\s*true");
    private static final Pattern CURRENT_VIDEO_ID = Pattern.compile(
        "\"currentVideoEndpoint\"\\s*:\\s*\\{[^}]*\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\""
    );
    private static final Pattern LIVE_VIDEO_ID = Pattern.compile(
        "\"isLiveNow\"\\s*:\\s*true[\\s\\S]{0,1200}?\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\""
    );
    private static final String INNERTUBE_CLIENT_VERSION = "2.20250201.00.00";
    private static final String LIVE_CHAT_API = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat";
    private static final String PLAYER_API = "https://www.youtube.com/youtubei/v1/player";
    private static final int MAX_SEEN_MESSAGE_IDS = 5000;

    private final String channelLabel;
    private final Source source;
    private final ChatMessagePipeline pipeline;
    private final Set<String> seenMessageIds = new HashSet<>();
    private volatile boolean running;
    private volatile boolean connected;
    private volatile Thread workerThread;
    private String continuation;
    private String liveChatId;
    private String activeVideoId;
    // The first response contains historical chat, about 70 messages; skip it.
    private boolean initialBacklogConsumed;

    public YouTubeChatClient(String channelInput, ChatMessagePipeline pipeline) {
        this.source = parseSource(channelInput);
        this.channelLabel = source.label();
        this.pipeline = pipeline;
        this.running = true;
        workerThread = new Thread(this::pollLoop, "YouTubeChat-" + channelLabel);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private record Source(Kind kind, String label, String handle, String videoId) {
        enum Kind { HANDLE, VIDEO_ID }

        static Source handle(String handle) {
            return new Source(Kind.HANDLE, handle, handle, null);
        }

        static Source videoId(String videoId) {
            return new Source(Kind.VIDEO_ID, videoId, null, videoId);
        }
    }

    private static Source parseSource(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Source.handle("");
        }

        Matcher urlMatcher = VIDEO_URL_PATTERN.matcher(trimmed);
        if (urlMatcher.find()) {
            return Source.videoId(urlMatcher.group(1));
        }

        if (BARE_VIDEO_ID_PATTERN.matcher(trimmed).matches()) {
            return Source.videoId(trimmed);
        }

        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        } else if (trimmed.startsWith("https://www.youtube.com/@")) {
            trimmed = trimmed.substring("https://www.youtube.com/@".length());
            int slash = trimmed.indexOf('/');
            trimmed = slash >= 0 ? trimmed.substring(0, slash) : trimmed;
        }

        return Source.handle(trimmed);
    }

    private void pollLoop() {
        while (running) {
            try {
                if (liveChatId == null && continuation == null) {
                    resolveLiveChat();
                }
                if (!running) {
                    break;
                }
                if (liveChatId == null && continuation == null) {
                    sleepQuietly(10000);
                    continue;
                }
                pollOnce();
                sleepQuietly(3000);
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (e instanceof com.hristrix.chatrix.integration.chat.ChatConnectException cce
                    && isOfflineError(cce)) {
                    ChatrixClient.LOGGER.info("YouTube {} offline: {}", channelLabel, e.getMessage());
                    com.hristrix.chatrix.integration.chat.ChatErrorReporter.report(ChatPlatform.YOUTUBE, e);
                } else {
                    ChatrixClient.LOGGER.warn("YouTube chat error for {}: {}", channelLabel, e.getMessage());
                    com.hristrix.chatrix.integration.chat.ChatErrorReporter.report(ChatPlatform.YOUTUBE, e);
                }
                resetSession();
                sleepQuietly(10000);
            }
        }
    }

    private void resetSession() {
        connected = false;
        liveChatId = null;
        continuation = null;
        activeVideoId = null;
        seenMessageIds.clear();
        initialBacklogConsumed = false;
    }

    private void resolveLiveChat() throws IOException {
        String videoId = resolveVideoId();
        activeVideoId = videoId;
        String watchUrl = watchUrlFor(videoId);
        String playerResponse = fetchPlayerResponse(videoId, watchUrl);
        boolean live = isLiveBroadcast(playerResponse);

        String chatId = firstMatch(LIVE_CHAT_ID_PATTERN, playerResponse);
        if (chatId != null) {
            liveChatId = chatId;
            continuation = null;
            ChatrixClient.LOGGER.info(
                "YouTube live chat resolved for {} via player API: {}",
                channelLabel,
                liveChatId
            );
            return;
        }

        String watchHtml = ChatHttp.get(watchUrl, watchUrl);
        String chatContinuation = fetchLiveChatContinuationFromWatchPage(watchHtml);
        if (chatContinuation != null) {
            liveChatId = null;
            continuation = chatContinuation;
            ChatrixClient.LOGGER.info(
                "YouTube live chat resolved for {} via watch page (video {})",
                channelLabel,
                videoId
            );
            return;
        }

        if (!live) {
            throw offlineException(videoId);
        }

        String chatTarget = source.kind() == Source.Kind.HANDLE
            ? "@" + source.handle()
            : videoId;
        throw new com.hristrix.chatrix.integration.chat.ChatConnectException(
            "chatrix.chat.error.no_live_chat",
            "Live chat not available for " + chatTarget,
            "YouTube", chatTarget
        );
    }

    private static String fetchPlayerResponse(String videoId, String watchUrl) throws IOException {
        JsonObject body = innertubeBody();
        body.addProperty("videoId", videoId);
        return ChatHttp.postJson(PLAYER_API, body.toString(), youtubeHeaders(watchUrl));
    }

    /** Only explicit currently-live flags count; past VODs with {@code liveBroadcastDetails} do not. */
    private static boolean isLiveBroadcast(String playerResponse) {
        if (JSON_UPCOMING.matcher(playerResponse).find()) {
            return false;
        }
        if (JSON_FALSE_LIVE_FLAG.matcher(playerResponse).find()) {
            return false;
        }
        return JSON_TRUE_FLAG.matcher(playerResponse).find();
    }

    private static boolean htmlIndicatesLiveStream(String html) {
        if (JSON_TRUE_FLAG.matcher(html).find()
            || html.contains("BADGE_STYLE_TYPE_LIVE_NOW")
            || html.contains("\"style\":\"LIVE\"")) {
            return true;
        }
        return false;
    }

    private com.hristrix.chatrix.integration.chat.ChatConnectException offlineException(String videoId) {
        if (source.kind() == Source.Kind.HANDLE) {
            return new com.hristrix.chatrix.integration.chat.ChatConnectException(
                "chatrix.chat.error.no_live_room",
                "No live stream for @" + source.handle(),
                "YouTube", "@" + source.handle()
            );
        }
        return new com.hristrix.chatrix.integration.chat.ChatConnectException(
            "chatrix.chat.error.not_live",
            "Stream is not live: " + videoId,
            "YouTube", videoId
        );
    }

    private static boolean isOfflineError(com.hristrix.chatrix.integration.chat.ChatConnectException error) {
        String key = error.getTranslationKey();
        return "chatrix.chat.error.no_live_room".equals(key)
            || "chatrix.chat.error.not_live".equals(key);
    }

    private String resolveVideoId() throws IOException {
        if (source.kind() == Source.Kind.VIDEO_ID) {
            return source.videoId();
        }

        String url = "https://www.youtube.com/@"
            + URLEncoder.encode(source.handle(), StandardCharsets.UTF_8) + "/live";
        String html = ChatHttp.get(url, "https://www.youtube.com/");
        if (!htmlIndicatesLiveStream(html)) {
            throw new com.hristrix.chatrix.integration.chat.ChatConnectException(
                "chatrix.chat.error.no_live_room",
                "No live stream found for @" + source.handle(),
                "YouTube", "@" + source.handle()
            );
        }
        String videoId = extractLiveVideoId(html);
        if (videoId == null) {
            throw new com.hristrix.chatrix.integration.chat.ChatConnectException(
                "chatrix.chat.error.no_live_room",
                "No live stream found for @" + source.handle(),
                "YouTube", "@" + source.handle()
            );
        }
        return videoId;
    }

    private static String fetchLiveChatContinuationFromWatchPage(String watchHtml) {
        int rendererIndex = indexOfPattern(LIVE_CHAT_RENDERER, watchHtml);
        if (rendererIndex < 0) {
            return null;
        }

        int chunkEnd = Math.min(rendererIndex + 12000, watchHtml.length());
        String chunk = watchHtml.substring(rendererIndex, chunkEnd);

        if (LIVE_CHAT_REPLAY.matcher(chunk).find()) {
            return null;
        }

        Matcher continuationMatcher = RELOAD_CONTINUATION.matcher(chunk);
        return continuationMatcher.find() ? continuationMatcher.group(1) : null;
    }

    private void pollOnce() throws IOException {
        String watchUrl = activeVideoId != null ? watchUrlFor(activeVideoId) : "https://www.youtube.com/";
        JsonObject body = innertubeBody();
        if (continuation != null) {
            body.addProperty("continuation", continuation);
        } else {
            body.addProperty("activeLiveChatId", liveChatId);
        }

        String raw = ChatHttp.postJson(LIVE_CHAT_API, body.toString(), youtubeHeaders(watchUrl));
        JsonObject response = JsonParser.parseString(raw).getAsJsonObject();
        String next = validatedContinuation(response);
        if (!running) {
            return;
        }
        continuation = next;
        if (!connected) {
            connected = true;
            com.hristrix.chatrix.integration.chat.ChatErrorReporter.clear(ChatPlatform.YOUTUBE);
            com.hristrix.chatrix.integration.chat.ChatStatusNotifier.showTranslatableSuccess(
                "chatrix.status.connected_platform", "YouTube", channelLabel);
            ChatrixClient.LOGGER.info("YouTube live chat polling confirmed for {}", channelLabel);
        }
        boolean deliver = initialBacklogConsumed;
        if (deliver) {
            List<IncomingChatMessage> batch = new ArrayList<>();
            collectMessages(response, true, batch);
            if (batch.size() == 1) {
                pipeline.process(batch.get(0));
            } else if (!batch.isEmpty()) {
                pipeline.processYouTubeBatch(batch);
            }
        } else {
            collectMessages(response, false, null);
        }
        initialBacklogConsumed = true;
    }

    private static JsonObject innertubeBody() {
        return JsonParser.parseString("""
            {"context":{"client":{"clientName":"WEB","clientVersion":"2.20250201.00.00","hl":"en","gl":"US"}}}
            """.trim()).getAsJsonObject();
    }

    private static String watchUrlFor(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private static Map<String, String> youtubeHeaders(String watchUrl) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Origin", "https://www.youtube.com");
        headers.put("Referer", watchUrl);
        headers.put("X-YouTube-Client-Name", "1");
        headers.put("X-YouTube-Client-Version", INNERTUBE_CLIENT_VERSION);
        return headers;
    }

    private static String validatedContinuation(JsonObject response) throws IOException {
        if (response.has("error") || !response.has("continuationContents")
            || !response.get("continuationContents").isJsonObject()) {
            throw new IOException("YouTube did not accept the live chat polling request");
        }
        JsonObject contents = response.getAsJsonObject("continuationContents");
        if (!contents.has("liveChatContinuation") || !contents.get("liveChatContinuation").isJsonObject()) {
            throw new IOException("YouTube live chat is unavailable or has ended");
        }
        String next = findTimedContinuation(contents.getAsJsonObject("liveChatContinuation"));
        if (next == null || next.isBlank()) {
            throw new IOException("YouTube live chat returned no continuation; the stream may have ended");
        }
        return next;
    }

    private static String findTimedContinuation(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String continuationType : List.of("timedContinuationData", "invalidationContinuationData", "reloadContinuationData")) {
              if (obj.has(continuationType) && obj.get(continuationType).isJsonObject()) {
                JsonObject timed = obj.getAsJsonObject(continuationType);
                if (timed.has("continuation") && timed.get("continuation").isJsonPrimitive()) {
                    return timed.get("continuation").getAsString();
                }
              }
            }
            for (String key : obj.keySet()) {
                String found = findTimedContinuation(obj.get(key));
                if (found != null) {
                    return found;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String found = findTimedContinuation(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void collectMessages(JsonElement element, boolean deliver, List<IncomingChatMessage> batch) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("liveChatTextMessageRenderer")) {
                handleTextMessage(obj.getAsJsonObject("liveChatTextMessageRenderer"), deliver, batch);
            } else if (obj.has("liveChatPaidMessageRenderer")) {
                handlePaidMessage(obj.getAsJsonObject("liveChatPaidMessageRenderer"), deliver, batch);
            } else if (obj.has("liveChatPaidStickerRenderer")) {
                handlePaidSticker(obj.getAsJsonObject("liveChatPaidStickerRenderer"), deliver, batch);
            } else if (obj.has("liveChatMembershipItemRenderer")) {
                handleMembership(obj.getAsJsonObject("liveChatMembershipItemRenderer"), deliver, batch);
            } else if (obj.has("liveChatSponsorshipsGiftPurchaseAnnouncementRenderer")) {
                handleGiftPurchase(obj.getAsJsonObject("liveChatSponsorshipsGiftPurchaseAnnouncementRenderer"), deliver, batch);
            } else if (obj.has("liveChatSponsorshipsGiftRedemptionAnnouncementRenderer")) {
                handleGiftRedemption(obj.getAsJsonObject("liveChatSponsorshipsGiftRedemptionAnnouncementRenderer"), deliver, batch);
            }
            for (String key : obj.keySet()) {
                collectMessages(obj.get(key), deliver, batch);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectMessages(child, deliver, batch);
            }
        }
    }

    private void handleTextMessage(JsonObject renderer, boolean deliver, List<IncomingChatMessage> batch) {
        if (!rememberRenderer(renderer) || !deliver) {
            return;
        }

        YouTubeEmoteParser.Parsed parsed = YouTubeEmoteParser.parse(renderer);
        if (parsed.text().isBlank()) {
            return;
        }
        emitYouTube(renderer, parsed.text(), parsed.emotes(), ChatEventType.CHAT, batch);
    }

    private void handlePaidMessage(JsonObject renderer, boolean deliver, List<IncomingChatMessage> batch) {
        if (!rememberRenderer(renderer) || !deliver) {
            return;
        }
        YouTubeEmoteParser.Parsed parsed = YouTubeEmoteParser.parse(renderer);
        String amount = YouTubeEmoteParser.plainText(renderer, "purchaseAmountText");
        String text = parsed.text().isBlank()
            ? Text.translatable("chatrix.event.youtube.superchat_plain", firstNonBlank(amount, "?")).getString()
            : amount.isBlank()
                ? parsed.text()
                : Text.translatable("chatrix.event.youtube.superchat", amount, parsed.text()).getString();
        emitYouTube(renderer, text, parsed.emotes(), ChatEventType.GIFT, batch);
    }

    private void handlePaidSticker(JsonObject renderer, boolean deliver, List<IncomingChatMessage> batch) {
        if (!rememberRenderer(renderer) || !deliver) {
            return;
        }
        String amount = YouTubeEmoteParser.plainText(renderer, "purchaseAmountText");
        String text = Text.translatable(
            "chatrix.event.youtube.supersticker",
            firstNonBlank(amount, "?")
        ).getString();
        emitYouTube(renderer, text, List.of(), ChatEventType.GIFT, batch);
    }

    private void handleMembership(JsonObject renderer, boolean deliver, List<IncomingChatMessage> batch) {
        if (!rememberRenderer(renderer) || !deliver) {
            return;
        }
        String detail = firstNonBlank(
            YouTubeEmoteParser.plainText(renderer, "headerSubtext"),
            YouTubeEmoteParser.plainText(renderer, "headerPrimaryText"),
            YouTubeEmoteParser.parse(renderer).text()
        );
        String text = detail.isBlank()
            ? Text.translatable("chatrix.event.youtube.member").getString()
            : Text.translatable("chatrix.event.youtube.member_milestone", detail).getString();
        emitYouTube(renderer, text, List.of(), ChatEventType.SUBSCRIBE, batch);
    }

    private void handleGiftPurchase(JsonObject renderer, boolean deliver, List<IncomingChatMessage> batch) {
        if (!rememberRenderer(renderer) || !deliver) {
            return;
        }
        JsonObject header = sponsorshipHeader(renderer);
        String primary = YouTubeEmoteParser.plainText(header, "primaryText");
        String text = primary.isBlank()
            ? Text.translatable("chatrix.event.youtube.gift_purchase_unknown").getString()
            : primary;
        emitYouTube(header != null ? header : renderer, text, List.of(), ChatEventType.GIFT, batch);
    }

    private void handleGiftRedemption(JsonObject renderer, boolean deliver, List<IncomingChatMessage> batch) {
        if (!rememberRenderer(renderer) || !deliver) {
            return;
        }
        String detail = firstNonBlank(
            YouTubeEmoteParser.parse(renderer).text(),
            YouTubeEmoteParser.plainText(renderer, "headerSubtext")
        );
        String text = detail.isBlank()
            ? Text.translatable("chatrix.event.youtube.gift_redeem").getString()
            : detail;
        emitYouTube(renderer, text, List.of(), ChatEventType.GIFT, batch);
    }

    private static JsonObject sponsorshipHeader(JsonObject renderer) {
        if (renderer == null || !renderer.has("header") || !renderer.get("header").isJsonObject()) {
            return renderer;
        }
        JsonObject header = renderer.getAsJsonObject("header");
        if (header.has("liveChatSponsorshipsHeaderRenderer")
            && header.get("liveChatSponsorshipsHeaderRenderer").isJsonObject()) {
            return header.getAsJsonObject("liveChatSponsorshipsHeaderRenderer");
        }
        return header;
    }

    private boolean rememberRenderer(JsonObject renderer) {
        if (renderer.has("id") && renderer.get("id").isJsonPrimitive()) {
            String messageId = renderer.get("id").getAsString();
            if (!seenMessageIds.add(messageId)) {
                return false;
            }
            if (seenMessageIds.size() > MAX_SEEN_MESSAGE_IDS) {
                seenMessageIds.clear();
                seenMessageIds.add(messageId);
            }
        }
        return true;
    }

    private void emitYouTube(
        JsonObject renderer,
        String text,
        List<MessageEmote> emotes,
        ChatEventType eventType,
        List<IncomingChatMessage> batch
    ) {
        if (text == null || text.isBlank()) {
            return;
        }
        String authorName = YouTubeEmoteParser.authorName(renderer);
        String authorChannelId = renderer.has("authorExternalChannelId")
            && renderer.get("authorExternalChannelId").isJsonPrimitive()
            ? renderer.get("authorExternalChannelId").getAsString()
            : "";
        ChatAuthorRoles roles = renderer.has("authorBadges")
            ? rolesFromYouTubeBadges(renderer.get("authorBadges"))
            : ChatAuthorRoles.NONE;
        batch.add(IncomingChatMessage.builder(ChatPlatform.YOUTUBE)
            .authorLogin(authorChannelId.isBlank() ? authorName : authorChannelId)
            .displayName(authorName.isBlank() ? "Viewer" : authorName)
            .text(text)
            .emotes(emotes)
            .roles(roles)
            .sourceTimestampMicros(parseTimestampMicros(renderer))
            .eventType(eventType)
            .build());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Long parseTimestampMicros(JsonObject renderer) {
        if (!renderer.has("timestampUsec") || !renderer.get("timestampUsec").isJsonPrimitive()) {
            return null;
        }
        try {
            return Long.parseLong(renderer.get("timestampUsec").getAsString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int indexOfPattern(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Gets the current live video's videoId rather than an arbitrary recommendation. */
    private static String extractLiveVideoId(String html) {
        String videoId = firstMatch(CURRENT_VIDEO_ID, html);
        if (videoId != null) {
            return videoId;
        }
        videoId = firstMatch(LIVE_VIDEO_ID, html);
        if (videoId != null) {
            return videoId;
        }
        return firstMatch(VIDEO_ID_PATTERN, html);
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
        return ChatPlatform.YOUTUBE;
    }

    /** Member, Moderator, and Owner from {@code authorBadges}; YouTube chat does not expose followers or VIPs. */
    private static ChatAuthorRoles rolesFromYouTubeBadges(JsonElement badges) {
        boolean member = false;
        boolean moderator = false;
        boolean owner = false;
        if (badges != null && badges.isJsonArray()) {
            for (JsonElement element : badges.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject renderer = element.getAsJsonObject();
                if (renderer.has("liveChatAuthorBadgeRenderer") && renderer.get("liveChatAuthorBadgeRenderer").isJsonObject()) {
                    renderer = renderer.getAsJsonObject("liveChatAuthorBadgeRenderer");
                }
                String iconType = "";
                if (renderer.has("icon") && renderer.get("icon").isJsonObject()) {
                    JsonObject icon = renderer.getAsJsonObject("icon");
                    if (icon.has("iconType") && !icon.get("iconType").isJsonNull()) {
                        iconType = icon.get("iconType").getAsString();
                    }
                }
                String tooltip = renderer.has("tooltip") && !renderer.get("tooltip").isJsonNull()
                    ? renderer.get("tooltip").getAsString()
                    : "";
                String hay = (iconType + " " + tooltip).toLowerCase(java.util.Locale.ROOT);
                if (hay.contains("owner")) {
                    owner = true;
                }
                if (hay.contains("moderator")) {
                    moderator = true;
                }
                if (hay.contains("member")) {
                    member = true;
                }
            }
        }
        return new ChatAuthorRoles(false, member, false, moderator, owner);
    }

    @Override
    public void connect(ChatMessagePipeline pipeline) {
        // Connected in constructor
    }

    @Override
    public void disconnect() {
        running = false;
        connected = false;
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isConnected() {
        return running && connected;
    }
}
