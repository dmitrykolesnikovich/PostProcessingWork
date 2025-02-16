package jet.opengl.demos.nvidia.waves.ocean;

import org.lwjgl.util.vector.Vector4f;

final class OceanSurfaceParameters {
    // Shading properties
    final Vector4f waterbody_color = new Vector4f();
    float sky_blending;

    void set(OceanSurfaceParameters ohs){
        waterbody_color.set(ohs.waterbody_color);
        sky_blending = ohs.sky_blending;
    }
}
