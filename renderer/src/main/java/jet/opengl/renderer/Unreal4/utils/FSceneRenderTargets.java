package jet.opengl.renderer.Unreal4.utils;

import org.lwjgl.util.vector.Vector2i;
import org.lwjgl.util.vector.Vector4f;

import java.util.ArrayList;

import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.texture.Texture2D;
import jet.opengl.postprocessing.texture.Texture2DDesc;
import jet.opengl.postprocessing.texture.TextureGL;
import jet.opengl.postprocessing.util.Numeric;
import jet.opengl.renderer.Unreal4.FSceneRenderer;
import jet.opengl.renderer.Unreal4.scenes.FSceneViewFamily;
import jet.opengl.renderer.Unreal4.FViewInfo;
import jet.opengl.renderer.Unreal4.UE4Engine;
import jet.opengl.renderer.Unreal4.api.ERHIFeatureLevel;
import jet.opengl.renderer.Unreal4.api.ERenderTargetLoadAction;
import jet.opengl.renderer.Unreal4.api.EShadingPath;
import jet.opengl.renderer.Unreal4.api.ESimpleRenderTargetMode;
import jet.opengl.renderer.Unreal4.api.ETranslucencyVolumeCascade;
import jet.opengl.renderer.Unreal4.api.FExclusiveDepthStencil;
import jet.opengl.renderer.Unreal4.api.FRHIRenderPassInfo;
import jet.opengl.renderer.Unreal4.api.FRHIRenderTargetView;

public class FSceneRenderTargets {
    /** Number of cube map shadow depth surfaces that will be created and used for rendering one pass point light shadows. */
    static final int NumCubeShadowDepthSurfaces = 5;

    /**
     * Allocate enough sets of translucent volume textures to cover all the cascades,
     * And then one more which will be used as a scratch target when doing ping-pong operations like filtering.
     */
    static final int NumTranslucentVolumeRenderTargetSets = (ETranslucencyVolumeCascade.TVC_MAX + 1);

    /** Function returning current translucency lighting volume dimensions. */
    public static int GetTranslucencyLightingVolumeDim(int GTranslucencyLightingVolumeDim) {
//        extern int32 GTranslucencyLightingVolumeDim;
        return Numeric.clamp(GTranslucencyLightingVolumeDim, 4, 2048);
    }

    /** Function to select the index of the volume texture that we will hold the final translucency lighting volume texture */
    public static int SelectTranslucencyVolumeTarget(int InCascade, boolean GUseTranslucencyVolumeBlur) {
        if (GUseTranslucencyVolumeBlur)
        {
            switch (InCascade)
            {
                case ETranslucencyVolumeCascade.TVC_Inner:
                {
                    return 2;
                }
                case ETranslucencyVolumeCascade.TVC_Outer:
                {
                    return 0;
                }
                default:
                {
                    // error
//                    check(false);
                    return 0;
                }
            }
        }
        else
        {
            switch (InCascade)
            {
                case ETranslucencyVolumeCascade.TVC_Inner:
                {
                    return 0;
                }
                case ETranslucencyVolumeCascade.TVC_Outer:
                {
                    return 1;
                }
                default:
                {
                    // error
//                    check(false);
                    return 0;
                }
            }
        }
    }

/** Number of surfaces used for translucent shadows. */
    public static final int NumTranslucencyShadowSurfaces = 2;

    /*
     * Stencil layout during basepass / deferred decals:
     *		BIT ID    | USE
     *		[0]       | sandbox bit (bit to be use by any rendering passes, but must be properly reset to 0 after using)
     *		[1]       | unallocated
     *		[2]       | unallocated
     *		[3]       | Temporal AA mask for translucent object.
     *		[4]       | Lighting channels
     *		[5]       | Lighting channels
     *		[6]       | Lighting channels
     *		[7]       | primitive receive decal bit
     *
     * After deferred decals, stencil is cleared to 0 and no longer packed in this way, to ensure use of fast hardware clears and HiStencil.
     */
    public static final int STENCIL_SANDBOX_BIT_ID	= 0,
            STENCIL_TEMPORAL_RESPONSIVE_AA_BIT_ID = 3,
            STENCIL_LIGHTING_CHANNELS_BIT_ID =	4,
            STENCIL_RECEIVE_DECAL_BIT_ID = 		7;

// Outputs a compile-time constant stencil's bit mask ready to be used
// in TStaticDepthStencilState<> template parameter. It also takes care
// of masking the Value macro parameter to only keep the low significant
// bit to ensure to not overflow on other bits.
//            #define GET_STENCIL_BIT_MASK(BIT_NAME,Value) uint8((uint8(Value) & uint8(0x01)) << (STENCIL_##BIT_NAME##_BIT_ID))
//
//            #define STENCIL_SANDBOX_MASK GET_STENCIL_BIT_MASK(SANDBOX,1)
//
//    #define STENCIL_TEMPORAL_RESPONSIVE_AA_MASK GET_STENCIL_BIT_MASK(TEMPORAL_RESPONSIVE_AA,1)
//
//    #define STENCIL_LIGHTING_CHANNELS_MASK(Value) uint8((Value & 0x7) << STENCIL_LIGHTING_CHANNELS_BIT_ID)
//
//            #define PREVENT_RENDERTARGET_SIZE_THRASHING (PLATFORM_DESKTOP || PLATFORM_XBOXONE || PLATFORM_PS4 || PLATFORM_ANDROID || PLATFORM_IOS || PLATFORM_SWITCH)
    enum  ESceneColorFormatType
    {
        Mobile,
        HighEnd,
        HighEndWithAlpha,
    }

    /** size of the back buffer, in editor this has to be >= than the biggest view port */
    private final Vector2i BufferSize = new Vector2i();
    /* Size of the first view, used for multiview rendertargets */
    private final Vector2i View0Size = new Vector2i();
    private final Vector2i SeparateTranslucencyBufferSize = new Vector2i();
    private float SeparateTranslucencyScale;
    /** e.g. 2 */
    private int SmallColorDepthDownsampleFactor = 2;
    /** Whether to use SmallDepthZ for occlusion queries. */
    private boolean bUseDownsizedOcclusionQueries = true;
    /** To detect a change of the CVar r.GBufferFormat */
    private int CurrentGBufferFormat;
    /** To detect a change of the CVar r.SceneColorFormat */
    private int CurrentSceneColorFormat;
    /** To detect a change of the mobile scene color format */
    private int CurrentMobileSceneColorFormat = GLenum.GL_NONE;
    /** Whether render targets were allocated with static lighting allowed. */
    private boolean bAllowStaticLighting = true;
    /** To detect a change of the CVar r.Shadow.MaxResolution */
    private int CurrentMaxShadowResolution;
    /** To detect a change of the CVar r.Shadow.RsmResolution*/
    private int CurrentRSMResolution;
    /** To detect a change of the CVar r.TranslucencyLightingVolumeDim */
    private int CurrentTranslucencyLightingVolumeDim = 64;
    /** To detect a change of the CVar r.MobileHDR / r.MobileHDR32bppMode */
    private int CurrentMobile32bpp;
    /** To detect a change of the CVar r.MobileMSAA or r.MSAA */
    private int CurrentMSAACount;
    /** To detect a change of the CVar r.Shadow.MinResolution */
    private int CurrentMinShadowResolution;
    /** To detect a change of the CVar r.LightPropagationVolume */
    private boolean bCurrentLightPropagationVolume;
    /** Feature level we were initialized for */
    private int CurrentFeatureLevel = ERHIFeatureLevel.Num;
    /** Shading path that we are currently drawing through. Set when calling Allocate at the start of a scene render. */
    private EShadingPath CurrentShadingPath = EShadingPath.Unkown;

    private boolean bRequireSceneColorAlpha;

    // Set this per frame since there might be cases where we don't need an extra GBuffer
    private boolean bAllocateVelocityGBuffer;

    /** Helpers to track gbuffer state on platforms that need to propagate clear information across parallel rendering boundaries. */
    private boolean bGBuffersFastCleared;

    /** Helpers to track scenedepth state on platforms that need to propagate clear information across parallel rendering boundaries. */
    private boolean bSceneDepthCleared;

    /** true is this is a snapshot on the scene allocator */
    private boolean bSnapshot;

    /** Clear color value, defaults to FClearValueBinding::Black */
    private FClearValueBinding DefaultColorClear = FClearValueBinding.Black;

    /** Clear depth value, defaults to FClearValueBinding::DepthFar */
    private FClearValueBinding DefaultDepthClear = FClearValueBinding.DepthFar;

    /** Helpers to track the bound index of the quad overdraw UAV. Needed because UAVs overlap RTs in DX11 */
    private int QuadOverdrawIndex = UE4Engine.INDEX_NONE;

    /** All outstanding snapshots */
    private final ArrayList<FSceneRenderTargets> Snapshots = new ArrayList<>();

    /** True if the depth target is allocated by an HMD plugin. This is a temporary fix to deal with HMD depth target swap chains not tracking the stencil SRV. */
    private boolean bHMDAllocatedDepthTarget;

    /** used by AdjustGBufferRefCount */
    private int GBufferRefCount;

    /** as we might get multiple BufferSize requests each frame for SceneCaptures and we want to avoid reallocations we can only go as low as the largest request */
    private static final int FrameSizeHistoryCount = 3;
    private final Vector2i[] LargestDesiredSizes = new Vector2i[FrameSizeHistoryCount];
//#if PREVENT_RENDERTARGET_SIZE_THRASHING
    // bit 0 - whether any scene capture rendered
    // bit 1 - whether any reflection capture rendered
    private final byte[] HistoryFlags = new byte[FrameSizeHistoryCount];
//#endif

    /** to detect when LargestDesiredSizeThisFrame is outdated */
    private int ThisFrameNumber;
    private int CurrentDesiredSizeIndex;

    private boolean bVelocityPass;
    private boolean bSeparateTranslucencyPass;

    // 0 before BeginRenderingSceneColor and after tone mapping in deferred shading
    // Permanently allocated for forward shading
    private final TextureGL[] SceneColor = new TextureGL[ESceneColorFormatType.values().length];
    // Light Attenuation is a low precision scratch pad matching the size of the scene color buffer used by many passes.
    private TextureGL LightAttenuation;

    // Light Accumulation is a high precision scratch pad matching the size of the scene color buffer used by many passes.
    public TextureGL LightAccumulation;

    // Reflection Environment: Bringing back light accumulation buffer to apply indirect reflections
    public Texture2D DirectionalOcclusion;

    // Scene depth and stencil.
    public Texture2D SceneDepthZ;
    public Texture2D SceneStencilSRV;
    // Scene velocity.
    public Texture2D SceneVelocity;

    public TextureGL LightingChannels;
    // Mobile without frame buffer fetch (to get depth from alpha).
    public Texture2D SceneAlphaCopy;
    // Auxiliary scene depth target. The scene depth is resolved to this surface when targeting SM4.
    public Texture2D AuxiliarySceneDepthZ;
    // Quarter-sized version of the scene depths
    public Texture2D SmallDepthZ;

    // GBuffer: Geometry Buffer rendered in base pass for deferred shading, only available between AllocGBufferTargets() and FreeGBufferTargets()
    public Texture2D GBufferA;
    public Texture2D GBufferB;
    public Texture2D GBufferC;
    public Texture2D GBufferD;
    public Texture2D GBufferE;

    // DBuffer: For decals before base pass (only temporarily available after early z pass and until base pass)
    public TextureGL DBufferA;
    public TextureGL DBufferB;
    public TextureGL DBufferC;
    public TextureGL DBufferMask;

    // for AmbientOcclusion, only valid for a short time during the frame to allow reuse
    public TextureGL ScreenSpaceAO;
    // for shader/quad complexity, the temporary quad descriptors and complexity.
    public TextureGL QuadOverdrawBuffer;
    // used by the CustomDepth material feature, is allocated on demand or if r.CustomDepth is 2
    public TextureGL CustomDepth;
    public TextureGL MobileCustomStencil;
    // used by the CustomDepth material feature for stencil
    public TextureGL CustomStencilSRV;
    // optional in case this RHI requires a color render target (adjust up if necessary)
    public final TextureGL[] OptionalShadowDepthColor = new TextureGL[4];

    /** 2 scratch cubemaps used for filtering reflections. */
    public final TextureGL[] ReflectionColorScratchCubemap = new TextureGL[2];

    /** Temporary storage during SH irradiance map generation. */
    public final TextureGL[] DiffuseIrradianceScratchCubemap = new TextureGL[2];

    /** Temporary storage during SH irradiance map generation. */
    public TextureGL SkySHIrradianceMap;

    /** Volume textures used for lighting translucency. */
    public final ArrayList<TextureGL> TranslucencyLightingVolumeAmbient = new ArrayList<>();
    public final ArrayList<TextureGL> TranslucencyLightingVolumeDirectional = new ArrayList<>();

    /** Color and depth texture arrays for mobile multi-view */
    public TextureGL MobileMultiViewSceneColor;
    public TextureGL MobileMultiViewSceneDepthZ;

    /** Color and opacity for editor primitives (i.e editor gizmos). */
    public TextureGL EditorPrimitivesColor;

    /** Depth for editor primitives */
    public TextureGL EditorPrimitivesDepth;

    /** ONLY for snapshots!!! this is a copy of the SeparateTranslucencyRT from the view state. */
    public TextureGL SeparateTranslucencyRT;
    /** Downsampled depth used when rendering translucency in smaller resolution. */
    public TextureGL DownsampledTranslucencyDepthRT;

    // todo: free ScreenSpaceAO so pool can reuse
    public boolean bScreenSpaceAOIsValid;

    // todo: free ScreenSpaceAO so pool can reuse
    public boolean bCustomDepthIsValid;

    protected   FSceneRenderTargets(){}

    protected FSceneRenderTargets(FViewInfo InView, FSceneRenderTargets SnapshotSource){
        throw new UnsupportedOperationException();
    }

    /** Singletons. At the moment parallel tasks get their snapshot from the rhicmdlist */
    public static FSceneRenderTargets Get(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

//    public static FSceneRenderTargets Get(/*FRHICommandListImmediate& RHICmdList*/);
//    public static FSceneRenderTargets Get(/*FRHIAsyncComputeCommandListImmediate& RHICmdList*/);

    // this is a placeholder, the context should come from somewhere. This is very unsafe, please don't use it!
    public static FSceneRenderTargets GetGlobalUnsafe(){
        throw new UnsupportedOperationException();
    }
    // As above but relaxed checks and always gives the global FSceneRenderTargets. The intention here is that it is only used for constants that don't change during a frame. This is very unsafe, please don't use it!
    public static FSceneRenderTargets Get_FrameConstantsOnly(){
        throw new UnsupportedOperationException();
    }

    /** Create a snapshot on the scene allocator */
    public FSceneRenderTargets CreateSnapshot(FViewInfo InView){
        throw new UnsupportedOperationException();
    }
    /** Set a snapshot on the TargetCmdList */
    public void SetSnapshotOnCmdList(/*FRHICommandList& TargetCmdList*/){
        throw new UnsupportedOperationException();
    }
    /** Destruct all snapshots */
    public void DestroyAllSnapshots(){
        throw new UnsupportedOperationException();
    }

    /**
     * Checks that scene render targets are ready for rendering a view family of the given dimensions.
     * If the allocated render targets are too small, they are reallocated.
     */
    public void Allocate(/*FRHICommandListImmediate& RHICmdList,*/ FSceneRenderer SceneRenderer){
        throw new UnsupportedOperationException();
    }

    public void AllocSceneColor(/*FRHICommandList RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /**
     *
     */
    public void SetBufferSize(int InBufferSizeX, int InBufferSizeY){
        throw new UnsupportedOperationException();
    }

    public void SetSeparateTranslucencyBufferSize(boolean bAnyViewWantsDownsampledSeparateTranslucency){
        throw new UnsupportedOperationException();
    }

    public void SetQuadOverdrawUAV(/*FRHICommandList& RHICmdList,*/ boolean bBindQuadOverdrawBuffers, boolean bClearQuadOverdrawBuffers, FRHIRenderPassInfo Info){
        throw new UnsupportedOperationException();
    }


    public void BindVirtualTextureFeedbackUAV(FRHIRenderPassInfo RPInfo){
        throw new UnsupportedOperationException();
    }

    public void BeginRenderingGBuffer(/*FRHICommandList& RHICmdList,*/ ERenderTargetLoadAction ColorLoadAction, ERenderTargetLoadAction DepthLoadAction, int DepthStencilAccess, boolean bBindQuadOverdrawBuffers, boolean bClearQuadOverdrawBuffers /*= false*/, Vector4f ClearColor /*= FLinearColor(0, 0, 0, 1)*/, boolean bIsWireframe/*=false*/){
        throw new UnsupportedOperationException();
    }

    public void FinishGBufferPassAndResolve(/*FRHICommandListImmediate& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the scene color target and restores its contents if necessary
     */
    public final void BeginRenderingSceneColor(){
        BeginRenderingSceneColor(ESimpleRenderTargetMode.EUninitializedColorExistingDepth, FExclusiveDepthStencil.DepthWrite_StencilWrite, true);
    }

    /**
     * Sets the scene color target and restores its contents if necessary
     */
    public final void BeginRenderingSceneColor(/*FRHICommandList& FRHICommandListImmediate,*/ ESimpleRenderTargetMode RenderTargetMode /*= ESimpleRenderTargetMode::EUninitializedColorExistingDepth*/){
        BeginRenderingSceneColor(RenderTargetMode, FExclusiveDepthStencil.DepthWrite_StencilWrite, true);
    }

    /**
     * Sets the scene color target and restores its contents if necessary
     */
    public final void BeginRenderingSceneColor(/*FRHICommandList& FRHICommandListImmediate,*/ ESimpleRenderTargetMode RenderTargetMode /*= ESimpleRenderTargetMode::EUninitializedColorExistingDepth*/, int DepthStencilAccess /*=FExclusiveDepthStencil::DepthWrite_StencilWrite*/){
        BeginRenderingSceneColor(RenderTargetMode, DepthStencilAccess, true);
    }

    /**
     * Sets the scene color target and restores its contents if necessary
     */
    public void BeginRenderingSceneColor(/*FRHICommandList& FRHICommandListImmediate,*/ ESimpleRenderTargetMode RenderTargetMode /*= ESimpleRenderTargetMode::EUninitializedColorExistingDepth*/, int DepthStencilAccess /*=FExclusiveDepthStencil::DepthWrite_StencilWrite*/, boolean bTransitionWritable /*= true*/){
        throw new UnsupportedOperationException();
    }
    public void FinishRenderingSceneColor(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    // @return true: call FinishRenderingCustomDepth after rendering, false: don't render it, feature is disabled
    public boolean BeginRenderingCustomDepth(/*FRHICommandListImmediate& RHICmdList,*/ boolean bPrimitives){
        throw new UnsupportedOperationException();
    }
    // only call if BeginRenderingCustomDepth() returned true
    public void FinishRenderingCustomDepth(/*FRHICommandListImmediate& RHICmdList,*/ FResolveRect ResolveRect /*= FResolveRect()*/){
        throw new UnsupportedOperationException();
    }

    /** Binds the appropriate shadow depth cube map for rendering. */
    public void BeginRenderingCubeShadowDepth(/*FRHICommandList& RHICmdList,*/ int ShadowResolution){
        throw new UnsupportedOperationException();
    }

    /** Begin rendering translucency in the scene color. */
    public void BeginRenderingTranslucency(/*FRHICommandList& RHICmdList,*/ FViewInfo View, FSceneRenderer Renderer, boolean bFirstTimeThisFrame /*= true*/){
        throw new UnsupportedOperationException();
    }

    public void FinishRenderingTranslucency(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /** Begin rendering translucency in a separate (offscreen) buffer. This can be any translucency pass. */
    public void BeginRenderingSeparateTranslucency(/*FRHICommandList& RHICmdList,*/ FViewInfo View, FSceneRenderer Renderer, boolean bFirstTimeThisFrame){
        throw new UnsupportedOperationException();
    }

    public void ResolveSeparateTranslucency(/*FRHICommandList& RHICmdList,*/ FViewInfo View){
        throw new UnsupportedOperationException();
    }

    public void FreeSeparateTranslucency()
    {
//        SeparateTranslucencyRT.SafeRelease();
//        check(!SeparateTranslucencyRT);

        if(SeparateTranslucencyRT != null){
            SeparateTranslucencyRT.dispose();
            SeparateTranslucencyRT = null;
        }
    }

    public void FreeDownsampledTranslucencyDepth()
    {
        /*if (DownsampledTranslucencyDepthRT.GetReference())
        {
            DownsampledTranslucencyDepthRT.SafeRelease();
        }*/

        if(DownsampledTranslucencyDepthRT != null){
            DownsampledTranslucencyDepthRT.dispose();
            DownsampledTranslucencyDepthRT = null;
        }
    }

    public void ResolveSceneDepthTexture(/*FRHICommandList& RHICmdList,*/ FResolveRect ResolveRect){

    }

    public void ResolveSceneDepthToAuxiliaryTexture(/*FRHICommandList& RHICmdList*/){

    }

    public void BeginRenderingPrePass(/*FRHICommandList& RHICmdList,*/ boolean bPerformClear){

    }
    public void FinishRenderingPrePass(/*FRHICommandListImmediate& RHICmdList*/){

    }

    public void BeginRenderingSceneAlphaCopy(/*FRHICommandListImmediate& RHICmdList*/){
        throw new UnsupportedOperationException();
    }
    public void FinishRenderingSceneAlphaCopy(/*FRHICommandListImmediate& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    public void BeginRenderingLightAttenuation(/*FRHICommandList& RHICmdList,*/ boolean bClearToWhite /*= false*/){
        throw new UnsupportedOperationException();
    }

    public void FinishRenderingLightAttenuation(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    public void SetDefaultColorClear(FClearValueBinding ColorClear)
    {
        DefaultColorClear = ColorClear;
    }

    public void SetDefaultDepthClear(FClearValueBinding DepthClear)
    {
        DefaultDepthClear = DepthClear;
    }

    public FClearValueBinding GetDefaultDepthClear()
    {
        return DefaultDepthClear;
    }

    public float GetSeparateTranslucencyDimensions(Vector2i OutScaledSize/*, float& OutScale*/)
    {
        OutScaledSize.set(SeparateTranslucencyBufferSize);
        return SeparateTranslucencyScale;
    }

    /** Separate translucency buffer can be downsampled or not (as it is used to store the AfterDOF translucency) */
    public TextureGL GetSeparateTranslucency(/*FRHICommandList& RHICmdList, */Vector2i Size){
        throw new UnsupportedOperationException();
    }

    public boolean IsDownsampledTranslucencyDepthValid()
    {
        return DownsampledTranslucencyDepthRT != null;
    }

    public TextureGL GetDownsampledTranslucencyDepth(/*FRHICommandList& RHICmdList,*/ Vector2i Size){
        throw new UnsupportedOperationException();
    }

    public TextureGL GetDownsampledTranslucencyDepthSurface()
    {
        return DownsampledTranslucencyDepthRT; //(const FTexture2DRHIRef&)DownsampledTranslucencyDepthRT->GetRenderTargetItem().TargetableTexture;
    }

    /**
     * Cleans up editor primitive targets that we no longer need
     */
    public void CleanUpEditorPrimitiveTargets(){
        throw new UnsupportedOperationException();
    }

    /**
     * Affects the render quality of the editor 3d objects. MSAA is needed if >1
     * @return clamped to reasonable numbers
     */
    public int GetEditorMSAACompositingSampleCount() {
        throw new UnsupportedOperationException();
    }

    /**
     * Affects the render quality of the scene. MSAA is needed if >1
     * @return clamped to reasonable numbers
     */
    public static short GetNumSceneColorMSAASamples(/*ERHIFeatureLevel::Type*/int InFeatureLevel){
        throw new UnsupportedOperationException();
    }

    public boolean IsStaticLightingAllowed() { return bAllowStaticLighting; }

    /**
     * Gets the editor primitives color target/shader resource.  This may recreate the target
     * if the msaa settings dont match
     */
    public Texture2D GetEditorPrimitivesColor(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the editor primitives depth target/shader resource.  This may recreate the target
     * if the msaa settings dont match
     */
    public Texture2D GetEditorPrimitivesDepth(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }


    // FRenderResource interface.
    public void ReleaseDynamicRHI(){
        throw new UnsupportedOperationException();
    }

    // Texture Accessors -----------

    public TextureGL GetSceneColorTexture(){
        throw new UnsupportedOperationException();
    }
    public TextureGL GetSceneColorTextureUAV(){
        throw new UnsupportedOperationException();
    }

    public Texture2D GetSceneAlphaCopyTexture()  {
//        return (const FTexture2DRHIRef&)SceneAlphaCopy->GetRenderTargetItem().ShaderResourceTexture;
        return (Texture2D) SceneAlphaCopy;
    }

    public boolean HasSceneAlphaCopyTexture()  { return SceneAlphaCopy/*.GetReference()*/ != null; }
    public Texture2D GetSceneDepthTexture()  {
//        return (const FTexture2DRHIRef&)SceneDepthZ->GetRenderTargetItem().ShaderResourceTexture;
        return SceneDepthZ;
    }

    public Texture2D GetAuxiliarySceneDepthTexture()
    {
//        check(!GSupportsDepthFetchDuringDepthTest);
//        return (const FTexture2DRHIRef&)AuxiliarySceneDepthZ->GetRenderTargetItem().ShaderResourceTexture;
        return AuxiliarySceneDepthZ;
    }

    public Texture2D GetActualDepthTexture() {
        throw new UnsupportedOperationException();
    }

    public Texture2D GetGBufferATexture()  { return GBufferA; /*(const FTexture2DRHIRef&)GBufferA->GetRenderTargetItem().ShaderResourceTexture;*/ }
    public Texture2D GetGBufferBTexture()  { return GBufferB; /*(const FTexture2DRHIRef&)GBufferB->GetRenderTargetItem().ShaderResourceTexture;*/ }
    public Texture2D GetGBufferCTexture()  { return GBufferC; /*(const FTexture2DRHIRef&)GBufferC->GetRenderTargetItem().ShaderResourceTexture;*/ }
    public Texture2D GetGBufferDTexture()  { return GBufferD; /*(const FTexture2DRHIRef&)GBufferD->GetRenderTargetItem().ShaderResourceTexture;*/ }
    public Texture2D GetGBufferETexture()  { return GBufferE; /*(const FTexture2DRHIRef&)GBufferE->GetRenderTargetItem().ShaderResourceTexture;*/ }
    public Texture2D GetGBufferVelocityTexture()  { return SceneVelocity; /*(const FTexture2DRHIRef&)SceneVelocity->GetRenderTargetItem().ShaderResourceTexture;*/ }

    public Texture2D GetLightAttenuationTexture()
    {
//        return *(FTextureRHIRef*)&GetLightAttenuation()->GetRenderTargetItem().ShaderResourceTexture;
        return GetLightAttenuation();
    }

    public Texture2D GetSceneColorSurface() {
        throw new UnsupportedOperationException();
    }
    public Texture2D GetSceneAlphaCopySurface() 						{ return SceneAlphaCopy; /*(const FTexture2DRHIRef&)SceneAlphaCopy->GetRenderTargetItem().TargetableTexture;*/ }
    public Texture2D GetSceneDepthSurface() 							{ return SceneDepthZ; /*(const FTexture2DRHIRef&)SceneDepthZ->GetRenderTargetItem().TargetableTexture;*/ }
    public Texture2D GetSmallDepthSurface() 							{ return SmallDepthZ; /*(const FTexture2DRHIRef&)SmallDepthZ->GetRenderTargetItem().TargetableTexture;*/ }
    public Texture2D GetOptionalShadowDepthColorSurface(/*FRHICommandList& RHICmdList,*/ int Width, int Height) {
        throw new UnsupportedOperationException();
    }

    public Texture2D GetLightAttenuationSurface() 					{ return GetLightAttenuation(); /*(const FTexture2DRHIRef&)GetLightAttenuation()->GetRenderTargetItem().TargetableTexture;*/ }
    public Texture2D GetAuxiliarySceneDepthSurface()
    {
//        check(!GSupportsDepthFetchDuringDepthTest);
        return AuxiliarySceneDepthZ; //(const FTexture2DRHIRef&)AuxiliarySceneDepthZ->GetRenderTargetItem().TargetableTexture;
    }

    public Texture2D GetDirectionalOcclusionTexture()
    {
        return DirectionalOcclusion; //(const FTexture2DRHIRef&)DirectionalOcclusion->GetRenderTargetItem().TargetableTexture;
    }

    public int GetQuadOverdrawIndex() { return QuadOverdrawIndex; }

    // @return can be 0 if the feature is disabled
    public Texture2D RequestCustomDepth(/*FRHICommandListImmediate& RHICmdList,*/ boolean bPrimitives){
        throw new UnsupportedOperationException();
    }

    public static boolean IsCustomDepthPassWritingStencil(){
        throw new UnsupportedOperationException();
    }

    // ---

    /** */
    public boolean UseDownsizedOcclusionQueries() { return bUseDownsizedOcclusionQueries; }

    // ---

//    template<int32 NumRenderTargets>
//    static void ClearVolumeTextures(FRHICommandList& RHICmdList, ERHIFeatureLevel::Type FeatureLevel, FTextureRHIParamRef* RenderTargets, const FLinearColor* ClearColors);

    public void ClearTranslucentVolumeLighting(/*FRHICommandListImmediate& RHICmdList,*/ int ViewIndex){
        throw new UnsupportedOperationException();
    }

    /** Get the current translucent ambient lighting volume texture. Can vary depending on whether volume filtering is enabled */
    public TextureGL GetTranslucencyVolumeAmbient(int Cascade, boolean GUseTranslucencyVolumeBlur, int ViewIndex /*= 0*/)
    {
        return TranslucencyLightingVolumeAmbient.get(SelectTranslucencyVolumeTarget(Cascade, GUseTranslucencyVolumeBlur) + ViewIndex * NumTranslucentVolumeRenderTargetSets);
    }

    /** Get the current translucent directional lighting volume texture. Can vary depending on whether volume filtering is enabled */
    public TextureGL GetTranslucencyVolumeDirectional(int Cascade, boolean GUseTranslucencyVolumeBlur, int ViewIndex /*= 0*/) {
        return TranslucencyLightingVolumeDirectional.get(SelectTranslucencyVolumeTarget(Cascade, GUseTranslucencyVolumeBlur) + ViewIndex * NumTranslucentVolumeRenderTargetSets);
    }

    /** Returns the size of most screen space render targets e.g. SceneColor, SceneDepth, GBuffer, ... might be different from final RT or output Size because of ScreenPercentage use. */
    public Vector2i GetBufferSizeXY() { return BufferSize; }
    /** */
    public int GetSmallColorDepthDownsampleFactor() { return SmallColorDepthDownsampleFactor; }
    /** Returns an index in the range [0, NumCubeShadowDepthSurfaces) given an input resolution. */
    public int GetCubeShadowDepthZIndex(int ShadowResolution) {
        throw new UnsupportedOperationException();
    }
    /** Returns the appropriate resolution for a given cube shadow index. */
    public int GetCubeShadowDepthZResolution(int ShadowIndex) {
        throw new UnsupportedOperationException();
    }
    /** Returns the size of the shadow depth buffer, taking into account platform limitations and game specific resolution limits. */
    public Vector2i GetShadowDepthTextureResolution() {
        throw new UnsupportedOperationException();
    }
    // @return >= 1x1 <= GMaxShadowDepthBufferSizeX x GMaxShadowDepthBufferSizeY
    public Vector2i GetPreShadowCacheTextureResolution()  {
        throw new UnsupportedOperationException();
    }
    public Vector2i GetTranslucentShadowDepthTextureResolution()  {
        throw new UnsupportedOperationException();
    }

    public int GetTranslucentShadowDownsampleFactor()  { return 2; }

    /** Returns the size of the RSM buffer, taking into account platform limitations and game specific resolution limits. */
    public int GetReflectiveShadowMapResolution() { return CurrentRSMResolution; }

    public int GetNumGBufferTargets() {
        throw new UnsupportedOperationException();
    }

    public int GetMSAACount()  { return CurrentMSAACount; }

    public boolean HasLightAttenuation() { return LightAttenuation != null; }

    // ---

    // needs to be called between AllocSceneColor() and ReleaseSceneColor()
	public Texture2D GetSceneColor(){
        throw new UnsupportedOperationException();
    }

//    TRefCountPtr<IPooledRenderTarget>& GetSceneColor();

    public int GetSceneColorFormat(int InFeatureLevel) {
        throw new UnsupportedOperationException();
    }
    public int GetSceneColorFormat() {
        throw new UnsupportedOperationException();
    }
    public int GetDesiredMobileSceneColorFormat() {
        throw new UnsupportedOperationException();
    }
    public int GetMobileSceneColorFormat() {
        throw new UnsupportedOperationException();
    }


    // changes depending at which part of the frame this is called
    public boolean IsSceneColorAllocated() {
        throw new UnsupportedOperationException();
    }

    public void SetSceneColor(TextureGL In){
        throw new UnsupportedOperationException();
    }

    // ---

    public void SetLightAttenuation(TextureGL In){
        throw new UnsupportedOperationException();
    }

    // needs to be called between AllocSceneColor() and SetSceneColor(0)
	public Texture2D GetLightAttenuation(){
        throw new UnsupportedOperationException();
    }


    // ---

    // allows to release the GBuffer once post process materials no longer need it
    // @param 1: add a reference, -1: remove a reference
    public void AdjustGBufferRefCount(/*FRHICommandList& RHICmdList,*/ int Delta){
        throw new UnsupportedOperationException();
    }

    public void PreallocGBufferTargets(){
        throw new UnsupportedOperationException();
    }
    public void GetGBufferADesc(Texture2DDesc Desc){
        throw new UnsupportedOperationException();
    }
    public void AllocGBufferTargets(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    public void AllocLightAttenuation(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    public void AllocateReflectionTargets(/*FRHICommandList& RHICmdList,*/ int TargetSize){
        throw new UnsupportedOperationException();
    }

    public void AllocateLightingChannelTexture(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    public void AllocateDebugViewModeTargets(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    public void AllocateScreenShadowMask(/*FRHICommandList& RHICmdList,*/ TextureGL ScreenShadowMaskTexture){
        throw new UnsupportedOperationException();
    }

    public TextureGL GetReflectionBrightnessTarget(){
        throw new UnsupportedOperationException();
    }

    public boolean IsSeparateTranslucencyPass() { return bSeparateTranslucencyPass; }

    // Can be called when the Scene Color content is no longer needed. As we create SceneColor on demand we can make sure it is created with the right format.
    // (as a call to SetSceneColor() can override it with a different format)
    public void ReleaseSceneColor(){

    }

    public int GetCurrentFeatureLevel() { return CurrentFeatureLevel; }

    /**
     * Initializes the editor primitive color render target
     */
    private void InitEditorPrimitivesColor(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /**
     * Initializes the editor primitive depth buffer
     */
    private void InitEditorPrimitivesDepth(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /** Allocates render targets for use with the mobile path. */
    private void AllocateMobileRenderTargets(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /** Allocates render targets for use with the deferred shading path. */
    // Temporarily Public to call from DefferedShaderRenderer to attempt recovery from a crash until cause is found.
    public void AllocateDeferredShadingPathRenderTargets(/*FRHICommandListImmediate& RHICmdList,*/ int NumViews /*= 1*/){
        throw new UnsupportedOperationException();
    }

    public final void AllocateDeferredShadingPathRenderTargets(){
        AllocateDeferredShadingPathRenderTargets(1);
    }

    /** Allocates render targets for use with the current shading path. */
    private void AllocateRenderTargets(/*FRHICommandListImmediate& RHICmdList,*/ int NumViews){
        throw new UnsupportedOperationException();
    }

    /** Allocates common depth render targets that are used by both mobile and deferred rendering paths */
    private void AllocateCommonDepthTargets(/*FRHICommandList& RHICmdList*/){
        throw new UnsupportedOperationException();
    }

    /** Determine the appropriate render target dimensions. */
    private Vector2i ComputeDesiredSize(FSceneViewFamily ViewFamily){
        throw new UnsupportedOperationException();
    }

    /** Allocates the mobile multi-view scene color texture array render target. */
    private void AllocMobileMultiViewSceneColor(/*FRHICommandList& RHICmdList,*/ int ScaleFactor){
        throw new UnsupportedOperationException();
    }

    /** Allocates the mobile multi-view depth (no stencil) texture array render target. */
    private void AllocMobileMultiViewDepth(/*FRHICommandList& RHICmdList,*/ int ScaleFactor){
        throw new UnsupportedOperationException();
    }

    // internal method, used by AdjustGBufferRefCount()
    private void ReleaseGBufferTargets(){
        throw new UnsupportedOperationException();
    }

    // release all allocated targets to the pool
    private void ReleaseAllTargets(){
        throw new UnsupportedOperationException();
    }

    /** Get the current scene color target based on our current shading path. Will return a null ptr if there is no valid scene color target  */
    private TextureGL GetSceneColorForCurrentShadingPath()  {
//        return SceneColor[(int32)GetSceneColorFormatType()];
        throw new UnsupportedOperationException();
    }
//    private TextureGL GetSceneColorForCurrentShadingPath() { check(CurrentShadingPath < EShadingPath::Num); return SceneColor[(int32)GetSceneColorFormatType()]; }

    /** Determine whether the render targets for a particular shading path have been allocated */
    private boolean AreShadingPathRenderTargetsAllocated(ESceneColorFormatType InSceneColorFormatType){
        throw new UnsupportedOperationException();
    }

    /** Determine if the default clear values for color and depth match the allocated scene render targets. Mobile only. */
    private boolean AreRenderTargetClearsValid(ESceneColorFormatType InSceneColorFormatType) {
        throw new UnsupportedOperationException();
    }

    /** Determine whether the render targets for any shading path have been allocated */
    private boolean AreAnyShadingPathRenderTargetsAllocated()
    {
        return AreShadingPathRenderTargetsAllocated(ESceneColorFormatType.HighEnd)
                || AreShadingPathRenderTargetsAllocated(ESceneColorFormatType.HighEndWithAlpha)
                || AreShadingPathRenderTargetsAllocated(ESceneColorFormatType.Mobile);
    }

    /** Gets all GBuffers to use.  Returns the number actually used. */
    private int GetGBufferRenderTargets(ERenderTargetLoadAction ColorLoadAction, FRHIRenderTargetView OutRenderTargets[/*RHIDefinitions.MaxSimultaneousRenderTargets*/], int[] OutVelocityRTIndex){
        throw new UnsupportedOperationException();
    }

    /** Fills the given FRenderPassInfo with the current GBuffer */
    private int FillGBufferRenderPassInfo(ERenderTargetLoadAction ColorLoadAction, FRHIRenderPassInfo OutRenderPassInfo, int[] OutVelocityRTIndex){
        throw new UnsupportedOperationException();
    }

    private ESceneColorFormatType GetSceneColorFormatType()
    {
        /*if (CurrentShadingPath == EShadingPath::Mobile)
        {
            return ESceneColorFormatType::Mobile;
        }
        else if (CurrentShadingPath == EShadingPath::Deferred && (bRequireSceneColorAlpha || GetSceneColorFormat() == PF_FloatRGBA))
        {
            return ESceneColorFormatType::HighEndWithAlpha;
        }
        else if (CurrentShadingPath == EShadingPath::Deferred && !bRequireSceneColorAlpha)
        {
            return ESceneColorFormatType::HighEnd;
        }

        check(0);
        return ESceneColorFormatType::Num;*/
        throw new UnsupportedOperationException();
    }

    static FResolveRect GetDefaultRect(FResolveRect Rect, int DefaultWidth, int DefaultHeight){
        throw new UnsupportedOperationException();
    }

    static void ResolveDepthTexture(/*FRHICommandList& RHICmdList,*/ TextureGL SourceTexture, TextureGL DestTexture, FResolveParams ResolveParams){
        throw new UnsupportedOperationException();
    }

}
