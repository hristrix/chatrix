# Chatrix rendering

Classes are under `com.hristrix.chatrix`. Supported targets are Minecraft 1.21.10, 1.21.11, 26.1, 26.2, and 26.3.

## Message flow

1. Platform clients parse chat and event data.
2. Messages pass through filtering and the bounded display queue.
3. `MessageSpawner` assigns world positions and schedules display.
4. `MessageLifecycleManager` updates timers, gaze freezing, and cleanup.
5. `MessageRenderer` draws world cards; `MessageHudRenderer` draws HUD cards.

The editor supplies preview messages to the same rendering system while hiding real chat. Closing it restores normal messages and the camera state.

## Panels and interaction

`MessagePanelConstants` defines shared dimensions and Chatrix texture paths. Fill and border textures are separate for tinting. Layout, gaze freezing, and click detection use matching panel dimensions.

`MessageClickHandler` resolves world hits. `PinnedMessageInteractionManager` handles pinning, unpinning, and dragging. Pins persist by world or server and dimension.

## Emotes and badges

Platform parsers create `MessageEmote` text ranges. The 7TV provider augments Twitch text with matching codes. Texture download and CPU decoding run away from the render thread; GPU registration occurs on the client side. Texture waits are bounded so unavailable downloads do not stall chat indefinitely.

Platform and role icons are local resources. Wrapped emote indices are relative to each line's text. Rendering and interaction must agree on scale, first-line indentation, and wrapping width.

## Compatibility

Minecraft 1.21.x uses mapped APIs. Minecraft 26.x uses Mojang-named APIs; later versions use submission-based rendering. Version overrides and Stonecutter conversions adapt drawing, screen navigation, camera access, and input.

After shared rendering changes, build every target and check world cards, HUD cards, editor previews, emotes, pins, and history in game. A successful build does not verify visual output.
