package jet.opengl.desktop.lwjgl;

import com.nvidia.developer.opengl.app.NvAppBase;
import com.nvidia.developer.opengl.app.NvEGLConfiguration;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import jet.opengl.demos.gpupro.culling.OcclusionCullingDemo;
import jet.opengl.demos.gpupro.ibl.IndirectLighting;
import jet.opengl.demos.labs.atmosphere.AtmosphereDemo;
import jet.opengl.demos.labs.atmosphere.AtmosphereOriginDemo;
import jet.opengl.demos.labs.scattering.AtmosphereTest;
import jet.opengl.demos.nvidia.fire.PerlinFire;
import jet.opengl.demos.nvidia.shadows.ShadowMapGenerator;
import jet.opengl.demos.nvidia.waves.crest.Wave_Animation_Test;
import jet.opengl.demos.nvidia.waves.crest.Wave_CDClipmap_Test;
import jet.opengl.demos.postprocessing.HBAODemo;
import jet.opengl.demos.postprocessing.OutdoorLightScatteringSample;
import jet.opengl.demos.postprocessing.hdr.HDRDemo;
import jet.opengl.postprocessing.util.FileLoader;
import jet.opengl.postprocessing.util.FileUtils;
import jet.opengl.postprocessing.util.Numeric;

/**
 * Created by mazhen'gui on 2017/4/12.
 */

public class DemoApp {

    static {
        System.setProperty("jet.opengl.postprocessing.debug", "true");
    }

    public static void run(NvAppBase app){
        run(app, LwjglApp.DEFAULT_WIDTH, LwjglApp.DEFAULT_HEIGHT);
    }

    public static void run(NvAppBase app, int width, int height){
        NvEGLConfiguration config = new NvEGLConfiguration();
        app.configurationCallback(config);

        LwjglApp baseApp = new LwjglApp();
        GLContextConfig glconfig = baseApp.getGLContextConfig();
        glconfig.alphaBits = config.alphaBits;
        glconfig.depthBits = config.depthBits;
        glconfig.stencilBits = config.stencilBits;

        glconfig.redBits = config.redBits;
        glconfig.greenBits = config.greenBits;
        glconfig.blueBits = config.blueBits;
        glconfig.debugContext = config.debugContext;
        glconfig.multiSamplers = config.multiSamplers;
        baseApp.setWidth(width);
        baseApp.setHeight(height);
        baseApp.setTile(app.getClass().getSimpleName());
        baseApp.registerGLEventListener(app);
        baseApp.registerGLFWListener(new InputAdapter(app, app, app));
//        baseApp.runTask(DemoApp::testFramebufferRead);
        app.setGLContext(baseApp);
        baseApp.start();
    }

    public static void main(String[] args) {
//        LogUtil.setLoggerLevel(LogUtil.LogType.NV_FRAMEWROK, Level.OFF);
//        LogUtil.setLoggerLevel(LogUtil.LogType.DEFAULT, Level.OFF);

        //获取可用内存
        long value = Runtime.getRuntime().freeMemory();
        System.out.println("The aviable memory:"+value/1024/1024+"mb");
        //获取jvm的总数量，该值会不断的变化
        long  totalMemory = Runtime.getRuntime().totalMemory();
        System.out.println("The total memory:"+totalMemory/1024/1024+"mb");
        //获取jvm 可以最大使用的内存数量，如果没有被限制 返回 Long.MAX_VALUE;
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("The maxmum memory:"+maxMemory/1024/1024+"mb");

        value = Runtime.getRuntime().availableProcessors();
        System.out.println("The CPU cores:" + value);

        final String path = "app\\src\\main\\assets\\";
        FileUtils.setIntenalFileLoader(new FileLoader() {
            @Override
            public InputStream open(String file) throws FileNotFoundException {
                if(file.contains(path)){  // Not safe
                    return new FileInputStream(file);
                }
                return new FileInputStream(path + file);
            }

            @Override
            public String getCanonicalPath(String file) throws IOException {
                if(file.contains(path)){
                    return new File(file).getCanonicalPath();
                }
                return new File(path + file).getCanonicalPath();
            }

            @Override
            public boolean exists(String file) {
                if(file.contains(path)){
                    return new File(file).exists();
                }

                return new File(path + file).exists();
            }

            @Override
            public String resolvePath(String file) {
                if(file.contains(path)){
                    return file;
                }
                return path + file;
            }
        });

//        testParaboloidMatrix();
//        testProjectionZ();  // 4:3 -->[33.962746, -157.41496, -69.76516, -69.55124], z/w = 1.0015378
//        testCamera();
//        NvImage.setAPIVersion(NvGfxAPIVersion.GL4_4);
//        run(new HDRDemo());
//        run(new HBAODemo());
//        run(new ASSAODemoDebug());
//        run(SSAODemoDX11.newInstance());
//        testRectVertex();
//        run(new OutdoorLightScatteringSample());
//        run(new AVSMDemo());
//        run(new ShaderTest());
//        run(new SoftShadowDemo());
//        run(new ShaderNoise());
//        run(new GrayScreenDemo(), 1024, 720);
//        run(new Flight404());
//        run(new LightingVolumeDemo());
//        run(new TestD3D11());
//        run(new Wave_CDClipmap_Test());
//        run(new Wave_Animation_Test());
//        run(new OcclusionCullingDemo());
//        run(new OrderIndependentTransparencyDemo());
//        run(new NvOceanDemo());
//        run(new SampleD3D11());
//        run(new IslandDemo());
//        run(new Chapman());
//        run(new AtmosphereOriginDemo());
//        run(new FaceWorkDemo());
//        run(new FaceWorkTest());
//        run(new VolumetricLightingDemo());
//        run(new ParaboloidShadowDemo());
//        run(new LightPropagationVolumeDemo());
//        run(new AntiAliasingDemo());
//        run(new HybridRendererDemo());
//        run(new CloudSkyDemo());
//        run(new VoxelConeTracingDemo());
//        run(new PerlinFire());
//        run(new VolumetricFogDemo());
//        run(new GeometryFXDemo());
//        run(new TiledLighting11());
//        run(new BindlessTextureSample());
//        run(new DervitiveComputShaderTest());
        run(new AtmosphereDemo());
//        run(new ScreenWaveDemo());
//        run(new VolumetricClouds());
//        run(new IndirectLighting());
    }

    private static void testParaboloidMatrix(){
        Vector3f lightPos = new Vector3f(10, 15, 00);
        final float near = 0.5f;
        final float far = 50.f;

        Vector3f dir = new Vector3f(Numeric.random(-1, 1), Numeric.random(-1, 1), Numeric.random(-1, 1));
        dir.normalise();

        Vector3f pos1 = Vector3f.scale(dir, 0.2f * far, null);
        Vector3f pos2 = Vector3f.scale(dir, 0.5f * far, null);

        Vector4f shadowUV1 = ParaboloidProject(pos1, near, far);
        Vector4f shadowUV2 = ParaboloidProject(pos2, near, far);

        float depth1 = shadowUV1.z * (far - near) + near;
        float depth2 = shadowUV2.z * (far - near) + near;
        Vector3f constructPos1 = Vector3f.scale(dir, depth1, null);
        Vector3f constructPos2 = Vector3f.scale(dir, depth2, null);

        System.out.println("shadowUV1 = " + shadowUV1);
        System.out.println("shadowUV2 = " + shadowUV2);
        System.out.println("constructPos1 = " + constructPos1);
        System.out.println("constructPos2 = " + constructPos2);
        System.out.println("pos1 = " + pos1);
        System.out.println("pos2 = " + pos2);
    }

    static Vector4f ParaboloidProject(Vector3f P, float zNear, float zFar)
    {
        Vector4f outP = new Vector4f();
        outP.w = P.z > 0 ? 1 : 0;
        float z = P.z;
        P.z = Math.abs(P.z);
        float lenP = Vector3f.length(P);
//        outP.xyz = P.xyz/lenP;
        Vector3f.scale(P, 1.0f/lenP, outP);
        outP.x = outP.x / (outP.z + 1);
        outP.y = outP.y / (outP.z + 1);
        outP.z = (lenP - zNear) / (zFar - zNear);
        P.z = z;
        return outP;
    }

    private static void testRectVertex(){
        int mWidth = 1,
            mHeight = 1,
            mSegsH = 1,
            mSegsW = 1;
        int i, j;
        for(i = 0; i <= mSegsH; i++){
            for(j = 0; j <= mSegsW; j++){

                //Vertices
                float v1 = ((float)j/mSegsW - 0.5f)*mWidth;
                float v2 = ((float)i/mSegsH - 0.5f)*mHeight;

                System.out.printf("Vertex: %f, %f.\n", v1, v2);

                //TextureCoords
                float u = (float) j / (float) mSegsW;
                float v = (float) i / (float) mSegsH;

                System.out.printf("Texcoord: %f, %f.\n", u, v);
            }
        }
    }

    private static void testFramebufferRead(){
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, 2, 2, 0, GL11.GL_RED, GL11.GL_FLOAT, new float[]{5,6,7,8});

        int framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);

        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        int buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
        GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, 16, GL15.GL_DYNAMIC_COPY);

        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);

        GL11.glReadPixels(0, 0, 2, 2, GL11.GL_RED, GL11.GL_FLOAT, 0);

        float[] values = new float[4];
        GL15.glGetBufferSubData(GL21.GL_PIXEL_PACK_BUFFER, 0,values);

        System.out.println("Test Read pixels = " + Arrays.toString(values));
    }

    private static void testProjectionZ(){
        final float Z = 100;

        Matrix4f proj = Matrix4f.perspective(60, 4f/3f, 0.1f, 1000f, null);
        Vector4f v = new Vector4f(0,0,Z, 1);
        Vector4f result = new Vector4f();

        for(int i = 0; i < 10; i++){
            v.x = Numeric.random(-100, 100);
            v.y = Numeric.random(-100, 100);

            Matrix4f.transform(proj, v, result);
            float deviceZ = (result.z / result.w) * 0.5f + 0.5f;
            System.out.println(result + ", z/w = " + deviceZ);
        }
    }

    private static void testCamera(){
        final float N = 1;
        final float F = 5000;

        Vector4f out = new Vector4f();
        out.x = F/(F-N);
        out.y =N/(N-F);
        out.z = out.y * F;
        out.w = F;
        System.out.println(out);   // [1.0002, 2.0004001E-4, 1.0002, 5000.0]
        // [1.0002, -2.0004001E-4, -1.0002, 5000.0]

        float viewDepth = 278;
        float r0 = out.z/viewDepth + out.x;
        System.out.println(r0);

        float r1 = (out.x * viewDepth + out.z)/viewDepth;
        System.out.println(r1);

        int gridX = (int) (1/0.00763);
        int gridY = (int) (1/0.0101);
        int gridZ = (int) (1/0.01563);

        System.out.println(gridX + ", " + gridY + ", " + gridZ);
    }
}
