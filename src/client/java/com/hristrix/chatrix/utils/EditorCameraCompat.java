package com.hristrix.chatrix.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;

/** Camera perspective and player rotation for the message editor preview. */
public final class EditorCameraCompat {
    private EditorCameraCompat() {}

    public static Object capturePerspective(MinecraftClient client) {
        return client.options.getPerspective();
    }

    public static void restorePerspective(MinecraftClient client, Object perspective) {
        if (perspective instanceof Perspective value) {
            client.options.setPerspective(value);
        }
    }

    public static void setThirdPersonBack(MinecraftClient client) {
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    public static float yaw(Entity entity) {
        return entity.getYaw();
    }

    public static float pitch(Entity entity) {
        return entity.getPitch();
    }

    /** Sets rotation immediately without tick interpolation while the editor pauses the game. */
    public static void setRotation(Entity entity, float yaw, float pitch) {
        entity.setYaw(yaw);
        entity.setPitch(pitch);
        entity.lastYaw = yaw;
        entity.lastPitch = pitch;
        entity.setHeadYaw(yaw);
    }
}
