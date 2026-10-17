package com.hristrix.chatrix.core.render;

import java.util.ArrayList;
import java.util.List;

/**
 * A 1x1x1-block reference cube around the message in the editor, with translucent faces
 * and bright edges, drawn as strips along each face so they remain visible from any angle.
 */
public final class BlockCubeGeometry {
    public static final float HALF = 0.5f;
    public static final float FACE_ALPHA = 0.2f;
    public static final float EDGE_ALPHA = 0.4f;
    private static final float EDGE_WIDTH = 0.025f;
    private static final float EDGE_LIFT = 0.002f;

    /** Four vertices with consecutive x, y, z coordinates in blocks relative to the cube center. */
    public record Quad(float[] xyz, boolean edge) {}

    private static final List<Quad> QUADS = build();

    private BlockCubeGeometry() {}

    public static List<Quad> quads() {
        return QUADS;
    }

    private static List<Quad> build() {
        List<Quad> quads = new ArrayList<>();
        float h = HALF;
        float w = EDGE_WIDTH;
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                float plane = sign * h;
                quads.add(face(axis, plane, -h, -h, h, h, false));
                float edgePlane = sign * (h + EDGE_LIFT);
                quads.add(face(axis, edgePlane, -h, -h, -h + w, h, true));
                quads.add(face(axis, edgePlane, h - w, -h, h, h, true));
                quads.add(face(axis, edgePlane, -h + w, -h, h - w, -h + w, true));
                quads.add(face(axis, edgePlane, -h + w, h - w, h - w, h, true));
            }
        }
        return List.copyOf(quads);
    }

    /** Rectangle in the {@code axis = plane} plane, spanning (u0, v0) to (u1, v1) on the other axes. */
    private static Quad face(int axis, float plane, float u0, float v0, float u1, float v1, boolean edge) {
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        float[] xyz = new float[12];
        for (int i = 0; i < 4; i++) {
            float[] point = new float[3];
            point[axis] = plane;
            point[(axis + 1) % 3] = uv[i][0];
            point[(axis + 2) % 3] = uv[i][1];
            System.arraycopy(point, 0, xyz, i * 3, 3);
        }
        return new Quad(xyz, edge);
    }
}
