package jet.opengl.postprocessing.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import jet.opengl.postprocessing.texture.Texture2D;
import jet.opengl.postprocessing.texture.Texture2DDesc;
import jet.opengl.postprocessing.texture.Texture3D;
import jet.opengl.postprocessing.texture.Texture3DDesc;
import jet.opengl.postprocessing.texture.TextureGL;
import jet.opengl.postprocessing.texture.TextureUtils;
import jet.opengl.postprocessing.util.LogUtil;

/**
 * Created by mazhen'gui on 2017/4/13.
 * This class can be moved to the package as same as the Texture class for better caching.
 */
public final class RenderTexturePool {

    private final HashSet<TextureGL> m_CreatedTextures = new HashSet<>();
    private final HashMap<Object, List<TextureGL>> m_RenderTexturePool = new HashMap<>();
    private static RenderTexturePool g_Instance;
    private static final Texture2DDesc m_TempDesc = new Texture2DDesc();

    private RenderTexturePool(){}

    public static RenderTexturePool getInstance(){
        if(g_Instance == null)
            g_Instance = new RenderTexturePool();

        return g_Instance;
    }

    public Texture2D findFreeElement(int width, int height, int format){
        m_TempDesc.width = width;
        m_TempDesc.height = height;
        m_TempDesc.format = format;

        return findFreeElement(m_TempDesc);
    }

    public Texture2D findFreeElement(Texture2DDesc desc){
        List<TextureGL> texture2DList = m_RenderTexturePool.get(desc);
        if(texture2DList == null || texture2DList.isEmpty()){
            if(m_CreatedTextures.size() > 30){
                LogUtil.e(LogUtil.LogType.DEFAULT, "Allocated two many textures in the pool! Desc: " + desc.toString());
            }

            Texture2D texture2D = TextureUtils.createTexture2D(desc, null);
            m_CreatedTextures.add(texture2D);
            LogUtil.i(LogUtil.LogType.DEFAULT, "Create a new Texture in the RenderTexturePool. Created Texture Count: " + m_CreatedTextures.size());
            return texture2D;
        }else{
//            LogUtil.i(LogUtil.LogType.DEFAULT, "Retrive a Texture from the RenderTexturePool." );
            return (Texture2D) texture2DList.remove(texture2DList.size() - 1);
        }
    }

    public Texture3D findFreeElement(Texture3DDesc desc){
        List<TextureGL> texture2DList = m_RenderTexturePool.get(desc);
        if(texture2DList == null || texture2DList.isEmpty()){
            if(m_CreatedTextures.size() > 30){
                LogUtil.e(LogUtil.LogType.DEFAULT, "Allocated two many textures in the pool! Desc: " + desc.toString());
            }

            Texture3D texture3D = TextureUtils.createTexture3D(desc, null);
            m_CreatedTextures.add(texture3D);
            LogUtil.i(LogUtil.LogType.DEFAULT, "Create a new Texture in the RenderTexturePool. Created Texture Count: " + m_CreatedTextures.size());
            return texture3D;
        }else{
//            LogUtil.i(LogUtil.LogType.DEFAULT, "Retrive a Texture from the RenderTexturePool." );
            return (Texture3D) texture2DList.remove(texture2DList.size() - 1);
        }
    }

    public void freeUnusedResource(TextureGL tex){
        if(tex == null){
            LogUtil.i(LogUtil.LogType.DEFAULT, "Couldn't put the null texture into the RenderTexturePool");
            return;
        }

        if(m_CreatedTextures.contains(tex)){
            Object key = null;
            if(tex instanceof  Texture2D){
                key = ((Texture2D)tex).getDesc();
            }else if(tex instanceof  Texture3D){
                key = ((Texture3D)tex).getDesc();
            }else{
                throw new IllegalArgumentException();
            }
            List<TextureGL> texture2DList = m_RenderTexturePool.get(key);
            if(texture2DList == null){
                texture2DList = new ArrayList<>(4);
                m_RenderTexturePool.put(key, texture2DList);
            }

//            LogUtil.i(LogUtil.LogType.DEFAULT, "Put a unused texture into the RenderTexturePool." );
            for(TextureGL texture2D : texture2DList){
                if(texture2D == tex || texture2D.getTexture() == tex.getTexture()){
                    return;
                }
            }

            texture2DList.add(tex);
        }else{
            LogUtil.e(LogUtil.LogType.DEFAULT, "Couldn't put the external texture into the RenderTexturePool");
        }
    }
}
