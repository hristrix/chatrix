# Chatrix message system

The message system separates data, placement, scheduling, lifecycle, interaction, and rendering.

## Components

| Class | Responsibility |
| --- | --- |
| `Message` | Text, position, creation time, frozen ticks, and effective age |
| `MessagePosition` | Random world positions, spawn radius, and height |
| `MessageViewDetector` | Gaze ray intersection with the actual message plane |
| `MessageQueue` | Pending messages, scheduled display, and spawn intervals |
| `MessageLifecycleManager` | Updates, gaze freezing, and timed removal |
| `MessageRenderer` | World text, 9-slice panels, and fall/fade animation |
| `MessageSpawner` | Message creation and coordination |
| `MessageSystemFactory` | Creates and configures the system components |

This division keeps each responsibility separate, permits focused tests and extensions, and allows components to be reused in other screens and rendering paths.

## Gaze freezing

The detector uses ray-plane intersection rather than an angle to the panel center, so looking at the edge of a large card still freezes its timer.

1. Calculate the message's effective age, excluding frozen ticks.
2. Apply the same fall offset used by the renderer.
3. Calculate panel dimensions from the rendered content.
4. Intersect the player's gaze ray with the message plane.
5. Check the hit against the panel bounds.
6. Account for message yaw, pitch, and the configured maximum freeze distance.

## Usage

```java
// Create the system through its factory.
MessageSpawner messageSpawner = MessageSystemFactory.createMessageSystem();

// Add a message.
messageSpawner.setCurrentMessage("Hello, world!");

// Stop the current message.
messageSpawner.setCurrentMessage("");
```

## Configuration

`ModConfig` provides access to settings stored by `ConfigManager`. Lifetime, fall duration, gaze freezing, and maximum freeze distance control message behavior. Legacy tick-based fields remain for configuration compatibility; the interface uses seconds where supported.

## Pinned messages

- Right-click a message to pin it in the current world or server.
- Hold the right mouse button to drag a pinned card in front of the player.
- Left-click dismisses unpinned cards.
- Pinned cards do not expire or follow the player.
- The configured unpin mode selects whether the pin icon or the whole card is clickable.

Persistence is scoped by world or server and dimension. Saved data includes message text, x/y/z position, yaw/pitch rotation, and author color when available. Files live in the Chatrix storage directory under `pinned-messages/`.
