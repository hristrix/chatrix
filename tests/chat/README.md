# Chat connection checks

Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-chat.ps1` after building Chatrix. Requires a JDK and the Gson dependency cached by Gradle.

Default checks cover username/URL normalization, wrong-platform links, IRC injection, failed WebSocket handshakes, and remote closure. They also check valid and failed YouTube polling responses and TikTok feed confirmation, closure, failure, and cancellation. TikTok comment checks cover language metadata, Unicode text, malformed optional emotes, and valid inline stickers. Socket checks use a local test server; TikTok state checks use a fake WebSocket.

Add `-Live` to check production Twitch and Kick parsers with fixtures, join Twitch's `aethelthryth` channel, and subscribe to Kick's `xqc` chat. These checks only read public chat and never post messages. They require internet access and depend on platform availability.

The harness compiles production transport and parser classes with minimal Minecraft UI, logging, emote-loading, and pipeline stubs. It verifies delivery into the pipeline, not filtering or in-game rendering. The live message count may be zero if a channel is quiet or offline.
