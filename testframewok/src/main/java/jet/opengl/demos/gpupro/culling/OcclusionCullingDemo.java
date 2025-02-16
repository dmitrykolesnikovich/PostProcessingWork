package jet.opengl.demos.gpupro.culling;

import com.nvidia.developer.opengl.app.NvCameraMotionType;
import com.nvidia.developer.opengl.app.NvInputDeviceType;
import com.nvidia.developer.opengl.app.NvPointerActionType;
import com.nvidia.developer.opengl.app.NvPointerEvent;
import com.nvidia.developer.opengl.app.NvSampleApp;
import com.nvidia.developer.opengl.models.DrawMode;
import com.nvidia.developer.opengl.models.GLVAO;
import com.nvidia.developer.opengl.models.ModelGenerator;
import com.nvidia.developer.opengl.models.QuadricBuilder;
import com.nvidia.developer.opengl.models.QuadricMesh;
import com.nvidia.developer.opengl.models.QuadricSphere;
import com.nvidia.developer.opengl.ui.NvUIText;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import java.io.IOException;

import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.shader.VisualDepthTextureProgram;
import jet.opengl.postprocessing.texture.SamplerDesc;
import jet.opengl.postprocessing.texture.SamplerUtils;
import jet.opengl.postprocessing.texture.Texture2D;
import jet.opengl.postprocessing.util.BoundingBox;
import jet.opengl.postprocessing.util.LogUtil;
import jet.opengl.postprocessing.util.Numeric;
import jet.opengl.postprocessing.util.Recti;

public final class OcclusionCullingDemo extends NvSampleApp {
    private Renderer mRenderer;
    private Scene mScene;
    private  OcclusionTester mCulling;

    private GLVAO mSphereVao;
    private GLVAO mCubeVao;

    // reflection variables.
    private boolean mOcclusionEnabled;
    private boolean mFineOcclusion;
    private boolean mShowHZBuffer;
    private boolean mEnablePick;
    private boolean mLastEnablePick;
    private int mHZBSlice;

    private NvUIText mCullingInfo;
    private VisualDepthTextureProgram mVisualTexture;
    private int m_PointSampler;

    private RenderInput mInput = new RenderInput();
    private RenderOutput mOutput = new RenderOutput();
    private boolean m_IsTouchDwon;
    private final Recti mPickRect = new Recti();

    private GLFuncProvider gl;
    @Override
    public void initUI() {
        mTweakBar.addValue("Occlusion Enabled", createControl("mOcclusionEnabled"));
        mTweakBar.addValue("Fine Occlusion", createControl("mFineOcclusion"));
        mTweakBar.addValue("Visual HZB", createControl("mShowHZBuffer"));
        mTweakBar.addValue("HZB Slice", createControl("mHZBSlice"), 0, 9);
        mTweakBar.addValue("Enable Picking", createControl("mEnablePick"));
    }

    @Override
    protected void initRendering() {
        gl = GLFuncProviderFactory.getGLFuncProvider();
        mScene = new Scene();
        buildBaseMesh();
        buildModles();
        mScene.buildMeshInformations();

        mRenderer = new ForwardRenderer();
        mRenderer.onCreate();

        m_transformer.setMotionMode(NvCameraMotionType.FIRST_PERSON);
        m_transformer.setTranslation(1,1,1);
        mPickRect.zero();
//        mCulling = new HZBOcclusionTester();
//        mCulling = new HZBOcclusionUE4Profie();
        mCulling = new DawnCulling();

        try {
            mVisualTexture = new VisualDepthTextureProgram(false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(m_PointSampler == 0){
            SamplerDesc desc = new SamplerDesc();
            desc.minFilter = desc.magFilter = GLenum.GL_NEAREST;
            m_PointSampler = SamplerUtils.createSampler(desc);
            desc.minFilter = GLenum.GL_NEAREST_MIPMAP_NEAREST;
//            m_PointMipmapSampler = SamplerUtils.createSampler(desc);
        }
        getGLContext().setSwapInterval(0);
//        mCulling = new DawnCulling();

        mInput.pickedRenderType = PickedRenderType.Silhouette;
    }

    @Override
    protected void reshape(int width, int height) {
        if(width <=0 || height <=0)
            return;
        mScene.onResize(width, height);
        mRenderer.onResize(width, height);
    }

    @Override
    public void display() {
        mScene.updateCamera(m_transformer);

        mRenderer.mFrameNumber = getFrameCount();

        if(mLastEnablePick != mEnablePick){
            mPickRect.zero();
        }

        boolean isDeferredCulling = (mCulling instanceof HZBOcclusionUE4Profie);
        HZBOcclusionUE4Profie UE4Culling = null;
        if(isDeferredCulling)
            UE4Culling = (HZBOcclusionUE4Profie)mCulling;

        boolean isSceneRendered = false;
        if(mOcclusionEnabled){
            if(!isDeferredCulling) {
                mCulling.newFrame(getFrameCount());
                mCulling.cullingCoarse(mRenderer, mScene);
                mInput.clearFBO = true;
                mRenderer.renderSolid(mScene, mInput, mOutput);

                if (mFineOcclusion) {
                    mCulling.cullingFine(mRenderer, mScene);

                    mInput.clearFBO = false;
                    mRenderer.renderSolid(mScene, mInput, mOutput);
                }

                isSceneRendered = true;

            }else{
                if(UE4Culling.isValid()){
                    UE4Culling.getResults(mScene);
                }
            }
        }

        if(!isSceneRendered) {
            mInput.clearFBO = true;
            mRenderer.renderSolid(mScene, mInput, mOutput);
        }

        if(mOcclusionEnabled && isDeferredCulling){
            UE4Culling.newFrame(getFrameCount());
            UE4Culling.cullingCoarse(mRenderer, mScene);
            gl.glViewport(0,0, getGLContext().width(), getGLContext().height());
        }

        mRenderer.present();
        GLCheck.checkError();

        if(mShowHZBuffer && mOcclusionEnabled){
            if(UE4Culling != null){
                visualTexture(UE4Culling.getHZBSlice(mHZBSlice));
            }else if(mCulling instanceof HZBOcclusionTester){
                visualTexture(((HZBOcclusionTester)mCulling).getHZBSlice(mHZBSlice));
            }else{
                visualTexture(((DawnCulling)mCulling).getHZBSlice());
            }
        }

        mLastEnablePick = mEnablePick;
        if(mInput.pickType !=PickType.None){
            mInput.pickType = PickType.None;
            LogUtil.e(LogUtil.LogType.DEFAULT, "Selected object count: " + mOutput.pickResults.size());
        }
    }

    private void visualTexture(Texture2D tex){
        if(tex == null || !mOcclusionEnabled)
            return;

        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
        gl.glBindFramebuffer(GLenum.GL_FRAMEBUFFER, 0);
        gl.glDisable(GLenum.GL_DEPTH_TEST);
        gl.glDisable(GLenum.GL_CULL_FACE);
        gl.glDisable(GLenum.GL_BLEND);
        gl.glViewport(getGLContext().width()/2, getGLContext().height()/2, getGLContext().width()/2, getGLContext().height()/2);

        gl.glBindTextureUnit(0, tex.getTexture());
        gl.glBindSampler(0, m_PointSampler);
        mVisualTexture.enable();
        mVisualTexture.setUniforms(0.1f, 100, 0, 1);
        gl.glBindVertexArray(0);
        gl.glDrawArrays(GLenum.GL_TRIANGLES, 0, 3);
        gl.glBindTextureUnit(0, 0);

        gl.glViewport(0,0, getGLContext().width(), getGLContext().height());
        gl.glBindSampler(0, 0);
    }

    @Override
    public boolean handlePointerInput(NvInputDeviceType device, int action, int modifiers, int count, NvPointerEvent[] points) {
        if(!mEnablePick)
            return false;

        float x = points[0].m_x;
        float y = getGLContext().height() -  points[0].m_y;

        if(action == NvPointerActionType.DOWN && !m_IsTouchDwon){
            m_IsTouchDwon = true;
            mPickRect.addPoint((int)(x+0.5),(int)(y+0.5));
        }else if(action == NvPointerActionType.UP){
            m_IsTouchDwon  = false;
            mPickRect.addPoint((int)(x+0.5),(int)(y+0.5));
        }

        if(!m_IsTouchDwon && mPickRect.isValid()){
            mInput.pickType = (mPickRect.width == 1 && mPickRect.height == 1) ? PickType.Single : PickType.Rect;
            mInput.pickRect.x = mPickRect.x;
            mInput.pickRect.y = mPickRect.y;
            mInput.pickRect.z = mPickRect.getMaxX();
            mInput.pickRect.w = mPickRect.getMaxY();

            mPickRect.zero();
        }

       return m_IsTouchDwon;
    }

    private void buildBaseMesh(){
        QuadricBuilder builder = new QuadricBuilder();
        builder.setXSteps(30).setYSteps(30);
        builder.setPostionLocation(0);
        builder.setNormalLocation(1);
        builder.setTexCoordLocation(2);
        builder.setDrawMode(DrawMode.FILL);
        builder.setCenterToOrigin(true);
        mSphereVao = new QuadricMesh(builder, new QuadricSphere(1)).getModel().genVAO();
        mCubeVao = ModelGenerator.genCube(2, true, true, false).genVAO();
    }

    private void buildModles(){
        Model model = new Model();
        model.mMaterial = new Material();
        model.mMaterial.mColor.set(0.7f, 0.8f, 0.6f, 1);



        Numeric.setRandomSeed(1000000);
        for (int i = 0; i < 100; i++){
            float rnd = Numeric.random();
            MeshType type  = rnd < 0.5 ? MeshType.Cube : MeshType.Sphere;
            Mesh mesh = new Mesh(i);
            mesh.mVao = type == MeshType.Cube ? mCubeVao : mSphereVao;
            mesh.mType = type;
            mesh.count = 1;
            mesh.mAABB.set(new Vector3f(-1,-1,-1),new Vector3f(1,1,1));
            mesh.mWorld.translate(Numeric.random(-10,10),Numeric.random(-10,10),Numeric.random(-10,10));
            mesh.mWorld.scale(Numeric.random(0.001f, 10),Numeric.random(0.001f, 10),Numeric.random(0.001f, 10));
            BoundingBox.transform(mesh.mWorld, mesh.mAABB, mesh.mAABB);

            model.mMeshes.add(mesh );
        }

        mScene.mModels.add(model);
    }

}
