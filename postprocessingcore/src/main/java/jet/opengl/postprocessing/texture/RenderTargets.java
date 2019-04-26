package jet.opengl.postprocessing.texture;

import java.nio.IntBuffer;

import jet.opengl.postprocessing.common.Disposeable;
import jet.opengl.postprocessing.common.GLAPIVersion;
import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.util.CachaRes;
import jet.opengl.postprocessing.util.CacheBuffer;

/**
 * Created by mazhen'gui on 2017/4/15.
 */

public class RenderTargets implements Disposeable{

    private static final TextureAttachDesc g_DefaultAttachDesc = new TextureAttachDesc();
    private static boolean g_DefaultAttachInit = false;

    private final AttachInfo m_DepthAttach = new AttachInfo();
    private final AttachInfo m_StencilAttach = new AttachInfo();
    private final AttachInfo m_DepthStencilAttach = new AttachInfo();
    private final AttachInfo[] m_ColorAttaches = new AttachInfo[8];

    private int m_Framebuffer;

    public RenderTargets(){
        for(int i = 0; i < m_ColorAttaches.length; i++){
            m_ColorAttaches[i] = new AttachInfo();
        }
    }

    public void initlize(){
        if (m_Framebuffer == 0)
        {
            GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
            m_Framebuffer = gl.glGenFramebuffer();
//            g_FBOCaches.insert(std::pair<GLuint, FramebufferGL*>(m_Framebuffer, this));
        }
    }

    public void dispose(){
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
        if (m_Framebuffer != 0)
        {
            gl.glDeleteFramebuffer(m_Framebuffer);
//            g_FBOCaches.erase(m_Framebuffer);
            m_Framebuffer = 0;
        }
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

    static TextureAttachDesc getDefaultAttachDesc(){
        if(!g_DefaultAttachInit){
            GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
            GLAPIVersion api = gl.getGLAPIVersion();
            if(api.major >= 3 && api.minor >= 2){ // Both the opengl and opengl es require the 3.2 that can support glFramebufferTexture.
                g_DefaultAttachDesc.type = AttachType.TEXTURE;
            }else{
                g_DefaultAttachDesc.type = AttachType.TEXTURE_2D;
            }

            g_DefaultAttachInit = true;
        }

        return g_DefaultAttachDesc;
    }

    public int getFramebuffer() {return m_Framebuffer;}

    public void unbind(){
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
        gl.glBindFramebuffer(GLenum.GL_FRAMEBUFFER, 0);
    }

    static void deAttachTexture(int attachment, AttachType type)
    {
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
        switch (type)
        {
            case TEXTURE_1D:
                gl.glFramebufferTexture1D(GLenum.GL_FRAMEBUFFER, attachment, GLenum.GL_TEXTURE_1D, 0, 0);
                break;
            case TEXTURE_2D:
                gl.glFramebufferTexture2D(GLenum.GL_FRAMEBUFFER, attachment, GLenum.GL_TEXTURE_2D, 0, 0);
                break;
            case TEXTURE_3D:
                gl.glFramebufferTexture3D(GLenum.GL_FRAMEBUFFER, attachment, GLenum.GL_TEXTURE_3D, 0, 0, 0);
                break;
            case TEXTURE_LAYER:
                gl.glFramebufferTextureLayer(GLenum.GL_FRAMEBUFFER, attachment, GLenum.GL_TEXTURE_3D, 0, 0);
                break;
            case TEXTURE:
                gl.glFramebufferTexture(GLenum.GL_FRAMEBUFFER, attachment, 0, 0);
                break;
            default:
                break;
        }
    }

    private final boolean[] colorHandled = new boolean[8];
    private final TextureGL[] texArrays = new TextureGL[1];
    private final TextureAttachDesc[] descArrays = new TextureAttachDesc[1];

    @CachaRes
    public void setRenderTexture(TextureGL texture, TextureAttachDesc desc){
        if(texture == null){
            setRenderTextures(null, null);
        }else {
            texArrays[0] = texture;
            descArrays[0] = desc;

            setRenderTextures(texArrays, descArrays);
        }
    }

    @CachaRes
    public void setRenderTextures(TextureGL[] textures, TextureAttachDesc[] descs){
        boolean depthHandled = false;
        boolean depthStencilHandled = false;
        boolean stencilHandled = false;
//        bool colorHandled[8] = { false };

        if(GLCheck.CHECK){
            if(m_Framebuffer == 0){
                throw new IllegalStateException("m_Framebuffer is 0.");
            }
            GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
            int fbo = gl.glGetInteger(GLenum.GL_DRAW_FRAMEBUFFER_BINDING);
            if(fbo != m_Framebuffer){
                throw new IllegalStateException("No binding the current framebuffer.");
            }
        }

        final int colorAttachedCount;
        if(textures != null && textures.length > 0) {
            int colorAttachIndex = 0;
            IntBuffer buffers = CacheBuffer.getCachedIntBuffer(textures.length);
            for (int i = 0; i < textures.length; i++) {
                TextureGL pTex = textures[i];
                if (pTex == null)
                    continue;

                TextureAttachDesc desc;
                if (descs != null && descs[i] != null) {
                    desc = descs[i];
                } else {
                    desc = getDefaultAttachDesc();
                    desc.index = colorAttachIndex;
                }

                int index = desc.index;
                assert (index < 8);

                int format_conponemt = TextureUtils.measureFormat(pTex.getFormat());
                switch (format_conponemt) {
                    case GLenum.GL_DEPTH_COMPONENT:
                        assert (!depthHandled);

                        if (m_DepthStencilAttach.attached) {
                            deAttachTexture(GLenum.GL_DEPTH_STENCIL_ATTACHMENT, m_DepthStencilAttach.type);
                            m_DepthStencilAttach.attached = false;
                        }

                        handleTextureAttachment(pTex, GLenum.GL_DEPTH_ATTACHMENT, desc, m_DepthAttach);
                        depthHandled = true;
                        break;
                    case GLenum.GL_DEPTH_STENCIL:
                        assert (!depthStencilHandled);
                        if (m_StencilAttach.attached) {
                            deAttachTexture(GLenum.GL_STENCIL_ATTACHMENT, m_StencilAttach.type);
                            m_StencilAttach.attached = false;
                        }

                        if (m_DepthAttach.attached) {
                            deAttachTexture(GLenum.GL_DEPTH_ATTACHMENT, m_DepthAttach.type);
                            m_DepthAttach.attached = false;
                        }

                        handleTextureAttachment(pTex, GLenum.GL_DEPTH_STENCIL_ATTACHMENT, desc, m_DepthStencilAttach);
                        depthStencilHandled = true;
                        break;
                    case GLenum.GL_STENCIL:
                        assert (!stencilHandled);
                        if (m_DepthStencilAttach.attached) {
                            deAttachTexture(GLenum.GL_DEPTH_STENCIL_ATTACHMENT, m_DepthStencilAttach.type);
                            m_DepthStencilAttach.attached = false;
                        }

                        handleTextureAttachment(pTex, GLenum.GL_STENCIL_ATTACHMENT, desc, m_StencilAttach);
                        stencilHandled = true;
                        break;
                    default:
                        assert (!colorHandled[index]);
                        handleTextureAttachment(pTex, GLenum.GL_COLOR_ATTACHMENT0 + index, desc, m_ColorAttaches[index]);
                        colorHandled[index] = true;

                        buffers.put(GLenum.GL_COLOR_ATTACHMENT0 + index);
                        colorAttachIndex++;
                        break;
                }
            }

            // TODO Performance isuee.
            buffers.flip();
            colorAttachedCount = buffers.remaining();
            if (colorAttachedCount > 0) {
                GLFuncProviderFactory.getGLFuncProvider().glDrawBuffers(buffers);
            } else {
                GLFuncProviderFactory.getGLFuncProvider().glDrawBuffers(GLenum.GL_NONE);
            }
        }else{
            colorAttachedCount = 0;
            GLFuncProviderFactory.getGLFuncProvider().glDrawBuffers(GLenum.GL_NONE);
        }

        // unbind the previouse textures attchment.
        if (!depthHandled && m_DepthAttach.attached)
        {
            deAttachTexture(GLenum.GL_DEPTH_ATTACHMENT, m_DepthAttach.type);
            m_DepthAttach.attached = false;
        }

        if (!depthStencilHandled && m_DepthStencilAttach.attached)
        {
            deAttachTexture(GLenum.GL_DEPTH_STENCIL_ATTACHMENT, m_DepthStencilAttach.type);
            m_DepthStencilAttach.attached = false;
        }

        if (!stencilHandled && m_StencilAttach.attached)
        {
            deAttachTexture(GLenum.GL_STENCIL_ATTACHMENT, m_StencilAttach.type);
            m_StencilAttach.attached = false;
        }

        for (int i = 0; i < 8; i++)
        {
            if (!colorHandled[i] && m_ColorAttaches[i].attached)
            {
                deAttachTexture(GLenum.GL_COLOR_ATTACHMENT0 + i, m_ColorAttaches[i].type);
                m_ColorAttaches[i].attached = false;
            }
        }

        if(GLCheck.CHECK)
            GLCheck.checkError("RenderTargets::setRenderTextures::end");

        for(int i = 0; i < colorAttachedCount; i++){
            colorHandled[i] = false;
        }

        if(GLCheck.CHECK){
            GLCheck.checkFramebufferStatus();
        }
    }

    private void handleTextureAttachment(TextureGL pTex, int attachment, TextureAttachDesc desc, AttachInfo info){
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
//        if(info.type != null && info.type != desc.type){
//            deAttachTexture(attachment, info.type);
//        }

        info.type = desc.type;
        switch (desc.type)
        {
            case TEXTURE_1D:
                if (pTex != null)
                {
                    assert(pTex.getTarget() == GLenum.GL_TEXTURE_1D);
                    if (!info.attached || info.textureTarget != pTex.getTarget() || info.textureId != pTex.getTexture() || info.level != desc.level)
                    {
                        gl.glFramebufferTexture1D(GLenum.GL_FRAMEBUFFER, attachment, pTex.getTarget(), pTex.getTexture(), desc.level);
                        info.attached = true;
                        info.textureTarget = pTex.getTarget();
                        info.textureId = pTex.getTexture();
                        info.level = desc.level;
                    }
                }
                else
                {
                    if (info.attached)
                    {
                        gl.glFramebufferTexture1D(GLenum.GL_FRAMEBUFFER, attachment, GLenum.GL_TEXTURE_1D, 0, 0);
                        info.attached = false;
                        info.textureTarget = GLenum.GL_TEXTURE_1D;
                        info.textureId = 0;
                    }
                }
                break;
            case TEXTURE_3D:
                if (pTex != null)
                {
                    assert(pTex.getTarget() == GLenum.GL_TEXTURE_3D);
                    if (!info.attached || info.textureTarget != pTex.getTarget() || info.textureId != pTex.getTexture() || info.level != desc.level || info.layer != desc.layer)
                    {
                        gl.glFramebufferTexture3D(GLenum.GL_FRAMEBUFFER, attachment, pTex.getTarget(), pTex.getTexture(), desc.level, desc.layer);
                        info.attached = true;
                        info.textureTarget = pTex.getTarget();
                        info.textureId = pTex.getTexture();
                        info.level = desc.level;
                        info.layer = desc.layer;
                    }
                }
                else
                {
                    if (info.attached)
                    {
                        gl.glFramebufferTexture3D(GLenum.GL_FRAMEBUFFER, attachment, GLenum.GL_TEXTURE_3D, 0, 0, 0);
                        info.attached = false;
                        info.textureTarget = GLenum.GL_TEXTURE_3D;
                        info.textureId = 0;
                        info.level = 0;
                        info.layer = 0;
                    }

                }
                break;
            case TEXTURE_2D:
                if (pTex!=null)
                {
                    int target = pTex.getTarget();
                    assert(target == GLenum.GL_TEXTURE_2D || target == GLenum.GL_TEXTURE_CUBE_MAP || target == GLenum.GL_TEXTURE_RECTANGLE ||
                            target == GLenum.GL_TEXTURE_2D_MULTISAMPLE);

                    if(target == GLenum.GL_TEXTURE_CUBE_MAP){
                        if(desc.layer < 0 || desc.layer >= 6)
                            throw new IllegalArgumentException("Invalid layer for cubemap target.");

                        target = GLenum.GL_TEXTURE_CUBE_MAP_POSITIVE_X + desc.layer;
                    }

                    if (!info.attached || info.textureTarget != target || info.textureId != pTex.getTexture() || info.level != desc.level)
                    {
                        gl.glFramebufferTexture2D(GLenum.GL_FRAMEBUFFER, attachment, target, pTex.getTexture(), desc.level);

                        info.attached = true;
                        info.textureTarget = target;
                        info.textureId = pTex.getTexture();
                        info.level = desc.level;
                    }
                }
                else
                {
                    if (info.attached)
                    {
                        gl.glFramebufferTexture2D(GLenum.GL_FRAMEBUFFER, attachment, GLenum.GL_TEXTURE_2D, 0, 0);
                        info.attached = false;
                        info.textureTarget = GLenum.GL_TEXTURE_2D;
                        info.textureId = 0;
                        info.level = 0;
                    }

                }
                break;
            case TEXTURE:
                if (pTex !=null)
                {
                    if (!info.attached || info.textureId != pTex.getTexture() || info.level != desc.level)
                    {
                        gl.glFramebufferTexture(GLenum.GL_FRAMEBUFFER, attachment, pTex.getTexture(), desc.level);
                        info.attached = true;
                        info.textureId = pTex.getTexture();
                        info.level = desc.level;
                    }
                }
                else
                {
                    if (info.attached)
                    {
                        gl.glFramebufferTexture(GLenum.GL_FRAMEBUFFER, attachment, 0, 0);
                        info.attached = false;
                        info.textureId = 0;
                        info.level = 0;
                    }
                }

                break;
            case TEXTURE_LAYER:
                if (pTex != null)
                {
                    int target = pTex.getTarget();
                    assert(target == GLenum.GL_TEXTURE_3D || target == GLenum.GL_TEXTURE_2D_ARRAY || target == GLenum.GL_TEXTURE_1D_ARRAY
                            || target == GLenum.GL_TEXTURE_2D_MULTISAMPLE_ARRAY || target == GLenum.GL_TEXTURE_CUBE_MAP_ARRAY);
                    if (!info.attached || info.textureId != pTex.getTexture() || info.level != desc.level || info.layer != desc.layer)
                    {
                        gl.glFramebufferTextureLayer(GLenum.GL_FRAMEBUFFER, attachment, pTex.getTexture(), desc.level, desc.layer);
                        info.attached = true;
                        info.textureId = pTex.getTexture();
                        info.level = desc.level;
                        info.layer = desc.layer;
                    }
                }
                else
                {
                    if (info.attached)
                    {
                        gl.glFramebufferTextureLayer(GLenum.GL_FRAMEBUFFER, attachment, 0, 0, 0);
                        info.attached = false;
                        info.textureId = 0;
                        info.level = 0;
                        info.layer = 0;
                    }

                }
                break;
            default:
                assert(false);
                break;
        }
    }

    private final static class AttachInfo{
        int textureId;
        int textureTarget;
        boolean attached;
        int level;
        int layer;
        AttachType type;
    }
}
