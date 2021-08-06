package jet.opengl.postprocessing.core;

import jet.opengl.postprocessing.common.GLAPI;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.texture.Texture2D;

/**
 * Created by mazhen'gui on 2017/4/17.
 */

public class PostProcessingParameters {
    float radialBlurCenterX = 0.5f;
    float radialBlurCenterY = 0.5f;
    int   radialBlurSamples = 24;
    float gloablTime;
    float elapsedTime = 1.0F/60.0F;

    float bloomIntensity;
    float edgeThreshold;
    float edgeThreshold2;
    float fishEyeFactor;

    float grayScreenRectBorder;
    Texture2D paper;

    float bloomThreshold;
    float exposureScale;

    float lumThreshold = 1.0f;
    float lumScalar = 0.3f;

    int fxaaQuality;
    boolean startStreaker = true;
    boolean enableLightStreaker;
    boolean enableLensFlare;
    Texture2D lensMask;

    // light effect parameters
    float blurAmout = 0.33f;
    float expose = 3.3654366f;
    float gamma = 1.0f/1.8f;

    // depth of field bokeh
    float focalDepth;
    float focalLength;
    float fstop;

    float nearTransitionRegion;
    float farTransitionRegion;
    float fieldScale = 0.1f;
    boolean enableNearBlur;
    boolean enableFarBlur;

    PostProcessing postProcessing;

    PostProcessingParameters(PostProcessing postProcessing){
        this.postProcessing = postProcessing;
        if(GLFuncProviderFactory.getGLFuncProvider().getHostAPI() == GLAPI.ANDROID){
            radialBlurSamples = 12;
        }
    }

    public float getLumThreshold() {return lumThreshold;}
    public float getLumScalar()    {return lumScalar;}

    public float getRadialBlurCenterX() {return radialBlurCenterX;}
    public float getRadialBlurCenterY() {return radialBlurCenterY;}
    public int getRadialBlurSamples() {return radialBlurSamples;}
    public float getGlobalTime() {return gloablTime;}
    public float getBloomIntensity() {return bloomIntensity;}

    public float getEdgeThreshold() {return edgeThreshold;}
    public float getEdgeThreshold2() {return edgeThreshold2;}
    public float getFishEyeFactor()  { return fishEyeFactor;}
    public float getRectBorder()  { return grayScreenRectBorder;}

    public float getBloomThreshold()	{ return bloomThreshold; }
    public float getExposureScale() { return exposureScale; }
    public int   getFXAAQuality() { return fxaaQuality;}

    @Deprecated
    public float getElapsedTime() {
        return elapsedTime;
    }
    // TODO Not safe
    public boolean isStartStreaker() {return startStreaker;}

    public Texture2D getLensMask() {return lensMask != null ? lensMask : postProcessing.getOrCreateLensMask();}
    public Texture2D getPaper() {return paper;}

    public float getLightEffectAmout() {return blurAmout;}
    public float getLightEffectExpose() { return expose;}
    public float getGamma() {return gamma;}

    public boolean isLightStreakerEnabled() {return enableLightStreaker;}
    public boolean isLensFlareEnable()      {return enableLensFlare;}

    public float getFocalDepth() {return focalDepth;}
    public float getFocalLength() {return focalLength;}
    public float getFstop()      {return fstop;}

    public float getNearTransitionRegion()  {return nearTransitionRegion;}
    public float getFarTransitionRegion()   {return farTransitionRegion;}
    public float getFieldScale()            {return fieldScale;}

    public boolean isFarBlur() {             return enableFarBlur;}
    public boolean isNearBlur() {            return enableNearBlur;}
}
