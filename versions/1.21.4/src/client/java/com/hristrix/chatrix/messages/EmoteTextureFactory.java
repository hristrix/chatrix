package com.hristrix.chatrix.messages;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/** Dynamic texture constructor before 1.21.8, accepting only an image. */
final class EmoteTextureFactory {
    private EmoteTextureFactory() {}

    static NativeImageBackedTexture create(String debugName, NativeImage image) {
        return new NativeImageBackedTexture(image);
    }
}
