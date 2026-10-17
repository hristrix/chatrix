package com.hristrix.chatrix.utils;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** RenderType helper; 26.2 uses SubmitNodeCollector without buffers. */
public final class RenderLayerCompat {
    private RenderLayerCompat() {}

    public static RenderType getEntityTextureLayer(Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    public static RenderType getTextLayer(Identifier texture) {
        return RenderTypes.text(texture);
    }
}
