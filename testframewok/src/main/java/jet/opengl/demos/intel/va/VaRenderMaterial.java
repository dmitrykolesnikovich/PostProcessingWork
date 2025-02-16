package jet.opengl.demos.intel.va;

import org.lwjgl.util.vector.Vector4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jet.opengl.postprocessing.common.Disposeable;
import jet.opengl.postprocessing.shader.Macro;
import jet.opengl.postprocessing.util.LogUtil;

/**
 * Created by mazhen'gui on 2017/11/17.
 */

public abstract class VaRenderMaterial extends  VaAssetResource implements VaRenderingModule,Disposeable{

    public static final int
            FaceCull_None = 0,
            FaceCull_Front = 1,
            FaceCull_Back = 2;

    private String __name;
    private final TT_Trackee< VaRenderMaterial > m_trackee;

    protected VaRenderMaterialManager m_renderMaterialManager;

    protected final MaterialSettings m_settings =new MaterialSettings();

    protected VaTexture   m_textureAlbedo;
    protected VaTexture   m_textureNormalmap;
    protected VaTexture   m_textureSpecular;
    protected VaTexture   m_textureEmissive;

    protected UUID m_textureAlbedoUID;
    protected UUID m_textureNormalmapUID;
    protected UUID m_textureSpecularUID;
    protected UUID m_textureEmissiveUID;

    protected String m_shaderFileName;
    protected String m_shaderEntryVS_PosOnly;
    protected String m_shaderEntryPS_DepthOnly;
    protected String m_shaderEntryVS_Standard;
    protected String m_shaderEntryPS_Forward;
    protected String m_shaderEntryPS_Deferred;

    protected final ArrayList<Macro> m_shaderMacros = new ArrayList<>(16);
    protected boolean                                            m_shaderMacrosDirty;
    protected boolean                                             m_shadersDirty;

    protected VaRenderMaterial( VaConstructorParamsBase  params ) {
        super(((VaRenderMaterialConstructorParams)params).UID);

        VaRenderMaterialConstructorParams rmParmas = (VaRenderMaterialConstructorParams)params;
        m_trackee = new TT_Trackee<>(rmParmas.RenderMaterialManager.GetRenderMaterialTracker(), this);
        m_renderMaterialManager = rmParmas.RenderMaterialManager;

        m_shaderFileName            = "vaRenderMesh_%s.%s";
        m_shaderEntryVS_PosOnly     = "VS_PosOnly";
        m_shaderEntryPS_DepthOnly   = "PS_DepthOnly";
        m_shaderEntryVS_Standard    = "VS_Standard";
        m_shaderEntryPS_Forward     = "PS_Forward";
        m_shaderEntryPS_Deferred    = "PS_Deferred";

//        m_shaderMacros.reserve( 16 );
        m_shaderMacrosDirty = true;
        m_shadersDirty = true;

        m_textureAlbedoUID          = VaCore.GUIDNull();
        m_textureNormalmapUID       = VaCore.GUIDNull();
        m_textureSpecularUID        = VaCore.GUIDNull();
        m_textureEmissiveUID        = VaCore.GUIDNull();
    }

    @Override
    public String GetRenderingModuleTypeName() {
        return __name;
    }

    @Override
    public void InternalRenderingModuleSetTypeName(String name) {
        __name = name;
    }

    public MaterialSettings  GetSettings( ) { return m_settings; }
    public void              SetSettings( MaterialSettings settings )  { if( !m_settings.equals(settings) ) m_shaderMacrosDirty = true; m_settings.set(settings); }

    public void              SetSettingsDirty( ) { m_shaderMacrosDirty = true; }

    public VaRenderMaterialManager   GetManager( )                                             { return m_renderMaterialManager; }
    public int                       GetListIndex( )                                           { return m_trackee.GetIndex( ); }

    public VaTexture                   GetTextureAlbedo( )                                        { return m_textureAlbedo;           }
    public VaTexture                   GetTextureNormalmap( )                                     { return m_textureNormalmap;        }
    public VaTexture                   GetTextureSpecular( )                                      { return m_textureSpecular;        }
    public VaTexture                   GetTextureEmissive( )                                      { return m_textureEmissive;        }

    public void   SetTextureAlbedo( VaTexture texture )    { m_textureAlbedo    = texture; m_shaderMacrosDirty = true; m_textureAlbedoUID  = (texture==null)?(null):(texture.UIDObject_GetUID()); }
    public void   SetTextureNormalmap( VaTexture texture )    { m_textureNormalmap = texture; m_shaderMacrosDirty = true; m_textureNormalmapUID = (texture==null)?(null):(texture.UIDObject_GetUID()); }
    public void   SetTextureSpecular(  VaTexture texture )    { m_textureSpecular  = texture; m_shaderMacrosDirty = true; m_textureSpecularUID = (texture==null)?(null):(texture.UIDObject_GetUID()); }
    public void   SetTextureEmissive(  VaTexture texture )    { m_textureEmissive  = texture; m_shaderMacrosDirty = true; m_textureEmissiveUID = (texture==null)?(null):(texture.UIDObject_GetUID()); }

    public boolean                                             GetNeedsPSForShadowGenerate( )                            { return false; }

    public int                                      GetFaceCull( )                                            { return m_settings.FaceCull; }

    @Override
    public void dispose() {
        m_trackee.release();
    }

    private static final int c_renderMeshMaterialFileVersion = 2;

    public boolean Save(VaStream outStream ) throws IOException{
        outStream./*WriteValue<int32>*/Write( c_renderMeshMaterialFileVersion );

        m_settings.Save(outStream);

        outStream.Write(m_textureAlbedo != null?m_textureAlbedo.UIDObject_GetUID():null);
        outStream.Write(m_textureNormalmap != null? m_textureNormalmap.UIDObject_GetUID():null);
        outStream.Write(m_textureSpecular!=null?m_textureSpecular.UIDObject_GetUID():null);
        outStream.Write(m_textureEmissive!=null?m_textureEmissive.UIDObject_GetUID():null);

        String shaderFileName = m_shaderFileName;
        if(m_shaderFileName.equals("vaRenderMesh_%s.%s")){
            shaderFileName = "vaRenderMesh.hlsl";
        }

        outStream.WriteString( shaderFileName );
        outStream.WriteString( m_shaderEntryVS_PosOnly );
        outStream.WriteString( m_shaderEntryPS_DepthOnly );
        outStream.WriteString( m_shaderEntryVS_Standard );
        outStream.WriteString( m_shaderEntryPS_Forward );
        outStream.WriteString( m_shaderEntryPS_Deferred );

        return true;
    }

    public boolean Load( VaStream inStream ) throws IOException{
        /*int32 fileVersion = 0;
        VERIFY_TRUE_RETURN_ON_FALSE( inStream.ReadValue<int32>( fileVersion ) );*/
        int fileVersion = inStream.ReadInt();

        if( !((fileVersion >= 1) && (fileVersion <= c_renderMeshMaterialFileVersion)) )
        {
            LogUtil.i(LogUtil.LogType.DEFAULT, "vaRenderMaterial::Load(): unsupported file version");
            return false;
        }

        if( fileVersion == 1 )
        {
            MaterialSettingsV1 settingsTemp = new MaterialSettingsV1();
//            VERIFY_TRUE_RETURN_ON_FALSE( inStream.ReadValue<MaterialSettingsV1>( settingsTemp ) );
            settingsTemp.Load(inStream);
//            m_settings = MaterialSettings();
            m_settings.FaceCull          = settingsTemp.FaceCull;
            m_settings.ColorMultAlbedo   .set(settingsTemp.ColorMultAlbedo);
            m_settings.ColorMultSpecular .set(settingsTemp.ColorMultSpecular);
            m_settings.AlphaTest         = settingsTemp.AlphaTest;
            m_settings.ReceiveShadows    = settingsTemp.ReceiveShadows;

        }
        else
        {
//            VERIFY_TRUE_RETURN_ON_FALSE( inStream.ReadValue<MaterialSettings>(m_settings ) );
            m_settings.Load(inStream);
        }

        m_textureAlbedoUID = inStream.ReadUUID(     );
        m_textureNormalmapUID = inStream.ReadUUID(     );
        m_textureSpecularUID = inStream.ReadUUID(     );

        if( fileVersion > 1 )
            m_textureEmissiveUID = inStream.ReadUUID();

        m_shaderFileName = inStream.ReadStringW( );
        m_shaderEntryVS_PosOnly = inStream.ReadString( );
        m_shaderEntryPS_DepthOnly = inStream.ReadString( );
        m_shaderEntryVS_Standard = inStream.ReadString( );
        m_shaderEntryPS_Forward = inStream.ReadString( );
        m_shaderEntryPS_Deferred = inStream.ReadString( );

        m_shaderMacrosDirty = true;
        m_shadersDirty = true;

        if(m_shaderFileName.equals("vaRenderMesh.hlsl")){
            m_shaderFileName = "vaRenderMesh_%s.%s";
        }

        return true;
    }

    public void ReconnectDependencies( ){
        m_textureAlbedo     = null;
        m_textureNormalmap  = null;
        m_textureSpecular   = null;

        m_textureAlbedo = VaUIDObjectRegistrar.GetInstance( ).ReconnectDependency(m_textureAlbedoUID );
        m_textureNormalmap = VaUIDObjectRegistrar.GetInstance( ).ReconnectDependency(m_textureNormalmapUID );
        m_textureSpecular = VaUIDObjectRegistrar.GetInstance( ).ReconnectDependency(m_textureSpecularUID );
        m_textureEmissive = VaUIDObjectRegistrar.GetInstance( ).ReconnectDependency(m_textureEmissiveUID );
    }

    public abstract void UploadToAPIContext( VaDrawContext drawContext );
    public abstract void ClearVertexBuffers( VaDrawContext drawContext);

    protected void UpdateShaderMacros( ){
        if( !m_shaderMacrosDirty )
            return;

//        vector< pair< string, string > > prevShaderMacros = m_shaderMacros;
        List<Macro> prevShaderMacros = new ArrayList<>(m_shaderMacros);

        assert( m_shaderMacrosDirty );
        m_shaderMacros.clear();

        m_shaderMacros.add( new Macro( "VA_RMM_TRANSPARENT",        ( ( m_settings.Transparent       ) ? ( "1" ) : ( "0" ) ) ) );
        m_shaderMacros.add( new Macro( "VA_RMM_ALPHATEST",          ( ( m_settings.AlphaTest         ) ? ( "1" ) : ( "0" ) ) ) );
        m_shaderMacros.add( new Macro( "VA_RMM_ACCEPTSHADOWS",      ( ( m_settings.ReceiveShadows    ) ? ( "1" ) : ( "0" ) ) ) );
        m_shaderMacros.add( new Macro( "VA_RMM_WIREFRAME",          ( ( m_settings.Wireframe         ) ? ( "1" ) : ( "0" ) ) ) );

        boolean texturingEnabled = !m_renderMaterialManager.GetTexturingDisabled();

        m_shaderMacros.add( new Macro( "VA_RMM_HASALBEDOTEXTURE",   ( ( texturingEnabled & (m_textureAlbedo != null    ) ) ? ( "1" ) : ( "0" ) ) ) );
        m_shaderMacros.add( new Macro( "VA_RMM_HASNORMALMAPTEXTURE",( ( texturingEnabled & (m_textureNormalmap != null ) ) ? ( "1" ) : ( "0" ) ) ) );
        m_shaderMacros.add( new Macro( "VA_RMM_HASSPECULARTEXTURE", ( ( texturingEnabled & (m_textureSpecular != null  ) ) ? ( "1" ) : ( "0" ) ) ) );
        m_shaderMacros.add( new Macro( "VA_RMM_HASEMISSIVETEXTURE", ( ( texturingEnabled & (m_textureEmissive != null  ) ) ? ( "1" ) : ( "0" ) ) ) );

        m_shaderMacrosDirty = false;
        m_shadersDirty = (!prevShaderMacros.equals(m_shaderMacros));
    }

    // these are static settings and should not be changed once per frame or more frequently

    public static final class MaterialSettingsV1
    {
        public int            FaceCull;

        public final Vector4f ColorMultAlbedo = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        public final Vector4f ColorMultSpecular = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

        public boolean                                            AlphaTest;
        public boolean                                            ReceiveShadows;

        MaterialSettingsV1( )
        {
            AlphaTest                   = false;
            ReceiveShadows              = true;
            FaceCull                    = FaceCull_Back;

            /*ColorMultAlbedo             = vaVector4( 1.0f, 1.0f, 1.0f, 1.0f );
            ColorMultSpecular           = vaVector4( 1.0f, 1.0f, 1.0f, 1.0f );*/
        }

        void Load(VaStream in) throws IOException{
            FaceCull = in.ReadInt();
            in.Read(ColorMultAlbedo);
            in.Read(ColorMultSpecular);
            AlphaTest = in.ReadBoolean();
            ReceiveShadows = in.ReadBoolean();
        }
    };

    public static final class MaterialSettings
    {
        public int                  FaceCull = FaceCull_Back;

        public final Vector4f       ColorMultAlbedo = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        public final Vector4f       ColorMultSpecular = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        public final Vector4f       ColorMultEmissive = new Vector4f();

        public boolean              AlphaTest;
        public boolean              ReceiveShadows;
        public boolean              Wireframe;

        public float                SpecPow;
        public float                SpecMul;
        public boolean              Transparent;

        // for future upgrades, so that file format stays the same
        public float                Dummy3;
        public float                Dummy4;
        public float                Dummy5;
        public float                Dummy6;
        public float                Dummy7;

        public void Save(VaStream outStream) throws IOException{
            outStream.Write(FaceCull);

            outStream.Write(ColorMultAlbedo);
            outStream.Write(ColorMultSpecular);
            outStream.Write(ColorMultEmissive);

            outStream.Write(AlphaTest);
            outStream.Write(ReceiveShadows);
            outStream.Write(Wireframe);
            outStream.Write(false);  // padding

            outStream.Write(SpecPow);
            outStream.Write(SpecMul);
            outStream.Write(Transparent);
            outStream.Write(false);  // padding
            outStream.Write(false);  // padding
            outStream.Write(false);  // padding

            outStream.Write(Dummy3);
            outStream.Write(Dummy4);
            outStream.Write(Dummy5);
            outStream.Write(Dummy6);
            outStream.Write(Dummy7);
        }

        public void Load(VaStream inStream) throws IOException{
            FaceCull = inStream.ReadInt();

            inStream.Read(ColorMultAlbedo);
            inStream.Read(ColorMultSpecular);
            inStream.Read(ColorMultEmissive);

            AlphaTest = inStream.ReadBoolean();
            ReceiveShadows = inStream.ReadBoolean();
            Wireframe = inStream.ReadBoolean();
            inStream.Read();  // skip the byte

            SpecPow = inStream.ReadFloat();
            SpecMul = inStream.ReadFloat();
            Transparent = inStream.ReadBoolean();
            inStream.Read();  // skip the byte
            inStream.Read();  // skip the byte
            inStream.Read();  // skip the byte

            Dummy3 = inStream.ReadFloat();
            Dummy4 = inStream.ReadFloat();
            Dummy5 = inStream.ReadFloat();
            Dummy6 = inStream.ReadFloat();
            Dummy7 = inStream.ReadFloat();
        }

        MaterialSettings( )
        {
            Transparent                 = false;
            AlphaTest                   = false;
            ReceiveShadows              = true;
            FaceCull                    = FaceCull_Back;
            Wireframe                   = false;

//            ColorMultAlbedo             = vaVector4( 1.0f, 1.0f, 1.0f, 1.0f );
//            ColorMultSpecular           = vaVector4( 1.0f, 1.0f, 1.0f, 1.0f );
//            ColorMultEmissive           = vaVector4( 0.0f, 0.0f, 0.0f, 0.0f );

            SpecPow                     = 1.0f;
            SpecMul                     = 1.0f;

            Dummy3                      = 0.0f;
            Dummy4                      = 0.0f;
            Dummy5                      = 0.0f;
            Dummy6                      = 0.0f;
            Dummy7                      = 0.0f;
        }

        public void set(MaterialSettings ohs){
            FaceCull = ohs.FaceCull;
            AlphaTest = ohs.AlphaTest;
            ReceiveShadows = ohs.ReceiveShadows;
            Wireframe = ohs.Wireframe;
            SpecPow = ohs.SpecPow;
            SpecMul = ohs.SpecMul;
            Transparent = ohs.Transparent;
            ColorMultAlbedo.set(ohs.ColorMultAlbedo);
            ColorMultSpecular.set(ohs.ColorMultSpecular);
            ColorMultEmissive.set(ohs.ColorMultEmissive);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MaterialSettings that = (MaterialSettings) o;

            if (FaceCull != that.FaceCull) return false;
            if (AlphaTest != that.AlphaTest) return false;
            if (ReceiveShadows != that.ReceiveShadows) return false;
            if (Wireframe != that.Wireframe) return false;
            if (Float.compare(that.SpecPow, SpecPow) != 0) return false;
            if (Float.compare(that.SpecMul, SpecMul) != 0) return false;
            if (Transparent != that.Transparent) return false;
            if (!ColorMultAlbedo.equals(that.ColorMultAlbedo)) return false;
            if (!ColorMultSpecular.equals(that.ColorMultSpecular)) return false;
            return ColorMultEmissive.equals(that.ColorMultEmissive);
        }

        @Override
        public int hashCode() {
            int result = FaceCull;
            result = 31 * result + ColorMultAlbedo.hashCode();
            result = 31 * result + ColorMultSpecular.hashCode();
            result = 31 * result + ColorMultEmissive.hashCode();
            result = 31 * result + (AlphaTest ? 1 : 0);
            result = 31 * result + (ReceiveShadows ? 1 : 0);
            result = 31 * result + (Wireframe ? 1 : 0);
            result = 31 * result + (SpecPow != +0.0f ? Float.floatToIntBits(SpecPow) : 0);
            result = 31 * result + (SpecMul != +0.0f ? Float.floatToIntBits(SpecMul) : 0);
            result = 31 * result + (Transparent ? 1 : 0);
            return result;
        }

        /*inline bool operator == ( const MaterialSettings & mat ) const
        {
            return 0 == memcmp( this, &mat, sizeof( MaterialSettings ) );
        }

        inline bool operator != ( const MaterialSettings & mat ) const
        {
            return 0 != memcmp( this, &mat, sizeof( MaterialSettings ) );
        }*/


    };
}
