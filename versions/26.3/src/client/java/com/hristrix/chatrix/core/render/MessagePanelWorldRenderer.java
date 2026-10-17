package com.hristrix.chatrix.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.hristrix.chatrix.core.MessagePanelConstants;
import com.hristrix.chatrix.utils.SubmitGeometryHelper;

/** 9-slice message panel in the 3D world (Minecraft 26.2). */
public final class MessagePanelWorldRenderer {
    /**
     * The border sits slightly closer to the camera than the fill: in 26.2, translucent geometry shares a
     * plane, and COLLECT_SUBMITS sorting can draw the background over the border.
     */
    private static final float BORDER_Z = 0.02f;

    private MessagePanelWorldRenderer() {}

    /** Panel background and border, tinted with the platform color. */
    public static void drawPanelWithBorder(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int x,
        int y,
        int width,
        int height,
        float alpha,
        int borderRgb
    ) {
        int baseRgb = com.hristrix.chatrix.core.MessagePanelColors.baseRgb();
        SubmitGeometryHelper.submitPanel(
            collector,
            poseStack,
            MessagePanelConstants.PANEL_TEXTURE,
            x,
            y,
            width,
            height,
            0f,
            alpha,
            ((baseRgb >> 16) & 0xFF) / 255.0f,
            ((baseRgb >> 8) & 0xFF) / 255.0f,
            (baseRgb & 0xFF) / 255.0f
        );

        int resolvedBorderRgb = com.hristrix.chatrix.core.MessagePanelColors.borderRgb(borderRgb);
        float red = ((resolvedBorderRgb >> 16) & 0xFF) / 255.0f;
        float green = ((resolvedBorderRgb >> 8) & 0xFF) / 255.0f;
        float blue = (resolvedBorderRgb & 0xFF) / 255.0f;
        SubmitGeometryHelper.submitPanel(
            collector,
            poseStack,
            MessagePanelConstants.PANEL_BORDER_TEXTURE,
            x,
            y,
            width,
            height,
            BORDER_Z,
            alpha,
            red,
            green,
            blue
        );
    }

    /** Translucent 1x1x1-block cube centered at the current matrix origin; units are blocks. */
    public static void drawBlockCube(org.joml.Matrix4f matrix, SubmitNodeCollector collector) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(matrix);
        SubmitGeometryHelper.submitBlockCube(collector, poseStack, MessagePanelConstants.PANEL_TEXTURE);
    }
}
