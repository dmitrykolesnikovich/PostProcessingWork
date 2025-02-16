// Copyright (c) 2011 NVIDIA Corporation. All rights reserved.
//
// TO  THE MAXIMUM  EXTENT PERMITTED  BY APPLICABLE  LAW, THIS SOFTWARE  IS PROVIDED
// *AS IS*  AND NVIDIA AND  ITS SUPPLIERS DISCLAIM  ALL WARRANTIES,  EITHER  EXPRESS
// OR IMPLIED, INCLUDING, BUT NOT LIMITED  TO, NONINFRINGEMENT,IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.  IN NO EVENT SHALL  NVIDIA 
// OR ITS SUPPLIERS BE  LIABLE  FOR  ANY  DIRECT, SPECIAL,  INCIDENTAL,  INDIRECT,  OR  
// CONSEQUENTIAL DAMAGES WHATSOEVER (INCLUDING, WITHOUT LIMITATION,  DAMAGES FOR LOSS 
// OF BUSINESS PROFITS, BUSINESS INTERRUPTION, LOSS OF BUSINESS INFORMATION, OR ANY 
// OTHER PECUNIARY LOSS) ARISING OUT OF THE  USE OF OR INABILITY  TO USE THIS SOFTWARE, 
// EVEN IF NVIDIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
//
// Please direct any bugs or questions to SDKFeedback@nvidia.com
package jet.opengl.demos.nvidia.waves.samples;

import java.io.IOException;
import java.nio.FloatBuffer;

import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.shader.GLSLProgram;
import jet.opengl.postprocessing.shader.ShaderLoader;
import jet.opengl.postprocessing.shader.ShaderSourceItem;
import jet.opengl.postprocessing.shader.ShaderType;
import jet.opengl.postprocessing.util.CacheBuffer;
import jet.opengl.postprocessing.util.CommonUtil;

final class FFT {

	private static final double TWO_PI = 2.0 * Math.PI;
	//Memory access coherency (in threads)
	public static final int COHERENCY_GRANULARITY = 128;
	
	public static final int FFT_DIMENSIONS = 3;
	public static final int FFT_PLAN_SIZE_LIMIT = 1 << 27;
	
	public static final int FFT_FORWARD = -1;
	public static final int FFT_INVERSE = 1;
	
	private static final String SHADER_FILE = "nvidia/WaveWorks/shaders/fft_512x512_c2c.glsl";
	
	private static void radix008A(CSFFT512x512_Plan fft_plan, int uav_dst, int srv_src, int thread_count, int istride){
		GLFuncProvider gl= GLFuncProviderFactory.getGLFuncProvider();
		// Setup execution configuration
		int grid = thread_count / COHERENCY_GRANULARITY;
		
		fft_plan.setShaderResource(srv_src);
		fft_plan.setUnorderedAccessViews(uav_dst);
		
		if(istride > 1)
			fft_plan.enableCS();
		else
			fft_plan.enableCS2();

		gl.glDispatchCompute(grid, 1, 1);

		gl.glMemoryBarrier(GLenum.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
		
		fft_plan.setShaderResource(0);
		fft_plan.setUnorderedAccessViews(0);
	}
	
	public static void fft_512x512_c2c(CSFFT512x512_Plan fft_plan, int uav_dst, int srv_dst, int srv_src){
		GLFuncProvider gl= GLFuncProviderFactory.getGLFuncProvider();
		final int thread_count = fft_plan.slices * (512 * 512) / 8;
		int pUAV_Tmp = fft_plan.srv_tmp;  // TODO
		int pSRV_Tmp = fft_plan.srv_tmp;
		
		fft_plan.use();  // TODO
		int istride = 512 * 512 / 8;
//		cs_cbs[0] = fft_plan->pRadix008A_CB[0];
//		pd3dContext->CSSetConstantBuffers(0, 1, &cs_cbs[0]);
		gl.glBindBufferBase(GLenum.GL_SHADER_STORAGE_BUFFER, 0, fft_plan.radix008A_CB[0]);
		radix008A(fft_plan, pUAV_Tmp, srv_src, thread_count, istride);

		istride /= 8;
//		cs_cbs[0] = fft_plan->pRadix008A_CB[1];
//		pd3dContext->CSSetConstantBuffers(0, 1, &cs_cbs[0]);
		gl.glBindBufferBase(GLenum.GL_SHADER_STORAGE_BUFFER, 0, fft_plan.radix008A_CB[1]);
		radix008A(fft_plan, uav_dst, pSRV_Tmp, thread_count, istride);

		istride /= 8;
		gl.glBindBufferBase(GLenum.GL_SHADER_STORAGE_BUFFER, 0, fft_plan.radix008A_CB[2]);
		radix008A(fft_plan, pUAV_Tmp, srv_dst, thread_count, istride);

		istride /= 8;
		gl.glBindBufferBase(GLenum.GL_SHADER_STORAGE_BUFFER, 0, fft_plan.radix008A_CB[3]);
		radix008A(fft_plan, uav_dst, pSRV_Tmp, thread_count, istride);

		istride /= 8;
		gl.glBindBufferBase(GLenum.GL_SHADER_STORAGE_BUFFER, 0, fft_plan.radix008A_CB[4]);
		radix008A(fft_plan, pUAV_Tmp, srv_dst, thread_count, istride);

		istride /= 8;
		gl.glBindBufferBase(GLenum.GL_SHADER_STORAGE_BUFFER, 0, fft_plan.radix008A_CB[5]);
		radix008A(fft_plan, uav_dst, pSRV_Tmp, thread_count, istride);
	}
	
	public static void fft512x512_create_plan(CSFFT512x512_Plan plan, int slices){
		GLFuncProvider gl= GLFuncProviderFactory.getGLFuncProvider();
		plan.slices = slices;
		
		CharSequence source = null;
		try {
			source = ShaderLoader.loadShaderFile(SHADER_FILE, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		ShaderSourceItem item = new ShaderSourceItem(source, ShaderType.COMPUTE);
		GLSLProgram _program = GLSLProgram.createFromShaderItems(CommonUtil.toArray(item));
		int program = _program.getProgram();

		plan.gl=gl;
		plan.program = program;
		plan.radix008A_CS = gl.glGetSubroutineIndex(program, GLenum.GL_COMPUTE_SHADER, "Radix008A_CS");
		plan.radix008A_CS2 = gl.glGetSubroutineIndex(program, GLenum.GL_COMPUTE_SHADER, "Radix008A_CS2");

		plan.thread_count = gl.glGetUniformLocation(program, "thread_count");
		plan.ostride = gl.glGetUniformLocation(program, "ostride");
		plan.istride = gl.glGetUniformLocation(program, "istride");
		plan.pstride = gl.glGetUniformLocation(program, "pstride");
		plan.phase_base = gl.glGetUniformLocation(program, "phase_base");
		
		// Constants
		// Create 6 cbuffers for 512x512 transform
		create_cbuffers_512x512(plan, slices);
		
		// Temp buffer
		int tmp_buf = gl.glGenBuffer();
		gl.glBindBuffer(GLenum.GL_TEXTURE_BUFFER, tmp_buf);
		gl.glBufferData(GLenum.GL_TEXTURE_BUFFER, 4 * 2 * (512 * slices) * 512, GLenum.GL_DYNAMIC_COPY);
		gl.glBindBuffer(GLenum.GL_TEXTURE_BUFFER, 0);
		
		int texture = gl.glGenTexture();
		gl.glBindTexture(GLenum.GL_TEXTURE_BUFFER, texture);
		gl.glTexBuffer(GLenum.GL_TEXTURE_BUFFER, GLenum.GL_RG32F, tmp_buf);
		gl.glBindTexture(GLenum.GL_TEXTURE_BUFFER, 0);
		
		plan.srv_tmp = texture;
		plan.buffer_tmp = tmp_buf;
	}
	
	public static void fft512x512_destroy_plan(CSFFT512x512_Plan plan){
		GLFuncProvider gl= GLFuncProviderFactory.getGLFuncProvider();
		if(plan.srv_tmp != 0) {gl.glDeleteTexture(plan.srv_tmp); plan.srv_tmp = 0;}
		if(plan.buffer_tmp != 0) {gl.glDeleteBuffer(plan.buffer_tmp); plan.buffer_tmp = 0;}
		
		if(plan.program != 0) {gl.glDeleteProgram(plan.program); plan.program = 0;}
		
		for(int i = 0 ; i < 6; i++){
			if(plan.radix008A_CB[i] != 0){
				gl.glDeleteBuffer(plan.radix008A_CB[i]);
				plan.radix008A_CB[i] = 0;
			}
		}
		
	}
	
	private static void create_cbuffers_512x512(CSFFT512x512_Plan plan, int slices){
		GLFuncProvider gl= GLFuncProviderFactory.getGLFuncProvider();
		// Create 6 cbuffers for 512x512 transform.
		
		final int target = GLenum.GL_SHADER_STORAGE_BUFFER;
		final int useage = GLenum.GL_STATIC_READ;
		FloatBuffer buf = CacheBuffer.getCachedFloatBuffer(5 * 4);
		
		// Buffer 0
		final int thread_count = slices * (512 * 512) / 8;
		int ostride = 512 * 512 / 8;
		int istride = ostride;
		double phase_base = -TWO_PI / (512.0 * 512.0);
		
		buf.put(Float.intBitsToFloat(thread_count));
		buf.put(Float.intBitsToFloat(ostride));
		buf.put(Float.intBitsToFloat(istride));
		buf.put(Float.intBitsToFloat(512));
		buf.put((float)phase_base);
		buf.flip();
		
		plan.radix008A_CB[0] = createBuffer(target, buf, useage);
		
		// Buffer 1
		istride /= 8;
		phase_base *= 8.0;
		
		buf.put(Float.intBitsToFloat(thread_count));
		buf.put(Float.intBitsToFloat(ostride));
		buf.put(Float.intBitsToFloat(istride));
		buf.put(Float.intBitsToFloat(512));
		buf.put((float)phase_base);
		buf.flip();
		
		plan.radix008A_CB[1] = createBuffer(target, buf, useage);
		
		// Buffer 2
		istride /= 8;
		phase_base *= 8.0;
		
		buf.put(Float.intBitsToFloat(thread_count));
		buf.put(Float.intBitsToFloat(ostride));
		buf.put(Float.intBitsToFloat(istride));
		buf.put(Float.intBitsToFloat(512));
		buf.put((float)phase_base);
		buf.flip();
		
		plan.radix008A_CB[2] = createBuffer(target, buf, useage);
		
		// Buffer 3
		istride /= 8;
		phase_base *= 8.0;
		ostride /= 512;
		
		buf.put(Float.intBitsToFloat(thread_count));
		buf.put(Float.intBitsToFloat(ostride));
		buf.put(Float.intBitsToFloat(istride));
		buf.put(Float.intBitsToFloat(1));
		buf.put((float)phase_base);
		buf.flip();
		
		plan.radix008A_CB[3] = createBuffer(target, buf, useage);
		
		// Buffer 4
		istride /= 8;
		phase_base *= 8.0;
		
		buf.put(Float.intBitsToFloat(thread_count));
		buf.put(Float.intBitsToFloat(ostride));
		buf.put(Float.intBitsToFloat(istride));
		buf.put(Float.intBitsToFloat(1));
		buf.put((float)phase_base);
		buf.flip();
		
		plan.radix008A_CB[4] = createBuffer(target, buf, useage);
		
		// Buffer 5
		istride /= 8;
		phase_base *= 8.0;
		
		buf.put(Float.intBitsToFloat(thread_count));
		buf.put(Float.intBitsToFloat(ostride));
		buf.put(Float.intBitsToFloat(istride));
		buf.put(Float.intBitsToFloat(1));
		buf.put((float)phase_base);
		buf.flip();
		
		plan.radix008A_CB[5] = createBuffer(target, buf, useage);
	}
	
	private static int createBuffer(int target, FloatBuffer buf_data, int useage){
		GLFuncProvider gl= GLFuncProviderFactory.getGLFuncProvider();
		int buf = gl.glGenBuffer();
		gl.glBindBuffer(target, buf);
		gl.glBufferData(target, buf_data, useage);
		gl.glBindBuffer(target, 0);
		
		return buf;
	}
	
	static final class CSFFT512x512_Plan{
		public int radix008A_CS;
		public int radix008A_CS2;
		public int program;
		
		// More than one array can be transformed at same time
		public int slices;
		
		// For 512x512 config, we need 6 constant buffers
		public final int[] radix008A_CB = new int[6];
		
		// Temporary buffers
		int buffer_tmp;
		int uav_tmp;
		int srv_tmp;
		
		int thread_count;
		int ostride;
		int istride;
		int pstride;
		int phase_base;

		private GLFuncProvider gl;

		void use(){ gl.glUseProgram(program);}
		void enableCS() { gl.glUniformSubroutinesui(GLenum.GL_COMPUTE_SHADER, radix008A_CS);}
		void enableCS2() { gl.glUniformSubroutinesui(GLenum.GL_COMPUTE_SHADER, radix008A_CS2);}
		
		void setShaderResource(int texture){ gl.glBindImageTexture(1, texture, 0, false, 0, GLenum.GL_READ_ONLY, GLenum.GL_RG32F);}
		void setUnorderedAccessViews(int texture){gl.glBindImageTexture(2, texture, 0, false, 0, GLenum.GL_WRITE_ONLY, GLenum.GL_RG32F);}
	}
}
