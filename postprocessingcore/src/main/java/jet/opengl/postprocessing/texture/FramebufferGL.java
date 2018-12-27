package jet.opengl.postprocessing.texture;

import jet.opengl.postprocessing.common.Disposeable;
import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.util.CacheBuffer;

import static jet.opengl.postprocessing.common.GLenum.GL_FRAMEBUFFER;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_1D;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_1D_ARRAY;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_2D;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_2D_ARRAY;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_2D_MULTISAMPLE;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_2D_MULTISAMPLE_ARRAY;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_3D;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_CUBE_MAP;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_CUBE_MAP_ARRAY;
import static jet.opengl.postprocessing.common.GLenum.GL_TEXTURE_RECTANGLE;

/**
 * Created by mazhen'gui on 2017/4/15.
 */

public class FramebufferGL implements Disposeable {
    private int m_Framebuffer;
    private int m_Width, m_Height;
    private int m_AttachCount;
    private TextureGL[] m_AttachedTextures = new TextureGL[8];
    private final boolean[] m_Owed = new boolean[8];

    static final int CUBE_FACES[] =
    {
            GLenum.GL_TEXTURE_CUBE_MAP_POSITIVE_X, GLenum.GL_TEXTURE_CUBE_MAP_NEGATIVE_X,
            GLenum.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, GLenum.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y,
            GLenum.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, GLenum.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z,
    };

    public static int measureTextureAttachment(TextureGL pTex, int index) {
//        assert(pTex);
//        assert(index < 8u);

        int format_conponemt = TextureUtils.measureFormat(pTex.getFormat());
        switch (format_conponemt)
        {
            case GLenum.GL_DEPTH_COMPONENT: return GLenum.GL_DEPTH_ATTACHMENT;
            case GLenum.GL_DEPTH_STENCIL:	 return GLenum.GL_DEPTH_STENCIL_ATTACHMENT;
            case GLenum.GL_STENCIL:		 return GLenum.GL_STENCIL_ATTACHMENT;
            default:
                return GLenum.GL_COLOR_ATTACHMENT0 + index;
        }
    }

    public void addTextures(TextureGL[] textures, TextureAttachDesc[] descs){
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
//        bind();
        if(GLCheck.CHECK){
            if(m_Framebuffer == 0){
                throw new IllegalStateException("m_Framebuffer is 0.");
            }
            int fbo = gl.glGetInteger(GLenum.GL_DRAW_FRAMEBUFFER_BINDING);
            if(fbo != m_Framebuffer){
                throw new IllegalStateException("No binding the current framebuffer.");
            }
        }

        int[] drawbuffers = new int[textures.length];
        int count = 0;
        for (int i = 0; i < textures.length; i++)
        {
            TextureGL pTex = textures[i];
            Texture2D tex2D = (Texture2D)pTex;  // Not safe
            if(m_Width == 0){
                m_Width = tex2D.getWidth();
                m_Height = tex2D.getHeight();
            }else{
                m_Width = Math.max(m_Width, tex2D.getWidth());
                m_Height = Math.max(m_Height, tex2D.getHeight());
            }

            TextureAttachDesc desc = descs[i];
            m_AttachedTextures[m_AttachCount] = pTex;
            switch (desc.type)
            {
                case TEXTURE:
                    if (pTex != null)
                    {
                        int index = measureTextureAttachment(pTex, desc.index);
                        gl.glFramebufferTexture(GL_FRAMEBUFFER, index, pTex.getTexture(), desc.level);
                        if(index >= GLenum.GL_COLOR_ATTACHMENT0 && index - GLenum.GL_COLOR_ATTACHMENT0 < 8) {
                            drawbuffers[count++] = index;
                        }
                        m_AttachCount++;
                    }
                    else
                    {
                        // TODO
                    }
                    break;
                case TEXTURE_1D:
                    if (pTex != null)
                    {
                        assert(pTex.getTarget() == GL_TEXTURE_1D /*|| pTex.getTarget() == GL_TEXTURE_1D_ARRAY*/);
                        int index = measureTextureAttachment(pTex, desc.index);
                        gl.glFramebufferTexture1D(GL_FRAMEBUFFER, index, GL_TEXTURE_1D, pTex.getTexture(), desc.level);
                        if(index >= GLenum.GL_COLOR_ATTACHMENT0 && index - GLenum.GL_COLOR_ATTACHMENT0 < 8) {
                            drawbuffers[count++] = index;
                        }
                        m_AttachCount++;
                    }
                    else
                    {
                        // TODO
                    }
                    break;
                case TEXTURE_2D:
                    if (pTex != null)
                    {
                        int target = pTex.getTarget();
                        assert(target == GL_TEXTURE_2D || target == GL_TEXTURE_CUBE_MAP || target == GL_TEXTURE_RECTANGLE ||
                                target == GL_TEXTURE_2D_MULTISAMPLE);
                        if (target == GL_TEXTURE_CUBE_MAP)
                        {
                            for (int j = 0; j < CUBE_FACES.length; j++) {
                                int index = measureTextureAttachment(pTex, desc.index);
                                gl.glFramebufferTexture2D(GL_FRAMEBUFFER, index, CUBE_FACES[j], pTex.getTexture(), desc.level); // TODO
                                if(index >= GLenum.GL_COLOR_ATTACHMENT0 && index - GLenum.GL_COLOR_ATTACHMENT0 < 8) {
                                    drawbuffers[count++] = index;
                                }
                            }
                        }
                        else
                        {
                            int index = measureTextureAttachment(pTex, desc.index);
                            gl.glFramebufferTexture2D(GL_FRAMEBUFFER, index, target, pTex.getTexture(), desc.level);
                            if(index >= GLenum.GL_COLOR_ATTACHMENT0 && index - GLenum.GL_COLOR_ATTACHMENT0 < 8) {
                                drawbuffers[count++] = index;
                            }
                        }
                        m_AttachCount++;
                    }
                    else
                    {
                        // TODO
                    }
                    break;
                case TEXTURE_3D:
                    if (pTex != null)
                    {
                        assert(pTex.getTarget() == GL_TEXTURE_3D /*|| pTex.getTarget() == GL_TEXTURE_1D_ARRAY*/);
                        int index = measureTextureAttachment(pTex, desc.index);
                        gl.glFramebufferTexture3D(GL_FRAMEBUFFER, index, GL_TEXTURE_3D, pTex.getTexture(), desc.level, desc.layer);
                        if(index >= GLenum.GL_COLOR_ATTACHMENT0 && index - GLenum.GL_COLOR_ATTACHMENT0 < 8) {
                            drawbuffers[count++] = index;
                        }
                        m_AttachCount++;
                    }
                    else
                    {
                        // TODO
                    }
                    break;
                case TEXTURE_LAYER:
                    if (pTex != null)
                    {
                        int target = pTex.getTarget();
                        assert(target == GL_TEXTURE_3D || target == GL_TEXTURE_2D_ARRAY || target == GL_TEXTURE_1D_ARRAY
                                || target == GL_TEXTURE_2D_MULTISAMPLE_ARRAY || target == GL_TEXTURE_CUBE_MAP_ARRAY);
                        int index = measureTextureAttachment(pTex, desc.index);
                        gl.glFramebufferTextureLayer(GL_FRAMEBUFFER, index, pTex.getTexture(), desc.level, desc.layer);
                        if(index >= GLenum.GL_COLOR_ATTACHMENT0 && index - GLenum.GL_COLOR_ATTACHMENT0 < 8) {
                            drawbuffers[count++] = index;
                        }
                        m_AttachCount++;
                    }
                    else
                    {
                        // TODO
                    }
                    break;
                default:
                    break;
            }
        }

        if(count > 0){
            gl.glDrawBuffers(CacheBuffer.wrap(drawbuffers, 0, count));
        }else{
            gl.glDrawBuffers(GLenum.GL_NONE);
        }
    }

    public TextureGL getAttachedTex(int index){
        return m_AttachedTextures[index];
    }

    public void addTexture(TextureGL texture, TextureAttachDesc desc){
        m_Owed[m_AttachCount] = true;
        addTextures(new TextureGL[]{texture}, new TextureAttachDesc[]{desc});
    }

    public Texture2D addTexture2D(Texture2DDesc texDesc, TextureAttachDesc attachDesc){
        Texture2D texture2D = TextureUtils.createTexture2D(texDesc, null);
        m_Owed[m_AttachCount] = true;
        addTextures(new TextureGL[]{texture2D}, new TextureAttachDesc[]{attachDesc});
        return texture2D;
    }

    public void bind(){
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
        if (m_Framebuffer == 0)
        {
            m_Framebuffer = gl.glGenFramebuffer();
//            g_FBOCaches.insert(std::pair<GLuint, FramebufferGL*>(m_Framebuffer, this));
        }

        gl.glBindFramebuffer(GLenum.GL_FRAMEBUFFER, m_Framebuffer);
    }

    public void setViewPort(){
        if(m_Width  > 0){
            GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
            gl.glViewport(0,0, m_Width, m_Height);
        }else{
            throw new IllegalArgumentException("Invalid size: width = " + m_Width + ", height = " + m_Height);
        }

        for(int i = 0; i < m_AttachCount; i++){
            if(m_AttachedTextures[i] != null){
                Texture2D tex = (Texture2D) m_AttachedTextures[i];
                GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
                gl.glViewport(0,0, tex.getWidth(), tex.getHeight());
                break;
            }
        }
    }

    public int getFramebuffer() { return m_Framebuffer;}

    public void unbind(){
        GLFuncProviderFactory.getGLFuncProvider().glBindFramebuffer(GLenum.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void dispose() {
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
        if (m_Framebuffer != 0)
        {
            gl.glDeleteFramebuffer(m_Framebuffer);
//            g_FBOCaches.erase(m_Framebuffer);
            m_Framebuffer = 0;
        }

        for (int i = 0; i < m_Owed.length; i++)
        {
            if (m_Owed[i])
            {
//                SAFE_DELETE(m_AttachedTextures[i]);
                m_Owed[i] = false;
                if(m_AttachedTextures[i] != null) {
                    m_AttachedTextures[i].dispose();
                    m_AttachedTextures[i] = null;
                }
            }
        }
    }

    public int getWidth() {
        return m_Width;
    }

    public int getHeight() { return m_Height;}
}
