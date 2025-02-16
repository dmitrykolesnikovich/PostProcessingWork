package jet.opengl.postprocessing.core.ssao;

import java.io.IOException;
import java.util.Arrays;

import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.core.PostProcessingParameters;
import jet.opengl.postprocessing.core.PostProcessingRenderContext;
import jet.opengl.postprocessing.core.PostProcessingRenderPass;
import jet.opengl.postprocessing.core.PostProcessingRenderPassOutputTarget;
import jet.opengl.postprocessing.texture.SamplerDesc;
import jet.opengl.postprocessing.texture.SamplerUtils;
import jet.opengl.postprocessing.texture.Texture2D;
import jet.opengl.postprocessing.texture.Texture2DDesc;
import jet.opengl.postprocessing.texture.TextureUtils;
import jet.opengl.postprocessing.util.LogUtil;

/**
 * Created by mazhen'gui on 2017-05-11 17:09:06.
 */

final class PostProcessingDeinterleavePass extends PostProcessingRenderPass {

    static final int HBAO_RANDOM_ELEMENTS = 16;
    static final int NUM_MRT = 8;

    private static PostProcessingDeinterleaveProgram g_DeinterleaveProgram = null;
    private static Texture2D m_DepthArray;
    private static final Texture2D[][] m_DepthView = new Texture2D[2][NUM_MRT];
    private final boolean m_bUse32FP;
    private int m_SamplerPointClamp;

    public PostProcessingDeinterleavePass(boolean use32FP) {
        super("Deinterleave");
        m_bUse32FP = use32FP;

        set(1,1);
        setOutputTarget(PostProcessingRenderPassOutputTarget.INTERNAL);
    }

    @Override
    public void process(PostProcessingRenderContext context, PostProcessingParameters parameters) {
        if(g_DeinterleaveProgram == null){
            try {
                g_DeinterleaveProgram = new PostProcessingDeinterleaveProgram();
                addDisposedResource(g_DeinterleaveProgram);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(m_SamplerPointClamp == 0){
            SamplerDesc desc = new SamplerDesc();
            desc.minFilter = desc.magFilter = GLenum.GL_NEAREST;
            m_SamplerPointClamp = SamplerUtils.createSampler(desc);
        }

        Texture2D input0 = getInput(0);
        if(input0 == null){
            LogUtil.e(LogUtil.LogType.DEFAULT, "ReinterleavePass:: Missing depth texture!");
            return;
        }

        int quarterWidth  = ((input0.getWidth()+3)/4);
        int quarterHeight = ((input0.getHeight()+3)/4);

        if(m_DepthArray == null || quarterWidth != m_DepthArray.getWidth() || quarterHeight != m_DepthArray.getHeight()){
            if(m_DepthArray != null)
                m_DepthArray.dispose();

            Texture2DDesc desc = new Texture2DDesc(quarterWidth, quarterHeight, m_bUse32FP? GLenum.GL_R32F:GLenum.GL_R16F);
            desc.arraySize = HBAO_RANDOM_ELEMENTS;
            m_DepthArray = TextureUtils.createTexture2D(desc, null);
            m_DepthArray.setName("Deinterleave");
            for(int i = 0; i < HBAO_RANDOM_ELEMENTS; i++){
                m_DepthView[i/NUM_MRT][i%NUM_MRT] = TextureUtils.createTextureView(m_DepthArray, GLenum.GL_TEXTURE_2D, 0, 1, i, 1);
            }
        }

        for(int i = 0; i < HBAO_RANDOM_ELEMENTS; i+=NUM_MRT){
            context.setViewport(0,0, quarterWidth, quarterHeight);
            context.setVAO(null);
            context.setProgram(g_DeinterleaveProgram);

            context.bindTexture(input0, 0, m_SamplerPointClamp);
            context.setBlendState(null);
            context.setDepthStencilState(null);
            context.setRasterizerState(null);

            g_DeinterleaveProgram.setUVAndResolution((float)(i % 4) + 0.5f, (float)(i / 4) + 0.5f, 1.0f/input0.getWidth(), 1.0f/input0.getHeight());
            context.setRenderTargets(m_DepthView[(i+1)/NUM_MRT]);
            context.drawFullscreenQuad();
        }

        context.bindTexture(input0, 0, 0);  // unbind samplers.
        if(GLCheck.CHECK)
            GLCheck.checkError("DeinterleavePass");
    }

    @Override
    public Texture2D getOutputTexture(int idx) {
        return idx ==0? m_DepthArray: null;
    }

    @Override
    public void computeOutDesc(int index, Texture2DDesc out) {
        Texture2D input = getInput(0);
        if(input != null){
            input.getDesc(out);
            out.format = GLenum.GL_RG16F;
        }

        super.computeOutDesc(index, out);
    }

    @Override
    public void dispose() {
        if(m_DepthArray != null){
            m_DepthArray.dispose();
            m_DepthArray = null;

            Arrays.fill(m_DepthView[0], null);
            Arrays.fill(m_DepthView[1], null);
        }
    }
}
