package jet.opengl.demos.intel.va;

import jet.opengl.postprocessing.common.Disposeable;

/**
 * Created by mazhen'gui on 2017/11/16.
 */

public abstract class VaAsset extends VaImguiHierarchyObject implements Disposeable{
    /*private:
    friend class vaAssetPack;*/
    private VaAssetResource m_resourceBasePtr;
    String                                              m_name;                     // warning, never change this except by using Rename
    VaAssetPack m_parentPack;
    int                                                 m_parentPackStorageIndex;   // referring to vaAssetPack::m_assetList

    protected VaAsset(VaAssetPack pack, VaAssetType type, String name, VaAssetResource resourceBasePtr ) //: m_parentPack( pack ), Type( type ), m_name( name ), m_resourceBasePtr( resourceBasePtr ), m_parentPackStorageIndex( -1 )
    {
        m_parentPack = pack;
        Type =type;
        m_resourceBasePtr = resourceBasePtr;
        m_name = name;
        m_resourceBasePtr.SetParentAsset( this );
    }
//    virtual ~vaAsset( )                                 { m_resourceBasePtr->SetParentAsset( nullptr ); }

    public VaAssetType Type;
//        const wstring                                       StoragePath;

    public VaTexture                                    GetTexture( )                       { return null; }
    public VaRenderMesh                                 GetMesh( )                          { return null; }
    public VaRenderMaterial                             GetMaterial( )                      { return null; }

    public String                                       Name( )                       { return m_name; }

//    bool                                                Rename( const string & newName );

    public abstract boolean                                        Save( VaStream outStream ) ;

    void                                                ReconnectDependencies( )            { m_resourceBasePtr.ReconnectDependencies(); }

    protected String                                      IHO_GetInstanceInfo( )        { return /*vaStringTools::Format( "%s", m_name.c_str() )*/m_name; }
    protected void                                        IHO_Draw( ){}

    @Override
    public void dispose() {
        m_resourceBasePtr.SetParentAsset(null);
    }
}
