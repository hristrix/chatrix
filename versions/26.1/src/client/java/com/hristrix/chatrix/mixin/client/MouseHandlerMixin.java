package com.hristrix.chatrix.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.config.MessageSpawnMode;
import com.hristrix.chatrix.config.ModConfig;
import com.hristrix.chatrix.messages.Message;
import com.hristrix.chatrix.messages.MessageClickHandler;
import com.hristrix.chatrix.messages.PinnedMessageStore;
import com.hristrix.chatrix.utils.ScreenNavigationCompat;

import java.util.ArrayList;

/** Mixin handling clicks on 3D messages (Minecraft 26.x). */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        int button = input.button();

        if (minecraft.player == null || minecraft.level == null || ScreenNavigationCompat.current(minecraft) != null) {
            return;
        }

        if (ModConfig.getMESSAGE_SPAWN_MODE() == MessageSpawnMode.HUD_WIDGET) {
            return;
        }

        var messageSpawner = ChatrixClient.getStaticMessageSpawner();
        if (messageSpawner == null) {
            return;
        }

        var lifecycleManager = messageSpawner.getLifecycleManager();
        if (lifecycleManager == null) {
            return;
        }

        if (button == 1) {
            var interactionManager = messageSpawner.getPinnedInteractionManager();
            if (interactionManager == null) {
                return;
            }

            if (action == 1) {
                if (!minecraft.player.getMainHandItem().isEmpty()) {
                    return;
                }
                if (interactionManager.onRightMousePressed(minecraft)) {
                    ci.cancel();
                }
                return;
            }

            if (action == 0) {
                interactionManager.onRightMouseReleased();
            }
            return;
        }

        if (!ModConfig.isENABLE_CLICK_TO_REMOVE() || button != 0 || action != 1) {
            return;
        }

        if (!minecraft.player.getMainHandItem().isEmpty()) {
            return;
        }

        var candidates = new ArrayList<Message>();
        for (Message message : lifecycleManager.getActiveMessages()) {
            if (!PinnedMessageStore.belongsToCurrentWorld(message, minecraft)) {
                continue;
            }
            if (message.isPinned()) {
                continue;
            }
            candidates.add(message);
        }

        MessageClickHandler.MessageHit hit = MessageClickHandler.findClosestMessageHit(
            minecraft,
            candidates,
            lifecycleManager.getTickCounter()
        );
        if (hit != null) {
            lifecycleManager.removeMessageWithParticles(hit.getMessage(), minecraft);
            LocalPlayer player = Minecraft.getInstance().player;
            player.swing(player.getUsedItemHand());
            player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.5f, 1.5f);
            ci.cancel();
        }
    }
}
