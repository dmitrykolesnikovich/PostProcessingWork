package jet.opengl.demos.intel.va;

import org.lwjgl.util.vector.Vector4f;
import org.lwjgl.util.vector.Vector4i;

import java.io.IOException;
import java.util.UUID;

import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.texture.TextureGL;
import jet.opengl.postprocessing.texture.TextureUtils;
import jet.opengl.postprocessing.util.CacheBuffer;
import jet.opengl.postprocessing.util.LogUtil;
import jet.opengl.postprocessing.util.NvImage;

/**
 * Created by mazhen'gui on 2017/11/17.
 */
public final class VaTextureDX11 extends VaTexture{
    private TextureGL         m_resource;
    private int               m_buffer;   // Texture Buffer
    private int               m_texture1D;
    private int               m_texture2D;
    private int               m_texture3D;
    /*ID3D11ShaderResourceView *      m_srv;
    ID3D11RenderTargetView *        m_rtv;
    ID3D11DepthStencilView *        m_dsv;
    ID3D11UnorderedAccessView *     m_uav;*/
    private GLFuncProvider gl;

    protected VaTextureDX11(  VaConstructorParamsBase  params  ){
        super(params);
        gl = GLFuncProviderFactory.getGLFuncProvider();
    }

    boolean  ImportDX11(String storageFilePath, boolean assumeSourceIsInSRGB, boolean dontAutogenerateMIPs, int binds ) throws IOException{
        Destroy();

        int dot = storageFilePath.lastIndexOf('.');
        boolean isDDS = false;
        if(dot > 0){
            if(storageFilePath.endsWith("dds") || storageFilePath.endsWith("DDS")){
                isDDS = true;
            }
        }else{
            //TODO how to do it?
        }


        if(VaFileTools.FileExists(storageFilePath)){
            if(isDDS){
                int textureID = NvImage.uploadTextureFromDDSFile(storageFilePath);
                m_resource = TextureUtils.createTexture2D(GLenum.GL_TEXTURE_2D, textureID); // TODO It must be a Texture2D
            }else{
                m_resource = TextureUtils.createTexture2DFromFile(storageFilePath, true, dontAutogenerateMIPs);
            }
        }else{
            VaFileTools.EmbeddedFileData embeddedFile = VaFileTools.EmbeddedFilesFind("textures:\\" + storageFilePath);
            if(embeddedFile.HasContents()){
                // TODO create a texture from embeddedFileData
            }
        }

        if(m_resource == null){
            LogUtil.e(LogUtil.LogType.DEFAULT, String.format("vaTextureDX11::Import - unable to find or load '%s' texture file!", storageFilePath));
            return false;
        }

        ProcessResource(false );

        return true;

    }

    private static int cast(TextureGL tex){
        return tex != null ? tex.getTexture() : 0;
    }

    @Override
    public void ClearRTV(Vector4f clearValue) {
        int rtv = cast(GetSRV()); if(rtv == 0) return;
        int glFormat = convertFromatToGL(GetResourceFormat());
        gl.glClearTexImage(rtv, 0, TextureUtils.measureFormat(glFormat),
                TextureUtils.measureDataType(glFormat), clearValue != null ? CacheBuffer.wrap(clearValue) : null);
    }

    @Override
    public void ClearUAV(Vector4i clearValue) {
        int rtv = cast(GetUAV()); if(rtv == 0) return;
        int glFormat = convertFromatToGL(GetResourceFormat());
        gl.glClearTexImage(rtv, 0, TextureUtils.measureFormat(glFormat),
                TextureUtils.measureDataType(glFormat), clearValue != null ? CacheBuffer.wrap(clearValue) : null);
    }

    @Override
    public void ClearUAV(Vector4f clearValue) {
        int rtv = cast(GetUAV()); if(rtv == 0) return;
        int glFormat = convertFromatToGL(GetResourceFormat());

        gl.glClearTexImage(rtv, 0, TextureUtils.measureFormat(glFormat),
                TextureUtils.measureDataType(glFormat), clearValue != null ? CacheBuffer.wrap(clearValue) : null);
    }

    @Override
    public void ClearDSV(boolean clearDepth, float depthValue, boolean clearStencil, int stencilValue) {
        int rtv = cast(GetDSV()); if(rtv == 0) return;

        /*gl.glClearTexImage(rtv, 0, TextureUtils.measureFormat(GetResourceFormat()),
                TextureUtils.measureDataType(GetResourceFormat()), clearValue != null ? CacheBuffer.wrap(clearValue) : null);*/
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean Load(VaStream inStream)  throws IOException{
        Destroy( );

        /*int32 fileVersion = 0;
        VERIFY_TRUE_RETURN_ON_FALSE( inStream.ReadValue<int32>( fileVersion ) );*/
        int fileVersion = inStream.ReadInt();

        if( fileVersion != c_fileVersion )
        {
            LogUtil.e(LogUtil.LogType.DEFAULT, "vaRenderMaterial::Load(): unsupported file version");
            return false;
        }

        m_flags = inStream./*ReadValue<vaTextureFlags            >*/ReadInt(  ) ;
        m_accessFlags = inStream./*ReadValue<vaTextureAccessFlags      >*/ReadInt(  );
        m_type = VaTextureType.values()[inStream./*ReadValue<vaTextureType             >*/ReadInt( )];
        m_bindSupportFlags = inStream./*ReadValue<vaTextureBindSupportFlags >*/ReadInt( );
        m_resourceFormat = inStream./*ReadValue<vaTextureFormat           >*/ReadInt( );
        m_srvFormat = inStream./*ReadValue<vaTextureFormat           >*/ReadInt(  );
        m_rtvFormat = inStream./*ReadValue<vaTextureFormat           >*/ReadInt(  );
        m_dsvFormat = inStream./*ReadValue<vaTextureFormat           >*/ReadInt(  );
        m_uavFormat = inStream./*ReadValue<vaTextureFormat           >*/ReadInt(  );
        m_sizeX = inStream.ReadInt(  );
        m_sizeY = inStream.ReadInt(  );
        m_sizeZ = inStream.ReadInt(  );
        m_sampleCount = inStream.ReadInt(  );
        m_mipLevels = inStream.ReadInt(  );

        /*int64 textureDataSize;
        VERIFY_TRUE_RETURN_ON_FALSE( inStream.ReadValue<int64                     >( textureDataSize ) );*/
        int textureDataSize = (int)inStream.ReadLong();

        // direct reading from the stream not implemented yet
        byte[] buffer = new byte[ textureDataSize ];
        if( inStream.Read( buffer ) != textureDataSize )
        {
            assert( false );
            return false;
        }

//        m_resource = vaDirectXTools::LoadTextureDDS( buffer, textureDataSize, false, false );
        NvImage image = new NvImage();
        int textureID = image.uploadTextureFromDDSData(buffer, 0, textureDataSize);
        int target;
        if(image.isCubeMap()){
            target = GLenum.GL_TEXTURE_CUBE_MAP;
        }else if(image.isVolume()){
            target = GLenum.GL_TEXTURE_3D;
        }else{
            target = GLenum.GL_TEXTURE_2D;
        }
        GLCheck.checkError();

        m_resource = TextureUtils.createTexture2D(target, textureID);

        /*if( m_resource == NULL )
        {
            VA_WARN( L"vaTextureDX11::Load - error processing file!" );
            assert( false );

            return false;
        }*/

        ProcessResource(false );

        return true;
    }

    @Override
    public boolean Save(VaStream outStream) throws IOException {
        assert( m_viewedOriginal == null );
        if( m_viewedOriginal != null )
            return false;

        outStream.Write( c_fileVersion );

        outStream.Write( m_flags );

        outStream.Write( m_accessFlags      );
        outStream.Write( m_type.ordinal()   );
        outStream.Write( m_bindSupportFlags );
        outStream.Write( m_resourceFormat   );
        outStream.Write( m_srvFormat        );
        outStream.Write( m_rtvFormat        );
        outStream.Write( m_dsvFormat        );
        outStream.Write( m_uavFormat        );
        outStream.Write( m_sizeX            );
        outStream.Write( m_sizeY            );
        outStream.Write( m_sizeZ            );
        outStream.Write( m_sampleCount      );
        outStream.Write( m_mipLevels        );

        /*int64 posOfSize = outStream.GetPosition( ); TODO how to handle this???
        VERIFY_TRUE_RETURN_ON_FALSE( outStream.WriteValue<int64>( 0 ) );

        VERIFY_TRUE_RETURN_ON_FALSE( vaDirectXTools::SaveDDSTexture( outStream, m_resource ) );

        int64 calculatedSize = outStream.GetPosition( ) - posOfSize;
        outStream.Seek( posOfSize );
        VERIFY_TRUE_RETURN_ON_FALSE( outStream.WriteValue<int64>( calculatedSize - 8 ) );
        outStream.Seek( posOfSize + calculatedSize );

        return true;*/

        throw new UnsupportedOperationException();
    }

    void Destroy( ){
        if(m_resource != null){
            m_resource.dispose();
        }
    }

    public TextureGL         GetResource( )          { return m_resource; }
    public int               GetTexture1D( )         { return m_texture1D; }
    public int               GetTexture2D( )         { return m_texture2D; }
    public int               GetTexture3D( )         { return m_texture3D; }
    public TextureGL         GetSRV( )               { return (GetBindSupportFlags() & BSF_ShaderResource) != 0 ? m_resource : null; }
    public TextureGL         GetRTV( )               { return (GetBindSupportFlags() & BSF_RenderTarget) != 0 ? m_resource : null; }
    public TextureGL         GetDSV( )               { return (GetBindSupportFlags() & BSF_DepthStencil) != 0 ? m_resource : null; }
    public TextureGL         GetUAV( )               { return (GetBindSupportFlags() & BSF_UnorderedAccess) != 0 ? m_resource : null; }

    public static  VaTexture CreateWrap(TextureGL resource
            /*, int srvFormat = vaTextureFormat::Unknown, vaTextureFormat rtvFormat = vaTextureFormat::Unknown, vaTextureFormat dsvFormat = vaTextureFormat::Unknown, vaTextureFormat uavFormat = vaTextureFormat::Unknown*/ ){
        int resourceFormat = convertFromatToDX(resource.getFormat());

        VaTexture newTexture = VaRenderingModuleRegistrar.CreateModuleTyped("vaTexture", new VaTextureConstructorParams(UUID.randomUUID()));
        newTexture.Initialize(0, resourceFormat);
        ((VaTextureDX11)newTexture).SetResource(resource, false);
        return newTexture;
    }

    void SetResource( TextureGL resource, boolean notAllBindViewsNeeded /*=false*/)
    {
        Destroy();
        m_resource = resource;
        ProcessResource( notAllBindViewsNeeded );
    }

    private void ProcessResource( boolean notAllBindViewsNeeded /*= false*/ ){
        InternalUpdateFromRenderingCounterpart( notAllBindViewsNeeded );

        if( ( GetBindSupportFlags( ) & BSF_VertexBuffer ) != 0 )
        {
            assert( false ); // not implemented yet
        }

        if( ( GetBindSupportFlags( ) & BSF_IndexBuffer ) != 0 )
        {
            assert( false ); // not implemented yet
        }

        if( ( GetBindSupportFlags( ) & BSF_ConstantBuffer ) != 0 )
        {
            assert( false ); // not implemented yet
        }

        if( ( GetBindSupportFlags( ) & BSF_ConstantBuffer ) != 0 )
        {
            assert( false ); // not implemented yet
        }

        /*if( ( GetBindSupportFlags( ) & vaTextureBindSupportFlags::ShaderResource ) != 0 )  TODO I don't kown how to handle this
        {
            // not the cleanest way to do this - should probably get updated and also assert on _TYPELESS
            if( GetSRVFormat() == vaTextureFormat::Unknown )
                m_srvFormat = m_resourceFormat;
            m_srv = vaDirectXTools::CreateShaderResourceView( m_resource, (DXGI_FORMAT)GetSRVFormat(), m_viewedMipSlice );
        }

        if( ( GetBindSupportFlags( ) & vaTextureBindSupportFlags::RenderTarget ) != 0 )
        {
            // not the cleanest way to do this - should probably get updated and also assert on _TYPELESS
            if( GetRTVFormat( ) == vaTextureFormat::Unknown )
                m_rtvFormat = m_resourceFormat;
            m_rtv = vaDirectXTools::CreateRenderTargetView( m_resource, (DXGI_FORMAT)GetRTVFormat(), m_viewedMipSlice, m_viewedArraySlice );
        }

        if( ( GetBindSupportFlags( ) & vaTextureBindSupportFlags::DepthStencil ) != 0 )
        {
            // non-0 mip levels not supported at the moment
            assert( m_viewedMipSlice == -1 );

            // not the cleanest way to do this - should probably get updated and also assert on _TYPELESS
            if( GetDSVFormat( ) == vaTextureFormat::Unknown )
                m_dsvFormat = m_resourceFormat;
            m_dsv = vaDirectXTools::CreateDepthStencilView( m_resource, (DXGI_FORMAT)GetDSVFormat() );
        }

        if( ( GetBindSupportFlags( ) & vaTextureBindSupportFlags::UnorderedAccess ) != 0 )
        {
            // non-0 mip levels not supported at the moment
            assert( m_viewedMipSlice == -1 );

            // not the cleanest way to do this - should probably get updated and also assert on _TYPELESS
            if( GetRTVFormat( ) == vaTextureFormat::Unknown )
                m_uavFormat = m_resourceFormat;
            m_uav = vaDirectXTools::CreateUnorderedAccessView( m_resource, (DXGI_FORMAT)GetUAVFormat() );
        }*/

        if( ( GetBindSupportFlags( ) & BSF_CreateAutoMipViews ) != 0 )
        {
            assert( false ); // not implemented yet
        }
    }

    @Override
    public void dispose() {
        Destroy();
    }
}
