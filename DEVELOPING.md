# Developing Chatrix

Chatrix uses shared Java sources and Stonecutter for Minecraft version differences. See [BUILD.md](BUILD.md) for build commands.

## Identity

- Java package and Maven group: `com.hristrix.chatrix`
- Client entrypoint: `com.hristrix.chatrix.ChatrixClient`
- Mod ID and asset namespace: `chatrix`
- Artifact prefix: `chatrix`
- Command: `/chatrix`;

## Source layout

| Location | Purpose |
| --- | --- |
| `src/client/java/com/hristrix/chatrix/` | Shared client implementation |
| `src/main/resources/assets/chatrix/` | Icons, textures, translations, and filter vocabulary |
| `src/main/resources/fabric.mod.json` | Mod metadata and entrypoints |
| `src/client/resources/chatrix.client.mixins.json` | Mixin configuration |
| `versions/<minecraft>/src/` | Version overrides |
| `versions/<minecraft>/gradle.properties` | Version dependencies and compatibility |
| `stonecutter.gradle.kts` | Active version and source conversions |
| `build-*.gradle.kts` | Build definitions for different API generations |

Edit shared sources or version overrides. Do not edit generated files under `build/` or `versions/*/build/`.

## Components

`integration` connects platform chats and parses incoming data. `messages` owns queuing, lifecycle, positioning, history, emotes, and rendering. `filtering` manages blocked words and usernames. `config` stores settings; `ui` implements settings and the visual editor. `utils` contains compatibility adapters.

Settings and saved messages use Chatrix storage. Legacy migration paths do not target another mod's configuration folder.

## Language and assets

Write comments, logs, and documentation in English. Put new user-facing UI strings in `assets/chatrix/lang/en_us.json` under `chatrix.*` keys. Run `python scripts/sync-lang-from-en.py` to add missing English fallback keys to bundled locales. The Russian locale deliberately mirrors English.

Keep texture dimensions compatible with rendering call sites. The logo is `assets/chatrix/icon.png`; controls, badges, and platform icons live under `textures/`.

## Verification

Build all five supported targets after shared code or resource changes. Check jar identity, entrypoints, mixins, and asset paths. Run the manual checks in BUILD.md when a Minecraft client is available. Chat transport checks are available in `scripts/check-chat.ps1`; see `tests/chat/README.md`. Compilation does not verify live services or in-game rendering.
