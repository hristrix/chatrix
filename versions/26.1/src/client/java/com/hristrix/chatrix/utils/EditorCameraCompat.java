package com.hristrix.chatrix.utils;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Camera perspective and player rotation for the message editor preview. */
public final class EditorCameraCompat {
    private EditorCameraCompat() {}

    public static Object capturePerspective(Minecraft client) {
        return client.options.getCameraType();
    }

    public static void restorePerspective(Minecraft client, Object perspective) {
        if (perspective instanceof CameraType value) {
            client.options.setCameraType(value);
        }
    }

    public static void setThirdPersonBack(Minecraft client) {
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    public static float yaw(Entity entity) {
        return entity.getYRot();
    }

    public static float pitch(Entity entity) {
        return entity.getXRot();
    }

    /** Sets rotation immediately without tick interpolation while the editor pauses the game. */
    public static void setRotation(Entity entity, float yaw, float pitch) {
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
        entity.setYHeadRot(yaw);
    }
}
