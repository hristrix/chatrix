package com.hristrix.chatrix.messages;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/** Dynamic texture constructor; a debug name is required starting with 1.21.8. */
final class EmoteTextureFactory {
    private EmoteTextureFactory() {}

    static NativeImageBackedTexture create(String debugName, NativeImage image) {
        return new NativeImageBackedTexture(() -> debugName, image);
    }
}
