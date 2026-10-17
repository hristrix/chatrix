package com.hristrix.chatrix.integration.chat;

import java.net.URI;
import java.util.Locale;

/** Normalizes public Twitch and Kick usernames without accepting IRC commands or URL fragments. */
public final class ChatChannelName {
    private ChatChannelName() {}

    public static String normalize(String input, String platform) {
        String name = input == null ? "" : input.trim();
        String domain = platform + ".tv";
        if ("kick".equals(platform) || "tiktok".equals(platform)) {
            domain = platform + ".com";
        }
        if (name.contains("://") || name.toLowerCase(Locale.ROOT).startsWith(domain + "/")
            || name.toLowerCase(Locale.ROOT).startsWith("www." + domain + "/")) {
            URI uri;
            try {
                uri = URI.create(name.contains("://") ? name : "https://" + name);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Enter a valid " + platform + " username or channel URL");
            }
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase(domain) || host.equalsIgnoreCase("www." + domain))) {
                throw new IllegalArgumentException("Use a " + domain + " channel URL, or enter just the username");
            }
            String path = uri.getPath();
            name = path == null ? "" : path.replaceFirst("^/+", "").split("/", 2)[0];
        }
        name = name.replaceFirst("^[@#]+", "").toLowerCase(Locale.ROOT);
        String pattern = "tiktok".equals(platform) ? "[a-z0-9_][a-z0-9_.]{0,49}" : "[a-z0-9_][a-z0-9_-]{0,49}";
        if (!name.matches(pattern)) {
            throw new IllegalArgumentException("Enter a valid " + platform + " username (without spaces)");
        }
        return name;
    }
}
