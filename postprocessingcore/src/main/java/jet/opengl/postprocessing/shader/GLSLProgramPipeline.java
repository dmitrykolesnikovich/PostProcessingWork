package jet.opengl.postprocessing.shader;

import jet.opengl.postprocessing.common.Disposeable;
import jet.opengl.postprocessing.common.GLAPIVersion;
import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.common.GLenum;

/**
 * Created by mazhen'gui on 2017/4/21.
 */

public class GLSLProgramPipeline implements Disposeable{

    private static final int UNKOWN = 0;
    private static final int ENABLE = 1;
    private static final int DISABLE = 2;

    private static int g_PipelineState = UNKOWN;

    private String name = "GLSLProgramPipeline";
    private int m_programPipeline;

    int m_VS;
    int m_PS;
    int m_TC;
    int m_TE;
    int m_GS;
    int m_CS;

    GLFuncProvider gl;

    public GLSLProgramPipeline(){
        gl = GLFuncProviderFactory.getGLFuncProvider();
        m_programPipeline = gl.glGenProgramPipeline();
    }

    public static boolean isSupportProgramPipeline(){
        if(g_PipelineState == UNKOWN){
            GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
            GLAPIVersion version = gl.getGLAPIVersion();
            if(gl.isSupportExt("ARB_separate_shader_objects") || (version.ES && version.major >= 3 && version.minor >= 1)){
                g_PipelineState = ENABLE;
            }else{
                g_PipelineState = DISABLE;
            }
        }

        return g_PipelineState  == ENABLE;
    }

    private static int getProgramID(ShaderProgram shaderProgram){
        return shaderProgram != null ? shaderProgram.getProgram() : 0;
    }

    private static ShaderProgram check(ShaderProgram program, ShaderType type){
        if(GLCheck.CHECK && program != null && program.getTarget() != type.shader){
            throw new IllegalArgumentException("The program is not the type of " + type);
        }

        return program;
    }

    public void setVS(ShaderProgram vs){
        int vs_id = getProgramID(check(vs, ShaderType.VERTEX));
        if(m_VS != vs_id){
            m_VS = vs_id;
            gl.glUseProgramStages(m_programPipeline, GLenum.GL_VERTEX_SHADER_BIT, vs_id);
        }
    }

    public void setPS(ShaderProgram ps){
        int ps_id = getProgramID(check(ps, ShaderType.FRAGMENT));
        if(m_PS != ps_id){
            m_PS = ps_id;
            gl.glUseProgramStages(m_programPipeline, GLenum.GL_FRAGMENT_SHADER_BIT, ps_id);
        }
    }

    public void setTC(ShaderProgram tc){
        int tc_id = getProgramID(check(tc, ShaderType.TESS_CONTROL));
        if(m_TC != tc_id){
            m_TC = tc_id;
            gl.glUseProgramStages(m_programPipeline, GLenum.GL_TESS_CONTROL_SHADER_BIT, tc_id);
        }
    }

    public void setTE(ShaderProgram te){
        int te_id = getProgramID(check(te, ShaderType.TESS_EVAL));
        if(m_TE != te_id){
            m_TE = te_id;
            gl.glUseProgramStages(m_programPipeline, GLenum.GL_TESS_EVALUATION_SHADER_BIT, te_id);
        }
    }

    public void setGS(ShaderProgram gs){
        int gs_id = getProgramID(check(gs, ShaderType.GEOMETRY));
        if(m_GS != gs_id){
            m_GS = gs_id;
            gl.glUseProgramStages(m_programPipeline, GLenum.GL_GEOMETRY_SHADER_BIT, gs_id);
        }
    }

    public void setCS(ShaderProgram cs){
        int cs_id = getProgramID(check(cs, ShaderType.COMPUTE));
        if(m_CS != cs_id){
            m_CS = cs_id;
            gl.glUseProgramStages(m_programPipeline, GLenum.GL_COMPUTE_SHADER_BIT, cs_id);
        }
    }

    public void enable() {
        gl.glBindProgramPipeline(m_programPipeline);
    }

    public void disable() {
        gl.glBindProgramPipeline(0);
    }


    public int getProgram() {
        return m_programPipeline;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {return name;}

    @Override
    public void dispose() {
        if(m_programPipeline != 0){
            gl.glDeleteProgramPipeline(m_programPipeline);
        }
    }
}
