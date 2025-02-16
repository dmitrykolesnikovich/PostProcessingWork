package jet.opengl.renderer.Unreal4.lgi;

import org.lwjgl.util.vector.Vector4f;

import java.io.IOException;

import jet.opengl.renderer.Unreal4.FForwardLocalLightData;
import jet.opengl.postprocessing.shader.GLSLProgram;
import jet.opengl.postprocessing.shader.Macro;
import jet.opengl.postprocessing.shader.ShaderLoader;
import jet.opengl.postprocessing.shader.ShaderSourceItem;
import jet.opengl.postprocessing.shader.ShaderType;

final class TLightGridInjectionCS extends GLSLProgram {

    TLightGridInjectionCS(String prefix, int threadGroupSize, boolean bLightLinkedListCulling){
        CharSequence source = null;

        try {
            source = ShaderLoader.loadShaderFile(prefix + "LightGridInjectionCS.comp", false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ShaderSourceItem item = new ShaderSourceItem(source, ShaderType.COMPUTE);
        item.macros = new Macro[]{
                new Macro("THREADGROUP_SIZE", threadGroupSize),
                new Macro("USE_LINKED_CULL_LIST", bLightLinkedListCulling?1:0),
                new Macro("LOCAL_LIGHT_DATA_STRIDE", FForwardLocalLightData.SIZE/ Vector4f.SIZE),
                new Macro("LIGHT_LINK_STRIDE", 2),

        };
        setSourceFromStrings(item);
    }
}
