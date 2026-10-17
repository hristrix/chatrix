package com.hristrix.chatrix.messages;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import com.hristrix.chatrix.config.MessageSpawnMode;
import com.hristrix.chatrix.config.ModConfig;
import com.hristrix.chatrix.filtering.BlockedUsernameManager;
import com.hristrix.chatrix.ui.gui.MessageCardRenderer;

import java.util.List;

/** Chat message HUD overlay; corner and offsets are configurable. */
public class MessageHudRenderer {
    private final MessageLifecycleManager lifecycleManager;
    private final BlockedUsernameManager blockedUsernameManager = BlockedUsernameManager.getInstance();

    public MessageHudRenderer(MessageLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (ModConfig.getMESSAGE_SPAWN_MODE() == MessageSpawnMode.HUD_WIDGET) {
                renderHudMessages(drawContext);
            }
        });
    }

    private void renderHudMessages(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!MessageHudVisibility.shouldRender(client)) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        float hudScale = ModConfig.getHUD_SCALE_FACTOR();
        List<MessageHudOverlay.PreparedCard> cards = MessageHudOverlay.prepare(
            textRenderer,
            lifecycleManager.getActiveMessages().stream()
                .filter(message -> PinnedMessageStore.belongsToCurrentWorld(message, client))
                .toList(),
            lifecycleManager.getTickCounter(),
            screenWidth,
            screenHeight
        );

        for (MessageHudOverlay.PreparedCard card : cards) {
            var matrices = drawContext.getMatrices();
            matrices.pushMatrix();
            matrices.translate((float) card.x(), (float) card.y());
            matrices.scale(hudScale, hudScale);

            MessageCardRenderer.drawHudCard(
                drawContext,
                textRenderer,
                blockedUsernameManager,
                card.message(),
                card.parsed(),
                card.layout(),
                0,
                0,
                card.alpha()
            );

            matrices.popMatrix();
        }
    }
}
