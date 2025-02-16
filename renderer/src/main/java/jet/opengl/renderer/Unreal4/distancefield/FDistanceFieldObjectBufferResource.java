package jet.opengl.renderer.Unreal4.distancefield;

public class FDistanceFieldObjectBufferResource {
    public final FDistanceFieldCulledObjectBuffers Buffers = new FDistanceFieldCulledObjectBuffers();

    private boolean bInitialized;

    public boolean IsInitialized(){
        return bInitialized;
    }

    public void InitDynamicRHI()
    {
        Buffers.Initialize();
        bInitialized = true;
    }

    public void ReleaseDynamicRHI()
    {
        Buffers.Release();

        bInitialized = false;
    }
}
