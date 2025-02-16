package jet.opengl.renderer.Unreal4;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.ReadableVector3f;

import java.util.ArrayList;
import java.util.List;

//import jet.opengl.demos.nvidia.shadows.ShadowMapGenerator;
import jet.opengl.postprocessing.core.volumetricLighting.LightType;
import jet.opengl.postprocessing.texture.TextureGL;
import jet.opengl.postprocessing.util.BoundingBox;
import jet.opengl.postprocessing.util.BoundingSphere;

public class UE4LightCollections {
    public void resetLights(){
        lightInfos.clear();
    }

    public void load(UE4LightCollections ohs){
        if(this == ohs) return;

        lightInfos.clear();
        lightInfos.addAll(ohs.lightInfos);
    }

    public void addPointLight(ReadableVector3f position, float range, ReadableVector3f color, float intensity, Matrix4f[] view, Matrix4f proj, TextureGL shadowmap){
        UE4LightInfo info = new UE4LightInfo();
        info.color.set(color);
        info.position.set(position);
        info.type = LightType.POINT;
        info.range = range;
        info.intensity = intensity;
        info.cubeViews = view;
        info.proj = proj;
        info.boundingSphere = new BoundingSphere(position, range);
        info.shadowmap = shadowmap;

        lightInfos.add(info);
    }

    public void addDirectionLight(ReadableVector3f dir, ReadableVector3f color, float intensity, Matrix4f view, Matrix4f proj, TextureGL shadowmap){
        UE4LightInfo info = new UE4LightInfo();
        info.color.set(color);
        info.type = LightType.DIRECTIONAL;
        info.direction.set(dir);
        info.intensity = intensity;
        info.view = view;
        info.proj = proj;
        BoundingBox box = new BoundingBox();
//        ShadowMapGenerator.calculateCameraViewBoundingBox(view, proj, box);  todo
        info.boundingSphere = new BoundingSphere(box.center(null), box.radius());
        info.shadowmap = shadowmap;

        lightInfos.add(info);
    }

    public void addSpotLight(ReadableVector3f position, float range,float outerAngle,
                             ReadableVector3f dir, ReadableVector3f color, float intensity, Matrix4f view, Matrix4f proj, TextureGL shadowmap){
        UE4LightInfo info = new UE4LightInfo();
        info.color.set(color);
        info.position.set(position);
        info.direction.set(dir);
        info.spotAngle = outerAngle;
        info.type = LightType.SPOT;
        info.range = range;
        info.intensity = intensity;
        info.view = view;
        info.proj = proj;
        info.boundingSphere = new BoundingSphere(position, range);
        info.shadowmap = shadowmap;

        lightInfos.add(info);
    }

    public List<UE4LightInfo> lightInfos = new ArrayList<>();
}
