package jet.opengl.demos.nvidia.lightning;

import com.nvidia.developer.opengl.app.NvCameraMotionType;
import com.nvidia.developer.opengl.app.NvKeyActionType;
import com.nvidia.developer.opengl.app.NvSampleApp;
import com.nvidia.developer.opengl.utils.FieldControl;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import jet.opengl.demos.amdfx.common.AMD_Mesh;
import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;

/**
 * Created by mazhen'gui on 2017/8/31.
 */

public final class LightningDemo extends NvSampleApp {
    Arena	g_arena = null;
    final LightningAppearance g_beam_parameters = new LightningAppearance();
    private GLFuncProvider gl;
    private static boolean g_printLog;

    final Vector3f g_colors[] =
    {
            new Vector3f(1.0f,0.0f,0.0f),
            new Vector3f(0.0f,1.0f,0.0f),
            new Vector3f(0.0f,0.0f,1.0f),
            new Vector3f(0.0f,1.0f,1.0f),
            new Vector3f(1.0f,0.0f,1.0f),
            new Vector3f(1.0f,1.0f,0.0f),
            new Vector3f(0.0f,0.0f,0.0f),
            new Vector3f(1.0f,1.0f,1.0f),

            new Vector3f(.5f,0.0f,0.0f),
            new Vector3f(0.0f,.5f,0.0f),
            new Vector3f(0.0f,0.0f,.5f),
            new Vector3f(0.0f,.5f,.5f),
            new Vector3f(.5f,0.0f,.5f),
            new Vector3f(.5f,.5f,0.0f),
            new Vector3f(.5f,.5f,.5f),
    };

    final Matrix4f m_proj = new Matrix4f();
    final Matrix4f m_view = new Matrix4f();
    float m_total_time;

    static void loadOrcXMesh(){
        String root = "nvidia\\PerlinFire\\models\\";
        String[] tokens = {"bonfire_wOrcs"};
        String[] exts = {".X"};


        for(int i = 0; i < tokens.length; i++){
            String token =tokens[i];
            String ext = exts[i];
            AMD_Mesh mesh = new AMD_Mesh();
            mesh.Create(root, token + ext, true);
        }
    }

    @Override
    public void initUI() {
        mTweakBar.addValue("Show Scene", new FieldControl(g_arena.settings, "Scene"));
        mTweakBar.addValue("Chain", new FieldControl(g_arena.settings, "Chain"));
        mTweakBar.addValue("Fence", new FieldControl(g_arena.settings, "Fence"));
        mTweakBar.addValue("CoilHelix", new FieldControl(g_arena.settings, "CoilHelix"));
        mTweakBar.addValue("InterCoil", new FieldControl(g_arena.settings, "InterCoil"));
        mTweakBar.addValue("Glow", new FieldControl(g_arena.settings, "Glow"));
        mTweakBar.addValue("Lines", new FieldControl(g_arena.settings, "Lines"));
    }

    @Override
    protected void initRendering() {
//        loadOrcXMesh();
        GLCheck.checkError();

        gl = GLFuncProviderFactory.getGLFuncProvider();
        m_transformer.setTranslation(-44.5418f, -53.0726f, 42.1582f);
        m_transformer.setMotionMode(NvCameraMotionType.FIRST_PERSON);
        m_transformer.setRotationVec(new Vector3f(0.7f, -2.2f, -1.0f));

        g_arena = new Arena(1);
        getGLContext().setSwapInterval(0);
    }

    @Override
    public void display() {
//        ID3D10RenderTargetView* pRTV = DXUTGetD3D10RenderTargetView();
//        pd3dDevice->ClearRenderTargetView( pRTV, ClearColor );

        // Clear the depth stencil
//        ID3D10DepthStencilView* pDSV = DXUTGetD3D10DepthStencilView();
//        pd3dDevice->ClearDepthStencilView( pDSV, D3D10_CLEAR_DEPTH, 1.0, 0 );

        // If the settings dialog is being shown, then render it instead of rendering the app's scene
//        if( g_SettingsDlg.IsActive() )
//        {
//            g_SettingsDlg.OnRender( fElapsedTime );
//            return;
//        }

        float  t = m_total_time;
        float dt = getFrameDeltaTime();

//        D3DXMATRIX view = *g_Camera.GetWorldMatrix() * *g_Camera.GetViewMatrix();
//        D3DXMATRIX world;
//        D3DXMatrixIdentity(&world);
        m_transformer.getModelViewMat(m_view);


//        g_beam_parameters.BoltWidth
//                (
//                        0.5f  * (50 + g_SampleUI.GetSlider(IDC_BOLT_WIDTH)->GetValue()) / 100.0f ,
//                0.5f  * (50 + g_SampleUI.GetSlider(IDC_BOLT_WIDTH_FALLOFF)->GetValue()) / 100.0f
//        );
        g_beam_parameters.BoltWidth.set(.5f,.5f);
        g_beam_parameters.ColorInside.set(1,1,1);
        g_beam_parameters.ColorOutside.set(g_colors[/*g_SampleUI.GetSlider(IDC_COLOR)->GetValue()*/0]);

        g_beam_parameters.ColorFallOffExponent = 5; // float(g_SampleUI.GetSlider(IDC_COLOR_EXPONENT)->GetValue());

        /*g_arena.settings.Scene =  true;
        g_arena.settings.Chain =  true;
        g_arena.settings.Fence =  true;
        g_arena.settings.CoilHelix =  true;
        g_arena.settings.InterCoil =  true;
        g_arena.settings.Glow =  true;
        g_arena.settings.Lines =  true;*/

//        g_arena.settings.BlurSigma = D3DXVECTOR3
//                (
//                        g_SampleUI.GetSlider(IDC_BLUR_SIGMA_R)->GetValue() / 100.0f ,
//                g_SampleUI.GetSlider(IDC_BLUR_SIGMA_G)->GetValue() / 100.0f ,
//                g_SampleUI.GetSlider(IDC_BLUR_SIGMA_B)->GetValue() / 100.0f
//        );
        g_arena.settings.BlurSigma.set(.9f,.9f,.9f);
        g_arena.settings.Beam.set(g_beam_parameters);
        g_arena.settings.AnimationSpeed =  15; //float(g_SampleUI.GetSlider(IDC_ANIMATION_SPEED)->GetValue());

        g_arena.Matrices(m_view, m_proj);
        g_arena.Time(t,dt);
        g_arena.Render();
        m_total_time += dt;

        /*if(g_screen_capture || g_single_frame)
        {
            IDXGISwapChain* pSwapChain = DXUTGetDXGISwapChain();

            ID3D10Texture2D* pRT;

            pSwapChain->GetBuffer(0, __uuidof(pRT), reinterpret_cast<void**>(&pRT));

            DXUTGetD3D10Device()->ResolveSubresource(g_pResolvedBuffer,0,pRT,0,DXUTGetDXGIBackBufferSurfaceDesc()->
            Format);
            WCHAR filename[MAX_PATH];

            if(g_single_frame)
            {
                D3DX10SaveTextureToFile(g_pResolvedBuffer, D3DX10_IFF_PNG, L"last_frame.png");
                g_single_frame = false;
                MessageBox(DXUTGetHWND(),L"Frame saved",L"Hint",MB_OK);
            }
            else
            {
                StringCchPrintf(filename, MAX_PATH, L"screenshot%.5d.bmp",g_Frame);
                D3DX10SaveTextureToFile(g_pResolvedBuffer, D3DX10_IFF_BMP, filename);
            }

            pRT->Release();

            ++g_Frame;

        }
        if( g_SampleUI.GetCheckBox(IDC_SHOW_TEXT)->GetChecked())
        {
            RenderText();
        }

        if(g_render_hud)
        {
            g_HUD.OnRender( fElapsedTime );
            g_SampleUI.OnRender( fElapsedTime );
        }*/

        g_printLog = true;
    }

    static boolean canPrintLog(){
        return !g_printLog;
    }

    @Override
    protected void reshape(int width, int height) {
        if(width <=0 || height <= 0){
            return;
        }

        float fAspectRatio = (float)width/height;
        Matrix4f.perspective(60, fAspectRatio, 0.1f, 1000.0f, m_proj);

        if(null != g_arena)
            g_arena.RenderTargetResize(width, height);
    }

    @Override
    public boolean handleKeyInput(int code, NvKeyActionType action) {
        return super.handleKeyInput(code, action);
    }
}
