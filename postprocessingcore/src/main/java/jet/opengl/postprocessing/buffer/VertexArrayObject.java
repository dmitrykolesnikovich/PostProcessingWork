package jet.opengl.postprocessing.buffer;

import jet.opengl.postprocessing.common.Disposeable;
import jet.opengl.postprocessing.common.GLAPIVersion;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;

/**
 * Created by mazhen'gui on 2017/4/15.
 */

public class VertexArrayObject implements Disposeable {

    private static final int UNKOWN = 0;
    private static final int ENABLE = 1;
    private static final int DISABLE = 2;

    private static int g_VAOState = UNKOWN;
    private BufferBinding[] m_bindings;
    private BufferGL m_indices;

    private int m_vao;
    private final boolean m_bUseVAO;

    public VertexArrayObject()               {this(true);}
    public VertexArrayObject(boolean useVAO) { m_bUseVAO = useVAO;}

    public static boolean isSupportVAO(){
        if(g_VAOState == UNKOWN){
            GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
            // Query the state
            GLAPIVersion version = gl.getGLAPIVersion();
            g_VAOState = (version.major >= 3 ? ENABLE : DISABLE);
        }

        return g_VAOState ==ENABLE;
    }

    public int getVAO() {return m_vao;}

    public void initlize(BufferBinding[] bindings, BufferGL indices){
        GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
        if(m_vao == 0 && isSupportVAO()){
            m_vao = gl.glGenVertexArray();
        }

        m_bindings = bindings;
        m_indices = indices;

        if(!m_bUseVAO || (bindings == null && indices == null) || !isSupportVAO()){
            return;
        }

        gl.glBindVertexArray(m_vao);
        _bind();
//        gl.glBindVertexArray(0);  TODO
    }

    private void _bind(){
        if(m_bindings != null){
            for(BufferBinding binding : m_bindings){
                binding.bind();
            }
        }

        if(m_indices != null){
            m_indices.bind();
        }
    }

    private void _unbind(){
        if(m_bindings != null){
            for(BufferBinding binding : m_bindings){
                binding.unbind();
            }
        }

        if(m_indices != null){
            m_indices.unbind();
        }
    }

    public void bind() {
        if(g_VAOState == ENABLE && m_bUseVAO){
            GLFuncProviderFactory.getGLFuncProvider().glBindVertexArray(m_vao);
        }else{
            _bind();
        }
    }
    public void unbind() {
        if(g_VAOState ==ENABLE && m_bUseVAO){
            GLFuncProviderFactory.getGLFuncProvider().glBindVertexArray(0);
        }else{
            _unbind();
        }
    }

    @Override
    public void dispose() {
        if(g_VAOState ==ENABLE && m_bUseVAO) {
            GLFuncProviderFactory.getGLFuncProvider().glDeleteVertexArray(m_vao);
            m_vao = 0;
        }
    }
}
