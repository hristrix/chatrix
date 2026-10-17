package com.hristrix.chatrix.messages;

import com.hristrix.chatrix.ChatrixClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 7TV emote manager: loads global and channel sets,
 * and provides lookup by emote name.
 */
public final class SevenTVEmoteProvider {
    private static final Gson GSON = new Gson();

    // name → emoteId
    private static final Map<String, String> GLOBAL_EMOTES = new ConcurrentHashMap<>();
    private static final Map<String, String> CHANNEL_EMOTES = new ConcurrentHashMap<>();
    private static final Map<String, String> GLOBAL_EMOTES_LOWER = new ConcurrentHashMap<>();
    private static final Map<String, String> CHANNEL_EMOTES_LOWER = new ConcurrentHashMap<>();

    private static final AtomicBoolean GLOBALS_LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean GLOBALS_LOADING = new AtomicBoolean(false);
    private static volatile String currentChannelName = null;
    private static volatile String currentTwitchRoomId = null;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Chatrix-7TV-Loader");
        t.setDaemon(true);
        return t;
    });

    private SevenTVEmoteProvider() {}

    /**
     * Initializes global emotes and channel emotes if a channel is specified.
     * Called by TwitchChatClient when connecting to a channel.
     */
    public static void init(String channelName) {
        loadGlobalEmotes();
        if (channelName != null && !channelName.isBlank()) {
            loadChannelEmotes(channelName);
        }
    }

    /**
     * Initializes using a Twitch room ID, the preferred path for 7TV v3.
     */
    public static void init(String channelName, String twitchRoomId) {
        loadGlobalEmotes();
        if (twitchRoomId != null && !twitchRoomId.isBlank()) {
            loadChannelEmotesByRoomId(channelName, twitchRoomId);
        } else if (channelName != null && !channelName.isBlank()) {
            loadChannelEmotes(channelName);
        }
    }

    /**
     * Loads the global 7TV emote set once.
     */
    public static void loadGlobalEmotes() {
        if (GLOBALS_LOADED.get() || !GLOBALS_LOADING.compareAndSet(false, true)) return;
        EXECUTOR.execute(() -> {
            boolean loadedSuccessfully = false;
            try {
                // 7TV v3 API: global set.
                JsonObject json = httpGetJson("https://7tv.io/v3/emote-sets/global");
                if (json == null) return;
                JsonArray emotes = json.getAsJsonArray("emotes");
                if (emotes == null) return;
                for (JsonElement el : emotes) {
                    JsonObject emoteObj = el.getAsJsonObject();
                    String name = emoteObj.has("name") ? emoteObj.get("name").getAsString() : null;
                    String id = emoteObj.has("id") ? emoteObj.get("id").getAsString() : null;
                    if (name != null && id != null && !name.isBlank()) {
                        GLOBAL_EMOTES.put(name, id);
                        GLOBAL_EMOTES_LOWER.put(name.toLowerCase(), id);
                    }
                }
                GLOBALS_LOADED.set(true);
                loadedSuccessfully = true;
            } catch (Exception e) {
                ChatrixClient.LOGGER.warn("Failed to load global 7TV emotes: {}", e.getMessage());
            } finally {
                if (!loadedSuccessfully) {
                    GLOBALS_LOADING.set(false); // Allow another load attempt after failure.
                }
            }
        });
    }

    /**
     * Loads the channel's 7TV emote set.
     * Uses login lookup: /v3/users/twitch/{login}.
     */
    public static void loadChannelEmotes(String channelName) {
        if (channelName == null || channelName.isBlank()) return;
        String lower = channelName.toLowerCase();
        if (lower.equals(currentChannelName) && !CHANNEL_EMOTES.isEmpty()) return; // Already loaded

        EXECUTOR.execute(() -> {
            try {
                Map<String, String> freshChannelEmotes = new java.util.HashMap<>();

                // 7TV API: look up the user by Twitch login.
                JsonObject userJson = httpGetJson("https://7tv.io/v3/users/twitch/" + lower);
                if (userJson == null) {
                    return;
                }

                JsonObject emoteSet = userJson.has("emote_set") ? userJson.getAsJsonObject("emote_set") : null;
                if (emoteSet == null) {
                    return;
                }

                JsonArray emotes = emoteSet.getAsJsonArray("emotes");
                if (emotes == null) return;

                for (JsonElement el : emotes) {
                    JsonObject emoteObj = el.getAsJsonObject();
                    String name = emoteObj.has("name") ? emoteObj.get("name").getAsString() : null;
                    String id = emoteObj.has("id") ? emoteObj.get("id").getAsString() : null;
                    if (name != null && id != null && !name.isBlank()) {
                        freshChannelEmotes.put(name, id);
                    }
                }

                CHANNEL_EMOTES.clear();
                CHANNEL_EMOTES_LOWER.clear();
                CHANNEL_EMOTES.putAll(freshChannelEmotes);
                for (Map.Entry<String, String> entry : freshChannelEmotes.entrySet()) {
                    CHANNEL_EMOTES_LOWER.put(entry.getKey().toLowerCase(), entry.getValue());
                }
                currentChannelName = lower;
                currentTwitchRoomId = null;
            } catch (Exception e) {
                ChatrixClient.LOGGER.warn("Failed to load channel 7TV emotes for {}: {}", lower, e.getMessage());
            }
        });
    }

    /**
     * Loads channel 7TV emotes by Twitch room ID, which is more reliable than login lookup.
     */
    public static void loadChannelEmotesByRoomId(String channelName, String twitchRoomId) {
        if (twitchRoomId == null || twitchRoomId.isBlank()) {
            if (channelName != null && !channelName.isBlank()) {
                loadChannelEmotes(channelName);
            }
            return;
        }

        if (twitchRoomId.equals(currentTwitchRoomId) && !CHANNEL_EMOTES.isEmpty()) {
            return;
        }

        String lower = channelName == null ? "" : channelName.toLowerCase();
        EXECUTOR.execute(() -> {
            try {
                Map<String, String> freshChannelEmotes = new java.util.HashMap<>();

                JsonObject userJson = httpGetJson("https://7tv.io/v3/users/twitch/" + twitchRoomId);
                if (userJson == null) {
                    if (!lower.isBlank()) {
                        loadChannelEmotes(lower);
                    }
                    return;
                }

                JsonObject emoteSet = userJson.has("emote_set") ? userJson.getAsJsonObject("emote_set") : null;
                if (emoteSet == null) {
                    if (!lower.isBlank()) {
                        loadChannelEmotes(lower);
                    }
                    return;
                }

                JsonArray emotes = emoteSet.getAsJsonArray("emotes");
                if (emotes == null) return;

                for (JsonElement el : emotes) {
                    JsonObject emoteObj = el.getAsJsonObject();
                    String name = emoteObj.has("name") ? emoteObj.get("name").getAsString() : null;
                    String id = emoteObj.has("id") ? emoteObj.get("id").getAsString() : null;
                    if (name != null && id != null && !name.isBlank()) {
                        freshChannelEmotes.put(name, id);
                    }
                }

                CHANNEL_EMOTES.clear();
                CHANNEL_EMOTES_LOWER.clear();
                CHANNEL_EMOTES.putAll(freshChannelEmotes);
                for (Map.Entry<String, String> entry : freshChannelEmotes.entrySet()) {
                    CHANNEL_EMOTES_LOWER.put(entry.getKey().toLowerCase(), entry.getValue());
                }
                currentChannelName = lower.isBlank() ? null : lower;
                currentTwitchRoomId = twitchRoomId;
            } catch (Exception e) {
                ChatrixClient.LOGGER.warn("Failed to load channel 7TV emotes for room-id {}: {}", twitchRoomId, e.getMessage());
                if (!lower.isBlank()) {
                    loadChannelEmotes(lower);
                }
            }
        });
    }

    /**
     * Finds a 7TV emote by name, checking channel emotes before global emotes.
     * @return emoteId or null
     */
    public static String findEmoteId(String emoteName) {
        // Channel emotes take priority.
        String id = CHANNEL_EMOTES.get(emoteName);
        if (id != null) return id;
        id = GLOBAL_EMOTES.get(emoteName);
        if (id != null) return id;

        String lower = emoteName.toLowerCase();
        id = CHANNEL_EMOTES_LOWER.get(lower);
        if (id != null) return id;
        return GLOBAL_EMOTES_LOWER.get(lower);
    }

    /**
     * Scans message text for 7TV emotes.
     * Skips positions already occupied by Twitch emotes.
     *
     * @param text complete message text, including the colored username prefix
     * @param existingEmotes Twitch emotes that have already been parsed
     * @return detected 7TV emotes; may be empty
     */
    public static List<MessageEmote> scanForEmotes(String text, List<MessageEmote> existingEmotes) {
        if (!GLOBALS_LOADED.get() && CHANNEL_EMOTES.isEmpty()) {
            return Collections.emptyList();
        }

        // Build a bitmask of positions occupied by Twitch emotes.
        boolean[] occupied = new boolean[text.length()];
        if (existingEmotes != null) {
            for (MessageEmote existing : existingEmotes) {
                for (int i = existing.getStartIndex(); i <= existing.getEndIndex() && i < occupied.length; i++) {
                    occupied[i] = true;
                }
            }
        }

        List<MessageEmote> found = new java.util.ArrayList<>();

        // Split text on spaces and check each word.
        int i = 0;
        while (i < text.length()) {
            // Skip section-sign formatting sequences.
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                i += 2;
                continue;
            }

            // Skip whitespace.
            if (text.charAt(i) == ' ') {
                i++;
                continue;
            }

            // Skip occupied positions.
            if (occupied[i]) {
                i++;
                continue;
            }

            // Find the end of the word.
            int wordStart = i;
            while (i < text.length() && text.charAt(i) != ' ' && !occupied[i]) {
                i++;
            }
            int wordEnd = i - 1;

            String word = text.substring(wordStart, wordEnd + 1);
            // Remove section-sign formatting from the word before comparison.
            String cleanWord = word.replaceAll("§.", "");

            if (!cleanWord.isEmpty()) {
                String emoteId = findEmoteId(cleanWord);
                if (emoteId != null) {
                    found.add(new MessageEmote("7tv", emoteId, cleanWord, wordStart, wordEnd));
                    // Preload the texture.
                    TwitchEmoteTextureCache.preload("7tv", emoteId);
                }
            }
        }

        return found.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    /**
     * Checks whether emote sets have loaded.
     */
    public static boolean isReady() {
        return GLOBALS_LOADED.get() || !CHANNEL_EMOTES.isEmpty();
    }

    // ─── HTTP helper ───

    private static JsonObject httpGetJson(String url) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(url);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Chatrix/1.0");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code != 200) {
                ChatrixClient.LOGGER.warn("7TV HTTP {} for {}", code, url);
                return null;
            }

            try (InputStream is = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            ChatrixClient.LOGGER.warn("7TV HTTP request failed for {}: {}", url, e.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
