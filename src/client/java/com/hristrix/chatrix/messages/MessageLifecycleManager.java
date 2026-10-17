package com.hristrix.chatrix.messages;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.math.MathHelper;
import com.hristrix.chatrix.config.ConfigManager;
import com.hristrix.chatrix.config.MessageSpawnMode;
import com.hristrix.chatrix.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the message lifecycle.
 */
public class MessageLifecycleManager {
    private final List<Message> activeMessages = new ArrayList<>();
    private final List<Message> messageHistory = new ArrayList<>();
    /** Editor preview messages; the renderer displays only these while the editor is open. */
    private final List<Message> previewMessages = new ArrayList<>();
    private boolean previewActive = false;
    private int tickCounter = 0;
    private final MessageParticleManager particleManager;
    private final Set<Message> spawnedParticlesForMessages = new HashSet<>();
    
    public MessageLifecycleManager(MessageParticleManager particleManager) {
        this.particleManager = particleManager;
    }
    
    /**
     * Updates all active messages.
     * @param client Minecraft client
     */
    public void updateMessages(MinecraftClient client) {
        if (particleManager != null) {
            particleManager.tick();
        }
        if (client.player == null || client.world == null) return;
        
        tickCounter++;
        // Smooth player following, for 3D modes only.
        if (com.hristrix.chatrix.config.ModConfig.isFOLLOW_PLAYER()
                && com.hristrix.chatrix.config.ModConfig.getMESSAGE_SPAWN_MODE() != com.hristrix.chatrix.config.MessageSpawnMode.HUD_WIDGET) {
            var eyePos = client.player.getEyePos();
            // Smoothing duration in ticks.
            float positionSmoothTime = 14.0f;   // More inertia
            float yawSmoothTime = 12.0f;
            float pitchSmoothTime = 12.0f;

            // Per-tick exponential smoothing coefficients.
            float positionLerp = clamp01(1.0f - (float)Math.exp(-1.0f / positionSmoothTime));
            float yawLerp = clamp01(1.0f - (float)Math.exp(-1.0f / yawSmoothTime));
            float pitchLerp = clamp01(1.0f - (float)Math.exp(-1.0f / pitchSmoothTime));

            for (Message message : activeMessages) {
                if (message.isPinned()) {
                    continue;
                }
                Vec3d local = message.getTargetOffsetFromEye();
                if (local == null) continue; // For HUD or older messages

                // Target world position is the player's eyes plus a fixed world offset, independent of yaw.
                Vec3d targetWorld = eyePos.add(local);

                // Approach the target smoothly with acceleration and deceleration, without velocity clipping.
                Vec3d currentPos = message.getPosition();
                Vec3d toTarget = targetWorld.subtract(currentPos);
                Vec3d newPos = currentPos.add(toTarget.multiply(positionLerp));
                message.setPosition(newPos);

                // Smoothly orient toward the player's eyes rather than snapping.
                double dx = eyePos.x - newPos.x;
                double dy = eyePos.y - newPos.y;
                double dz = eyePos.z - newPos.z;
                double distXZ = Math.sqrt(dx * dx + dz * dz);
                float targetYaw = (float)(MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
                float targetPitch = (float)-(MathHelper.atan2(dy, distXZ) * (180.0 / Math.PI));
                message.setYaw(lerpAngleDegrees(message.getYaw(), targetYaw, yawLerp));
                message.setPitch(lerpAngleDegrees(message.getPitch(), targetPitch, pitchLerp));
            }
        }
        
        boolean hudMode = ModConfig.getMESSAGE_SPAWN_MODE() == MessageSpawnMode.HUD_WIDGET;

        // Update frozen time for all messages, in 3D modes only.
        if (!hudMode && ModConfig.isENABLE_FREEZING_ON_VIEW()) {
            for (Message message : activeMessages) {
                if (message.isPinned()) {
                    continue;
                }
                if (MessageViewDetector.isPlayerLookingAtMessage(client, message, tickCounter)) {
                    message.incrementFrozenTicks();
                }
            }
        }
        
        // Remove expired messages, accounting for frozen time.
        int fallTicks = ModConfig.getMESSAGE_FALL_TICKS();
        int removeAfter = ModConfig.getMESSAGE_LIFETIME_TICKS() + fallTicks;
        if (ModConfig.isENABLE_FREEZING_ON_VIEW()) {
            activeMessages.removeIf(message -> {
                if (message.isPinned()) {
                    return false;
                }
                int effectiveAge = message.getEffectiveAge(tickCounter);
                trySpawnBreakParticles(message, client, effectiveAge, removeAfter, hudMode);
                if (effectiveAge > removeAfter) {
                    spawnedParticlesForMessages.remove(message);
                    return true;
                }
                return false;
            });
        } else {
            activeMessages.removeIf(message -> {
                if (message.isPinned()) {
                    return false;
                }
                int effectiveAge = tickCounter - (int) message.getSpawnTick();
                trySpawnBreakParticles(message, client, effectiveAge, removeAfter, hudMode);
                if (effectiveAge > removeAfter) {
                    spawnedParticlesForMessages.remove(message);
                    return true;
                }
                return false;
            });
        }
    }

    private void trySpawnBreakParticles(
        Message message,
        MinecraftClient client,
        int effectiveAge,
        int removeAfter,
        boolean hudMode
    ) {
        if (hudMode || particleManager == null || spawnedParticlesForMessages.contains(message)) {
            return;
        }
        // Spawn particles when the panel shatters at the end of its fall animation.
        if (effectiveAge < removeAfter - 1) {
            return;
        }
        int shatterAge = removeAfter - 1;
        Vec3d panelCenter = MessageViewDetector.calculateFallingPosition(
            message.getPosition(),
            shatterAge,
            message.getYaw(),
            message.getPitch()
        );
        MessageParticleSpawner.spawnParticlesForMessage(
            message, particleManager, client, panelCenter, shatterAge
        );
        spawnedParticlesForMessages.add(message);
    }
    
    /**
     * Adds a new message to the active list.
     * @param message message to add
     */
    public void addMessage(Message message) {
        activeMessages.add(message);
        
        // Add to history.
        messageHistory.add(message);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            message.warmHistoryLayout(client.textRenderer);
        }
        
        // Limit history size.
        enforceHistoryLimit();
    }

    public void enforceHistoryLimit() {
        int maxSize = getMaxHistorySize();
        while (messageHistory.size() > maxSize) {
            messageHistory.remove(0);
        }
    }

    private int getMaxHistorySize() {
        Object value = ConfigManager.getInstance().getConfigValue("messageHistoryMaxSize");
        if (value instanceof Integer size && size > 0) {
            return size;
        }
        return 100;
    }
    
    public void addReplayMessage(Message message) {
        activeMessages.add(message);
    }

    public Message findPinnedForHistory(Message historyMessage) {
        if (historyMessage == null) {
            return null;
        }
        long sourceId = historyMessage.getId();
        for (Message active : activeMessages) {
            Long linkedId = active.getHistorySourceId();
            if (active.isPinned() && linkedId != null && linkedId == sourceId) {
                return active;
            }
        }
        return null;
    }

    public boolean isVisibleInWorld(Message historyMessage) {
        if (historyMessage == null) {
            return false;
        }
        for (Message active : activeMessages) {
            if (active == historyMessage) {
                return true;
            }
            Long linkedId = active.getHistorySourceId();
            if (active.isPinned() && linkedId != null && historyMessage.getId() == linkedId) {
                return true;
            }
        }
        return false;
    }

    public boolean isPinnedInWorld(Message historyMessage) {
        if (historyMessage == null) {
            return false;
        }
        if (historyMessage.isPinned() && activeMessages.contains(historyMessage)) {
            return true;
        }
        return findPinnedForHistory(historyMessage) != null;
    }

    /** All messages pinned in the world, including messages no longer in history. */
    public List<Message> getAllPinnedMessages() {
        List<Message> pinned = new ArrayList<>();
        for (Message message : activeMessages) {
            if (message.isPinned()) {
                pinned.add(message);
            }
        }
        return pinned;
    }

    /**
     * Clears all active messages.
     */
    public void clearAllMessages() {
        activeMessages.clear();
        tickCounter = 0;
    }

    public void clearTransientMessages() {
        activeMessages.removeIf(message -> !message.isPinned());
        spawnedParticlesForMessages.removeIf(message -> !message.isPinned());
    }

    public void removeMessagesOutsideDimension(String dimensionKey) {
        if (dimensionKey == null || dimensionKey.isBlank()) {
            return;
        }
        activeMessages.removeIf(message -> !message.isInDimension(dimensionKey));
        spawnedParticlesForMessages.removeIf(message -> !message.isInDimension(dimensionKey));
    }
    
    /**
     * Messages to display: only preview messages while the visual editor is open.
     * Use {@link #getLiveMessages()} to save or modify actual chat messages.
     */
    public List<Message> getActiveMessages() {
        return previewActive ? previewMessages : activeMessages;
    }

    /** Actual chat messages, independent of the editor preview. */
    public List<Message> getLiveMessages() {
        return activeMessages;
    }

    public void startPreview(List<Message> messages) {
        previewMessages.clear();
        for (Message message : messages) {
            message.setPreview(true);
            previewMessages.add(message);
        }
        previewActive = true;
    }

    public void stopPreview() {
        previewActive = false;
        previewMessages.clear();
    }

    public boolean isPreviewActive() {
        return previewActive;
    }
    
    /**
     * Returns the current tick counter.
     * @return current tick counter
     */
    public int getTickCounter() {
        return tickCounter;
    }
    
    /**
     * Returns all messages in history.
     * @return all messages in history
     */
    public List<Message> getAllMessages() {
        return List.copyOf(messageHistory);
    }

    public void warmHistoryLayouts(TextRenderer textRenderer) {
        if (textRenderer == null) {
            return;
        }
        for (Message message : messageHistory) {
            message.warmHistoryLayout(textRenderer);
        }
        for (Message message : getAllPinnedMessages()) {
            message.warmHistoryLayout(textRenderer);
        }
    }

    public void warmPendingHistoryLayouts(TextRenderer textRenderer, int limit) {
        if (textRenderer == null || limit <= 0) {
            return;
        }
        int remaining = limit;
        for (Message message : messageHistory) {
            if (message.hasWarmedHistoryLayout()) {
                continue;
            }
            message.warmHistoryLayout(textRenderer);
            if (--remaining <= 0) {
                return;
            }
        }
    }

    public long peekHistoryFirstId() {
        return messageHistory.isEmpty() ? -1L : messageHistory.get(0).getId();
    }

    public long peekHistoryLastId() {
        return messageHistory.isEmpty() ? -1L : messageHistory.get(messageHistory.size() - 1).getId();
    }
    
    /**
     * Returns the message history size.
     * @return number of messages in history
     */
    public int getMessageHistorySize() {
        return messageHistory.size();
    }
    
    /**
     * Clears message history.
     */
    public void clearMessageHistory() {
        messageHistory.clear();
    }
    
    /**
     * Removes a message and immediately spawns particles.
     * @param message message to remove
     * @param client Minecraft client
     */
    public void removeMessageWithParticles(Message message, MinecraftClient client) {
        if (activeMessages.contains(message)) {
            // Spawn particles at the current position.
            if (particleManager != null) {
                int age = message.getEffectiveAge(tickCounter);
                MessageParticleSpawner.spawnParticlesForMessage(
                    message, particleManager, client, message.getPosition(), age
                );
            }
            
            // Remove the message.
            activeMessages.remove(message);
            spawnedParticlesForMessages.remove(message);
        }
    }

    public void removeMessageWithoutParticles(Message message) {
        activeMessages.remove(message);
        spawnedParticlesForMessages.remove(message);
    }

    public void addPinnedMessage(Message message) {
        message.setPinned(true);
        activeMessages.add(message);
    }

    public Message unpinMessage(Message message, MinecraftClient client) {
        if (message == null || !activeMessages.contains(message)) {
            return null;
        }

        Vec3d targetOffset = Vec3d.ZERO;
        if (client != null && client.player != null) {
            targetOffset = message.getPosition().subtract(client.player.getEyePos());
        }

        Message replacement = new Message(
            message.getText(),
            message.getPosition(),
            tickCounter,
            message.getYaw(),
            message.getPitch(),
            message.getAuthorColorRgb(),
            targetOffset,
            message.getEmotes()
        );
        replacement.setPinned(false);
        replacement.setDimensionKey(message.getDimensionKey());

        int idx = activeMessages.indexOf(message);
        activeMessages.set(idx, replacement);
        spawnedParticlesForMessages.remove(message);
        return replacement;
    }

    private static float lerpAngleDegrees(float a, float b, float t) {
        float delta = MathHelper.wrapDegrees(b - a);
        return a + delta * MathHelper.clamp(t, 0.0f, 1.0f);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
} 