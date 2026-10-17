package com.hristrix.chatrix.messages;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import com.hristrix.chatrix.config.MessageSpawnMode;
import com.hristrix.chatrix.config.ModConfig;
import com.hristrix.chatrix.filtering.BlockedUsernameManager;
import com.hristrix.chatrix.ui.gui.MessageCardRenderer;

import java.util.List;

/** Chat message HUD overlay; corner and offsets are configurable. */
public class MessageHudRenderer {
    private static final Identifier HUD_ELEMENT_ID = Identifier.fromNamespaceAndPath(
        "chatrix",
        "message_hud"
    );

    private final MessageLifecycleManager lifecycleManager;
    private final BlockedUsernameManager blockedUsernameManager = BlockedUsernameManager.getInstance();

    public MessageHudRenderer(MessageLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.BOSS_BAR,
            HUD_ELEMENT_ID,
            (graphics, deltaTracker) -> {
                if (ModConfig.getMESSAGE_SPAWN_MODE() == MessageSpawnMode.HUD_WIDGET) {
                    renderHudMessages(graphics);
                }
            }
        );
    }

    private void renderHudMessages(GuiGraphicsExtractor drawContext) {
        Minecraft client = Minecraft.getInstance();
        if (!MessageHudVisibility.shouldRender(client)) {
            return;
        }

        Font textRenderer = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        float hudScale = ModConfig.getHUD_SCALE_FACTOR();
        List<MessageHudOverlay.PreparedCard> cards = MessageHudOverlay.prepare(
            textRenderer,
            lifecycleManager.getActiveMessages(),
            lifecycleManager.getTickCounter(),
            screenWidth,
            screenHeight
        );

        for (MessageHudOverlay.PreparedCard card : cards) {
            var matrices = drawContext.pose();
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
