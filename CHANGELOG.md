# Chatrix changelog

## Branding update

- Use the supplied Chatrix chat-bubble logo as the mod icon and in the README.
- Match navigation icons, role badges, platform icons, the pin, and particles to its white-and-violet geometric style.
- Restyle settings screens with charcoal panels, clipped corners, white text, violet selections, matching platform cards, and custom sliders.
- Add the chat-bubble mark to screen titles and improve keyboard-focus highlighting on shared buttons.

## Connection fixes

- Confirm YouTube only after valid live-chat polling succeeds, and TikTok only after a decoded feed response.
- Clear TikTok connection state on failed handshakes, remote closure, timeouts, and send failures; reconnect with backoff.
- Accept TikTok usernames and channel URLs, and format connection parameters independently of system language.

- Accept usernames, @usernames, and matching Twitch or Kick URLs; reject links from another platform.
- Resolve Kick's current public chat configuration instead of using an obsolete app key.
- Wait for Kick connection and subscription acknowledgements, handle heartbeat events, and reconnect after WebSocket failures.
- Serialize WebSocket text sends and clear state after handshake errors or remote closure.
- Connect Twitch in the background with connection/join timeouts, UTF-8 decoding, and retries.
- Show confirmed connection status only after a platform acknowledges the join or subscription.
- Add transport regression checks and optional live read-only checks in `scripts/check-chat.ps1`.

## 2.2.0 - Chatrix project setup

- Established the `com.hristrix.chatrix` package and Maven group.
- Set the mod ID, resource namespace, and jar prefix to `chatrix`.
- Added a logo and replacement interface, badge, pin, and platform icons.
- Added `/chatrix` as the primary command.
- Translated Russian comments and command text to English.
- Made the Russian locale use English strings and the bundled filter vocabulary use English words.
- Updated installation, development, build, and rendering documentation.
- Added a build script and CI configuration for Fabric 1.21.10 through 26.3.
- Fixed 26.x screen-close and history-tooltip compatibility conversions.

Initial code provenance is recorded in ATTRIBUTION.md. Live services and in-game behavior require separate runtime verification.
