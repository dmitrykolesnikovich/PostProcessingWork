package jet.opengl.postprocessing.core.light;

import java.io.IOException;

import jet.opengl.postprocessing.core.PostProcessingParameters;
import jet.opengl.postprocessing.core.PostProcessingRenderContext;
import jet.opengl.postprocessing.core.PostProcessingRenderPass;
import jet.opengl.postprocessing.texture.RenderTexturePool;
import jet.opengl.postprocessing.texture.Texture2D;
import jet.opengl.postprocessing.texture.Texture2DDesc;

/**
 * Created by mazhen'gui on 2017/5/2.
 */

final class PostProcessingLightStreakerPass extends PostProcessingRenderPass{

    static final ColorModulation g_ColorModulation = new ColorModulation();
    private static PostProcessingLightStreakerProgram g_LightStreakerProgram = null;
    private static PostProcessingLightStreakerComposeProgram g_CombineProgram = null;

    private Texture2D m_TempTexture;
//    private final boolean startSteaker;

    public PostProcessingLightStreakerPass() {
        super("LightStreaker");
//        this.startSteaker = startSteaker;

        set(1, 1);
    }

    @Override
    public void process(PostProcessingRenderContext context, PostProcessingParameters parameters) {
        if(g_LightStreakerProgram == null){
            try {
                g_LightStreakerProgram = new PostProcessingLightStreakerProgram();
                addDisposedResource(g_LightStreakerProgram);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Texture2D input0 = getInput(0);
        if(input0 == null){
            return;
        }

        final RenderTexturePool pool = RenderTexturePool.getInstance();
        Texture2D output0 = pool.findFreeElement(input0.getWidth(), input0.getHeight(), input0.getFormat());
        Texture2D output1 = pool.findFreeElement(input0.getWidth(), input0.getHeight(), input0.getFormat());
        Texture2D output2 = null;
        Texture2D output3 = null;

        m_TempTexture = pool.findFreeElement(input0.getWidth(), input0.getHeight(), input0.getFormat());

        if(parameters.isStartStreaker()){
            output2 = pool.findFreeElement(input0.getWidth(), input0.getHeight(), input0.getFormat());
            output3 = pool.findFreeElement(input0.getWidth(), input0.getHeight(), input0.getFormat());

//            float ratio = (float) output0.getWidth() / output0.getHeight();
            float ratio = 16.0f/9.0f;
            genStarStreak(context, 0, ratio, output0);
            genStarStreak(context, 1, ratio, output1);
            genStarStreak(context, 2, ratio, output2);
            genStarStreak(context, 3, ratio, output3);
        }else{

            genHorizontalGlare(context, 0, output0);
            genHorizontalGlare(context, 1, output1);
        }
        pool.freeUnusedResource(m_TempTexture);
        m_TempTexture = null;

        // Compose Streakers and output
        composeStreaker(context, output0, output1, output2, output3);

        pool.freeUnusedResource(output0);
        pool.freeUnusedResource(output1);
        if(parameters.isStartStreaker()) {
            pool.freeUnusedResource(output2);
            pool.freeUnusedResource(output3);
        }
    }

    private void composeStreaker(PostProcessingRenderContext context, Texture2D input0, Texture2D input1, Texture2D input2, Texture2D input3){
        if(g_CombineProgram == null){
            try {
                g_CombineProgram = new PostProcessingLightStreakerComposeProgram();
                addDisposedResource(g_CombineProgram);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Texture2D output = getOutputTexture(0);

        context.setViewport(0,0, output.getWidth(), output.getHeight());
        context.setVAO(null);
        context.setProgram(g_CombineProgram);

        context.bindTexture(input0, 0, 0);
        context.bindTexture(input1, 1, 0);
        context.bindTexture(input2, 2, 0);
        context.bindTexture(input3, 3, 0);

        context.setBlendState(null);
        context.setDepthStencilState(null);
        context.setRasterizerState(null);
        context.setRenderTarget(output);

        context.drawFullscreenQuad();
    }

    private void drawAxisAlignedQuad(PostProcessingRenderContext context, Texture2D output){
        context.setBlendState(null);
        context.setDepthStencilState(null);
        context.setRasterizerState(null);
        context.setRenderTarget(output);
        context.setViewport(0,0, output.getWidth(), output.getHeight());
        context.setVAO(null);

        context.drawFullscreenQuad();
    }

    private void genStarStreak(PostProcessingRenderContext context, final int dir, float ratio, Texture2D output) {
        final float delta = 0.9f;
        int n,s,w,h;
//    	float[] step = new float[2];
        float step0 = 0, step1 = 0;
        float stride = 1.0f;
//        Dimension size = starSize;
        w = output.getWidth();
        h = output.getHeight();

        Texture2D compose_buffer = getInput(0);

        float m_aspectRatio = ratio;
        switch (dir) {
            case 0:
                step1 = (delta)/w*m_aspectRatio;
                step0 = (delta)/w;
                break;
            case 1:
                step1 = (delta)/w*m_aspectRatio;
                step0 = -(delta)/w;
                break;
            case 2:
                step1 = -(delta)/w*m_aspectRatio;
                step0 = (delta)/w;
                break;
            case 3:
                step1 = -(delta)/w*m_aspectRatio;
                step0 = -(delta)/w;
                break;
            default:
                break;
        }

        //3 passes to generate 64 pixel blur in each direction
        {   // first pass: compose_buffer --> output
            context.bindTexture(compose_buffer, 0,0);
            context.setProgram(g_LightStreakerProgram);
            g_LightStreakerProgram.setUniforms(step0, step1, stride);
            g_LightStreakerProgram.setColorCoeff(g_ColorModulation.hori_passes[ColorModulation.STAR0]);
            drawAxisAlignedQuad(context, output);
        }

        {   // second pass: output --> m_TempTexture
            stride = 4;

            context.bindTexture(output, 0,0);
            context.setProgram(g_LightStreakerProgram);
            g_LightStreakerProgram.setUniforms(step0, step1, stride);
            g_LightStreakerProgram.setColorCoeff(g_ColorModulation.hori_passes[ColorModulation.STAR1]);
            drawAxisAlignedQuad(context, m_TempTexture);
        }

        {   // third pass: m_TempTexture --> output
            stride = 16;

            context.bindTexture(m_TempTexture, 0,0);
            context.setProgram(g_LightStreakerProgram);
            g_LightStreakerProgram.setUniforms(step0, step1, stride);
            g_LightStreakerProgram.setColorCoeff(g_ColorModulation.hori_passes[ColorModulation.STAR2]);
            drawAxisAlignedQuad(context, output);
        }
    }

    private void genHorizontalGlare(PostProcessingRenderContext context, int dir, Texture2D output) {
        final float delta  = 0.9f;
        int n,s,w,h;
        float step0, step1;
        float stride = 1.0f;
        w = output.getWidth();
        h = output.getHeight();

        if (dir==0) {
            step0 = (delta)/w;
        }
        else {
            step0 = -(delta)/w;
        }
        step1 = 0;

        Texture2D compose_buffer = getInput(0);
        //4 passes to generate 256 pixel blur in each direction
        {   // first pass: compose_buffer --> tempTexture
            context.bindTexture(compose_buffer, 0,0);
            context.setProgram(g_LightStreakerProgram);
            g_LightStreakerProgram.setUniforms(step0, step1, stride);
            g_LightStreakerProgram.setColorCoeff(g_ColorModulation.hori_passes[ColorModulation.HORI0]);
            drawAxisAlignedQuad(context, m_TempTexture);
        }

        {   // second pass: m_TempTexture --> ouput
            stride = 4;

            context.bindTexture(m_TempTexture, 0,0);
            context.setProgram(g_LightStreakerProgram);
            g_LightStreakerProgram.setUniforms(step0, step1, stride);
            g_LightStreakerProgram.setColorCoeff(g_ColorModulation.hori_passes[ColorModulation.HORI1]);
            drawAxisAlignedQuad(context, output);
        }

        {   // third pass: ouput --> m_TempTexture
            stride = 16;

            context.bindTexture(output, 0,0);
            context.setProgram(g_LightStreakerProgram);
            g_LightStreakerProgram.setUniforms(step0, step1, stride);
            g_LightStreakerProgram.setColorCoeff(g_ColorModulation.hori_passes[ColorModulation.HORI2]);
            drawAxisAlignedQuad(context, m_TempTexture);
        }

        {   // fourth pass: m_TempTexture --> ouput
            stride = 64;

            context.bindTexture(m_TempTexture, 0,0);
            context.setProgram(g_LightStreakerProgram);
            g_LightStreakerProgram.setUniforms(step0, step1, stride);
            g_LightStreakerProgram.setColorCoeff(g_ColorModulation.hori_passes[ColorModulation.HORI3]);
            drawAxisAlignedQuad(context, output);
        }
    }

    @Override
    public void computeOutDesc(int index, Texture2DDesc out) {
        Texture2D input = getInput(0);
        if(input != null){
            input.getDesc(out);
        }

        super.computeOutDesc(index, out);
    }
}
