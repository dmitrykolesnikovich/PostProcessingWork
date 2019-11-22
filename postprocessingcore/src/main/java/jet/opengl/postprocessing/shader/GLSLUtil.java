package jet.opengl.postprocessing.shader;

import org.lwjgl.util.vector.Matrix2f;
import org.lwjgl.util.vector.Matrix3f;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.ReadableVector2f;
import org.lwjgl.util.vector.ReadableVector3f;
import org.lwjgl.util.vector.ReadableVector4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector2i;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.common.GLenum;
import jet.opengl.postprocessing.core.OpenGLProgram;
import jet.opengl.postprocessing.util.BufferUtils;
import jet.opengl.postprocessing.util.CachaRes;
import jet.opengl.postprocessing.util.CacheBuffer;
import jet.opengl.postprocessing.util.DebugTools;

public final class GLSLUtil {

	private static final int INT = GLSLTypeInfo.TYPE_INT;
	private static final int FLOAT = GLSLTypeInfo.TYPE_FLOAT;
	private static final int DOUBLE = GLSLTypeInfo.TYPE_DOUBLE;
	private static final int BOOL = GLSLTypeInfo.TYPE_BOOL;

	private static final HashMap<Integer, GLSLTypeInfo> TYPE_TO_NAMES = new HashMap<>();

	public static int compileShaderFromSource(CharSequence source, ShaderType type, boolean print_log){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		int shader = gl.glCreateShader(type.shader);
		gl.glShaderSource(shader, source);
		gl.glCompileShader(shader);
		
		if (!checkCompileError(shader, type.name(), source,print_log))
	        return 0;
		
		return shader;
	}
	
	public static int createProgramFromShaders(int vertexShader,int tessControlShader, int tessEvalateShader, int geometyShader, int fragmentShader, ProgramLinkTask taskBeforeLink){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		
		int program = gl.glCreateProgram();
		
		if(vertexShader != 0) gl.glAttachShader(program, vertexShader);
		if(tessControlShader != 0) gl.glAttachShader(program, tessControlShader);
		if(tessEvalateShader != 0) gl.glAttachShader(program, tessEvalateShader);
		if(geometyShader != 0) gl.glAttachShader(program, geometyShader);
		if(fragmentShader != 0) gl.glAttachShader(program, fragmentShader);
		
		if(taskBeforeLink != null)
			taskBeforeLink.invoke(program);
		
		gl.glLinkProgram(program);
	    
	    try {
			checkLinkError(program);
			return program;
		} catch (GLSLException e) {
			gl.glDeleteProgram(program);
			e.printStackTrace();
			return 0;
		}
	}
	
	/**
	 * Create a computePogram by the computeShader
	 * @param computeShader
	 * @param taskBeforeLink
	 * @return
	 */
	public static int createProgramFromShaders(int computeShader, ProgramLinkTask taskBeforeLink){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		int program = gl.glCreateProgram();
		
		gl.glAttachShader(program, computeShader);
		
		if(taskBeforeLink != null)
			taskBeforeLink.invoke(program);
		
		gl.glLinkProgram(program);
	    
	    try {
			checkLinkError(program);
			return program;
		} catch (GLSLException e) {
			gl.glDeleteProgram(program);
			e.printStackTrace();
			return 0;
		}
	}
	
	public static void checkLinkError(int program){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		
		int success = gl.glGetProgrami(program, GLenum.GL_LINK_STATUS);
	    if(success == 0){
//	    	int bufLength = gl.glGetProgrami(program, GLenum.GL_INFO_LOG_LENGTH);
//	    	if(bufLength > 0){
//	    		gl.glGetProgramInfoLog(program)
//	    		String buf = gl.glGetProgramInfoLog(program, bufLength);
//	    		throw new OpenGLException(String.format("compileProgram::Could not link program:\n%s\n", buf));
//	    	}
	    	String log = gl.glGetProgramInfoLog(program);
	    	if(log.length() > 0)
	    		throw new GLSLException(String.format("compileProgram::Could not link program:\n%s\n", log));
	    }
	}
	
	public static boolean checkCompileError(int shader, String shaderName, CharSequence source, boolean print_log){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		int compiled = gl.glGetShaderi(shader, GLenum.GL_COMPILE_STATUS);
		
		if(compiled == 0 || print_log){
//			if (compiled == 0) {
//	            throw new OpenGLException("Error compiling shader");
//	        }
//	        int infoLen = gl.glGetShaderi(shader, GLenum.GL_INFO_LOG_LENGTH);
			String buf = gl.glGetShaderInfoLog(shader);
	        if (buf.length() > 0) {
                StringTokenizer tokens = new StringTokenizer(buf, "\n");
                List<String> oldLines = new ArrayList<String>();
                List<Integer> oldMarks = new ArrayList<Integer>();
                List<Integer> lineCount = new ArrayList<Integer>();
                final String prefix = "ERROR: 0:";
                
                int c = 0;
                while(tokens.hasMoreTokens()){
                	String line = tokens.nextToken();
                	
                	if(line.startsWith(prefix)){
                		int mm = line.indexOf(':', prefix.length());
                		if(mm > 0){
                			String digit = line.substring(prefix.length(), mm);
                			try {
								int count = Integer.parseInt(digit);
								lineCount.add(count);
								oldMarks.add(c++);
								oldLines.add(line.substring(mm + 1));
							} catch (NumberFormatException e) {
								System.out.println(e);
							}
                			
                		}
                	}else{
                		oldLines.add(line);
                	}
                }
                
                if(lineCount.size() > 0){
                	StringBuilder sb;
                	List<CharSequence> lines = splitToLines(source);
                	
                	for(int i = 0; i < lineCount.size(); i++){
                		int lc = lineCount.get(i);
                		CharSequence line = lines.get(lc - 1);
                		c = oldMarks.get(i);
                		oldLines.set(c,lc + " " + oldLines.get(c) + "   <" + line + '>');
                	}
                	
                	int totalLength = 0;
                	for(String l : oldLines){
                		totalLength += l.length();
                	}
                	
                	sb= new StringBuilder(totalLength + oldLines.size() + 16);
                	for(String l : oldLines)
                		sb.append(l).append('\n');
                	
                	buf = sb.toString();
                }
                if(compiled == 0){
					DebugTools.saveErrorShaderSource(source);
                	gl.glDeleteShader(shader);
    	            shader = 0;
                	throw new GLSLException(String.format("%s Shader log:\n%s\n", shaderName, buf));
                }else
                	System.out.println(String.format("%s Shader log:\n%s\n", shaderName, buf));
	        }
	        if (compiled == 0) {
	            return false;
	        }
		}
		
		return true;
	}

	public static String getGLSLTypeName(int type){
		initlizeTypeInfos();

		GLSLTypeInfo info = TYPE_TO_NAMES.get(type);
		if(info != null)
			return info.name;
		else
			return "Unkown";
	}
	
	public static String getShaderName(int shader){
		switch (shader) {
		case GLenum.GL_VERTEX_SHADER:
			return "Vertex";
		case GLenum.GL_FRAGMENT_SHADER:
			return "Fragment";
		case GLenum.GL_GEOMETRY_SHADER:
			return "Geometry";
		case GLenum.GL_TESS_CONTROL_SHADER:
			return "Tess_Control";
		case GLenum.GL_TESS_EVALUATION_SHADER:
			return "Tess_Evaluation";
		case GLenum.GL_COMPUTE_SHADER:
			return "Compute";
		default:
			return "Unkown";
		}
	}
	
	public static int getUniformLocation(int program, CharSequence name){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		int location = gl.glGetUniformLocation(program, name);
		if(location == -1){
			throw new GLSLException("No found uniform location from the name: " + name);
		}
		
		return location;
	}
	
	public static int getAttribLocation(int program, CharSequence name){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		int location = gl.glGetAttribLocation(program, name);
		if(location == -1){
			throw new GLSLException("No found uniform location from the name: " + name);
		}
		
		return location;
	}
	
	/// --------------------------------- other stuff ----------------------------------------//////
	static List<CharSequence> splitToLines(CharSequence str){
		int pre = 0;
		int length = str.length();
		ArrayList<CharSequence> lines = new ArrayList<CharSequence>(128);
		
		for(int i=0; i < length; i ++){
			char c = str.charAt(i);
			if(c == '\n'){
				lines.add(str.subSequence(pre, i));
//				System.out.println(pre + ", " + i);
				pre = i + 1;
			}
		}
		
		if(pre < length){
			lines.add(str.subSequence(pre, length));
		}
		
		lines.trimToSize();
		return lines;
	}

	public static void assertUniformBuffer(OpenGLProgram program, String name, int index, int bufferSize) {
		if(!GLCheck.CHECK) return;

		final GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		int buffer_index = gl.glGetUniformBlockIndex(program.getProgram(), name);
		if(buffer_index >=0){
			IntBuffer params = CacheBuffer.getCachedIntBuffer(1);
			gl.glGetActiveUniformBlockiv(program.getProgram(), buffer_index, GLenum.GL_UNIFORM_BLOCK_BINDING, params);
			if(params.get(0) != index)
				throw new AssertionError("The binding point is not match!");

			gl.glGetActiveUniformBlockiv(program.getProgram(), buffer_index, GLenum.GL_UNIFORM_BLOCK_DATA_SIZE, params);
			if(bufferSize != params.get(0)){
				String msg = String.format("The size[%d] of uniform buffer[%s] doesn't match the given size[%d], The program name is %s",
						params.get(0), name, bufferSize, program.getName());
				throw new AssertionError(msg);
			}
		}
	}

	@Deprecated
	public static ProgramProperties getProperties(int programId){
		final GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		ProgramProperties property = new ProgramProperties();
		if(!gl.glIsProgram(programId))
			return property;

		property.programID = programId;
		property.delete_status = gl.glGetProgrami(programId, GLenum.GL_DELETE_STATUS) != 0;
		property.link_status = gl.glGetProgrami(programId, GLenum.GL_LINK_STATUS) != 0;
		property.validate_status = gl.glGetProgrami(programId, GLenum.GL_VALIDATE_STATUS) != 0;
		property.info_log_length = gl.glGetProgrami(programId, GLenum.GL_INFO_LOG_LENGTH);
		// shader properties.
		property.attached_shaders = gl.glGetProgrami(programId, GLenum.GL_ATTACHED_SHADERS);
		// atomic properties.
		property.active_atomic_counter_buffers = gl.glGetProgrami(programId, GLenum.GL_ACTIVE_ATOMIC_COUNTER_BUFFERS);
		// attributes properties.
		property.active_attributes = gl.glGetProgrami(programId, GLenum.GL_ACTIVE_ATTRIBUTES);
		property.active_attribute_properties = new AttribProperties[property.active_attributes];
		property.active_attribute_max_length = gl.glGetProgrami(programId, GLenum.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH);
		property.active_uniform_max_length = gl.glGetProgrami(programId, GLenum.GL_ACTIVE_UNIFORM_MAX_LENGTH);
		GLCheck.checkError();
//    	ByteBuffer[] bufs = GLUtil.getCachedByteBuffer(new int[]{4, 4, 4, property.active_attribute_max_length});
//    	ByteBuffer _length = BufferUtils.createByteBuffer(4);
		IntBuffer size = BufferUtils.createIntBuffer(1);
		IntBuffer type = BufferUtils.createIntBuffer(1);
//    	ByteBuffer name = BufferUtils.createByteBuffer(max_length);

		for(int i = 0; i < property.active_attributes; i++){
//    		GL20.glGetActiveAttrib(programId, i, property.active_attribute_max_length, _length, size, type, name);
			AttribProperties properties = new AttribProperties();
//    		int length = _length.getInt(0);
//    		name.get(bytes, 0, length).position(0);
			properties.name = gl.glGetActiveAttrib(programId, i, property.active_attribute_max_length, size, type);
			properties.size = size.get(0);
			properties.type = type.get(0);
//    		properties.name = new String(bytes, 0, length, Charset.forName("utf-8"));
			properties.location = gl.glGetAttribLocation(programId, properties.name);

			property.active_attribute_properties[i] = properties;
		}
		GLCheck.checkError();
		// uniforms properties.
		property.active_uniforms = gl.glGetProgrami(programId, GLenum.GL_ACTIVE_UNIFORMS);
		property.active_uniform_properties = new UniformProperty[property.active_uniforms];

		for(int i = 0; i < property.active_uniforms; i++){
			String name = gl.glGetActiveUniform(programId, i, property.active_uniform_max_length, size, type);

			UniformProperty properties = new UniformProperty();
			properties.size = size.get(0);
			properties.type = type.get(0);
//    		int length = _length.getInt(0);
//    		name.get(bytes, 0, length).position(0);
			properties.name = name; //new String(bytes, 0, length, Charset.forName("utf-8"));
			properties.location = gl.glGetUniformLocation(programId, name);
			property.active_uniform_properties[i] = properties;
			GLCheck.checkError();
			GLSLTypeInfo typeinfo = TYPE_TO_NAMES.get(properties.type);
			if(typeinfo == null){
				System.out.println("Unkonw type: 0x" + Integer.toHexString(properties.type));
				continue;
			}

			if((typeinfo.isSampler() || typeinfo.isImage()) && properties.location >=0){
				properties.value = gl.glGetUniformi(programId, properties.location);
				GLCheck.checkError();
			}else{
				properties.value = getUniformValue(programId, properties.location, properties.type, properties.size);
			}
		}


		return property;
	}

	public static Object getUniformValue(int programId, int location, int type, int size){
		final GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		if(location == -1)
			return null;

		switch (type) {
			case GLenum.GL_FLOAT:
			{
				if(size == 1){
					float f = gl.glGetUniformf(programId, location);
					return f;
				}else{// float array
					float[] array = new float[size];
					for(int i = 0; i < size; i++){
						array[i] = gl.glGetUniformf(programId, location+i);
					}
					return array;
				}

			}
			case GLenum.GL_INT:
			{
				if(size == 1){
					int i=gl.glGetUniformi(programId, location);
					return i;
				}else{// float array
					IntBuffer buf = CacheBuffer.getCachedIntBuffer(size);
					gl.glGetUniformiv(programId, location, buf);
					int[] array = new int[size];
					buf.get(array);
					return array;
				}
			}
			case GLenum.GL_UNSIGNED_INT:
			{
				if(size == 1){
					int i=gl.glGetUniformui(programId, location);
					return i;
				}else{// float array
					IntBuffer buf = CacheBuffer.getCachedIntBuffer(size);
					gl.glGetUniformuiv(programId, location, buf);
					int[] array = new int[size];
					buf.get(array);
					return array;
				}
			}

			case GLenum.GL_DOUBLE:
			{
				if(size == 1){
					double d = gl.glGetUniformd(programId, location);
					return d;
				}else{// float array
					DoubleBuffer buf = CacheBuffer.getCachedDoubleBuffer(size);
					gl.glGetUniformdv(programId, location, buf);
					double[] array = new double[size];
					buf.get(array);
					return array;
				}
			}

			case GLenum.GL_BOOL:
			{
				if(size == 1){
					boolean b = gl.glGetUniformi(programId, location) != 0;
					return b;
				}else{// float array
					IntBuffer buf = CacheBuffer.getCachedIntBuffer(size);
					gl.glGetUniformiv(programId, location, buf);
					boolean[] array = new boolean[size];
					for(int i = 0; i < array.length; i++)
						array[i] = buf.get() != 0;
					return array;
				}
			}

			case GLenum.GL_FLOAT_VEC2:
			{
				FloatBuffer buf = CacheBuffer.getCachedFloatBuffer(2);
				int count = size;
				if(count == 1){
					gl.glGetUniformfv(programId, location, buf);
					Vector2f out = new Vector2f();
					out.load(buf);
					return out;
				}else{
					Vector2f[] out = new Vector2f[count];
					for(int i = 0; i < count; i++){
						gl.glGetUniformfv(programId, location + i, buf);
						out[i] = new Vector2f(buf.get(), buf.get());
						buf.flip();
					}

					return out;
				}
			}

			case GLenum.GL_FLOAT_VEC3:
			{
				FloatBuffer buf = CacheBuffer.getCachedFloatBuffer(3);
				int count = size;
				if(count == 1){
					gl.glGetUniformfv(programId, location, buf);
					Vector3f out = new Vector3f();
					out.load(buf);
					return out;
				}else{
					Vector3f[] out = new Vector3f[count];
					for(int i = 0; i < count; i++){
						gl.glGetUniformfv(programId, location + i, buf);
						out[i] = new Vector3f(buf.get(), buf.get(), buf.get());
						buf.flip();
					}

					return out;
				}
			}

			case GLenum.GL_FLOAT_VEC4:
			{
				FloatBuffer buf = CacheBuffer.getCachedFloatBuffer(4);
				int count = size;
				if(count == 1){
					gl.glGetUniformfv(programId, location, buf);
					Vector4f out = new Vector4f();
					out.load(buf);
					return out;
				}else{
					Vector4f[] out = new Vector4f[count];
					for(int i = 0; i < count; i++){
						gl.glGetUniformfv(programId, location + i, buf);
						out[i] = new Vector4f(buf.get(), buf.get(), buf.get(), buf.get());
						buf.flip();
					}

					return out;
				}
			}

			case GLenum.GL_DOUBLE_VEC2:
			{
				DoubleBuffer buf = CacheBuffer.getCachedDoubleBuffer(2);
				int count = size;
				if(count == 1){
					gl.glGetUniformdv(programId, location, buf);
					Vector2f out = new Vector2f((float)buf.get(), (float)buf.get());
					return out;
				}else{
					Vector2f[] out = new Vector2f[count];
					for(int i = 0; i < count; i++){
						gl.glGetUniformdv(programId, location+i, buf);
						out[i] = new Vector2f((float)buf.get(), (float)buf.get());
						buf.flip();
					}

					return out;
				}
			}

			case GLenum.GL_DOUBLE_VEC3:
			{
				DoubleBuffer buf = CacheBuffer.getCachedDoubleBuffer(3);
				int count = size;
				if(count == 1){
					gl.glGetUniformdv(programId, location, buf);
					Vector3f out = new Vector3f((float)buf.get(), (float)buf.get(), (float)buf.get());
					return out;
				}else{
					Vector3f[] out = new Vector3f[count];
					for(int i = 0; i < count; i++){
						gl.glGetUniformdv(programId, location+i, buf);
						out[i] = new Vector3f((float)buf.get(), (float)buf.get(), (float)buf.get());
						buf.flip();
					}

					return out;
				}
			}

			case GLenum.GL_DOUBLE_VEC4:
			{
				DoubleBuffer buf = CacheBuffer.getCachedDoubleBuffer(4);
				int count = size;
				if(count == 1){
					gl.glGetUniformdv(programId, location, buf);
					Vector4f out = new Vector4f((float)buf.get(), (float)buf.get(), (float)buf.get(), (float)buf.get());
					return out;
				}else{
					Vector4f[] out = new Vector4f[count];
					for(int i = 0; i < count; i++){
						gl.glGetUniformdv(programId, location+i, buf);
						out[i] = new Vector4f((float)buf.get(), (float)buf.get(), (float)buf.get(), (float)buf.get());
						buf.flip();
					}
					return out;
				}
			}
			case GLenum.GL_FLOAT_MAT2:
			{
				FloatBuffer buf = CacheBuffer.getCachedFloatBuffer(4);
				if(size == 1){
					gl.glGetUniformfv(programId, location, buf);
					Matrix2f mat = new Matrix2f();
					mat.load(buf);
					return mat;
				}else{
					Matrix2f mats[] = new Matrix2f[size];
					for(int i = 0; i < size; i++){
						gl.glGetUniformfv(programId, location+i, buf);
						mats[i] = new Matrix2f();
						mats[i].load(buf);
						buf.flip();
					}

					return mats;
				}
			}

			case GLenum.GL_FLOAT_MAT3:
			{
				FloatBuffer buf = CacheBuffer.getCachedFloatBuffer(size * 9);
				gl.glGetUniformfv(programId, location, buf);
				if(size == 1){
					Matrix3f mat = new Matrix3f();
					mat.load(buf);
					return mat;
				}else{
					Matrix3f mats[] = new Matrix3f[size];
					for(int i = 0; i < size; i++){
						mats[i] = new Matrix3f();
						mats[i].load(buf);
					}

					return mats;
				}
			}

			case GLenum.GL_FLOAT_MAT4:
			{
				FloatBuffer buf = CacheBuffer.getCachedFloatBuffer(16);
				if(size == 1){
					gl.glGetUniformfv(programId, location, buf);
					Matrix4f mat = new Matrix4f();
					mat.load(buf);
					return mat;
				}else{
					Matrix4f mats[] = new Matrix4f[size];
					for(int i = 0; i < size; i++){
						gl.glGetUniformfv(programId, location + i, buf);
						mats[i] = new Matrix4f();
						mats[i].load(buf);
						buf.flip();
					}

					return mats;
				}
			}

			case GLenum.GL_INT_VEC2:
			{
				IntBuffer buf = CacheBuffer.getCachedIntBuffer(size << 1);
				gl.glGetUniformiv(programId, location, buf);
				int count = size;
				if(count == 1){
					Vector2i out = new Vector2i();
					out.load(buf);
					return out;
				}else{
					Vector2i[] out = new Vector2i[count];
					for(int i = 0; i < count; i++){
						out[i] = new Vector2i(buf.get(), buf.get());
					}

					return out;
				}
			}

			case GLenum.GL_INT_VEC3:
			{
				IntBuffer buf = CacheBuffer.getCachedIntBuffer(size * 3);
				gl.glGetUniformiv(programId, location, buf);
				int[] out = new int[3 * size];
				buf.get(out);
				return out;
			}

			case GLenum.GL_INT_VEC4:
			{
				IntBuffer buf = CacheBuffer.getCachedIntBuffer(size << 2);
				gl.glGetUniformiv(programId, location, buf);
				int[] out = new int[4 * size];
				buf.get(out);
				return out;
			}

			case GLenum.GL_UNSIGNED_INT_VEC2:
			{
				IntBuffer buf = CacheBuffer.getCachedIntBuffer(size << 1);
				gl.glGetUniformuiv(programId, location, buf);
				int count = size;
				if(count == 1){
					Vector2i out = new Vector2i();
					out.load(buf);
					return out;
				}else{
					Vector2i[] out = new Vector2i[count];
					for(int i = 0; i < count; i++){
						out[i] = new Vector2i(buf.get(), buf.get());
					}

					return out;
				}
			}

			case GLenum.GL_UNSIGNED_INT_VEC3:
			{
				IntBuffer buf = CacheBuffer.getCachedIntBuffer(size * 3);
				gl.glGetUniformuiv(programId, location, buf);
				int[] out = new int[3 * size];
				buf.get(out);
				return out;
			}

			case GLenum.GL_UNSIGNED_INT_VEC4:
			{
				IntBuffer buf = CacheBuffer.getCachedIntBuffer(size << 2);
				gl.glGetUniformuiv(programId, location, buf);
				int[] out = new int[4 * size];
				buf.get(out);
				return out;
			}
			case GLenum.GL_SAMPLER_1D:
			case GLenum.GL_SAMPLER_2D:
			case GLenum.GL_SAMPLER_3D:
			case GLenum.GL_SAMPLER_CUBE:
			case GLenum.GL_SAMPLER_1D_SHADOW:
			case GLenum.GL_SAMPLER_2D_SHADOW:
			case GLenum.GL_SAMPLER_1D_ARRAY:
			case GLenum.GL_SAMPLER_2D_ARRAY:
			case GLenum.GL_SAMPLER_1D_ARRAY_SHADOW:
			case GLenum.GL_SAMPLER_2D_ARRAY_SHADOW:
			case GLenum.GL_SAMPLER_2D_MULTISAMPLE:
			case GLenum.GL_SAMPLER_2D_MULTISAMPLE_ARRAY:
			case GLenum.GL_SAMPLER_CUBE_SHADOW:
			case GLenum.GL_SAMPLER_BUFFER:
			case GLenum.GL_SAMPLER_2D_RECT:
			case GLenum.GL_INT_SAMPLER_1D:
			case GLenum.GL_INT_SAMPLER_2D:
			case GLenum.GL_INT_SAMPLER_3D:
			case GLenum.GL_INT_SAMPLER_CUBE:
			case GLenum.GL_INT_SAMPLER_1D_ARRAY:
			case GLenum.GL_INT_SAMPLER_2D_ARRAY:
			case GLenum.GL_INT_SAMPLER_2D_MULTISAMPLE:
			case GLenum.GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY:
			case GLenum.GL_INT_SAMPLER_BUFFER:
			case GLenum.GL_INT_SAMPLER_2D_RECT:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_1D:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_2D:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_3D:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_CUBE:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_1D_ARRAY:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_2D_ARRAY:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_BUFFER:
			case GLenum.GL_UNSIGNED_INT_SAMPLER_2D_RECT:
			case GLenum.GL_IMAGE_1D:
			case GLenum.GL_IMAGE_2D:
			case GLenum.GL_IMAGE_3D:
			case GLenum.GL_IMAGE_2D_RECT:
			case GLenum.GL_IMAGE_CUBE:
			case GLenum.GL_IMAGE_BUFFER:
			case GLenum.GL_IMAGE_1D_ARRAY:
			case GLenum.GL_IMAGE_2D_ARRAY:
			case GLenum.GL_IMAGE_2D_MULTISAMPLE:
			case GLenum.GL_IMAGE_2D_MULTISAMPLE_ARRAY:
			case GLenum.GL_INT_IMAGE_1D:
			case GLenum.GL_INT_IMAGE_2D:
			case GLenum.GL_INT_IMAGE_3D:
			case GLenum.GL_INT_IMAGE_2D_RECT:
			case GLenum.GL_INT_IMAGE_CUBE:
			case GLenum.GL_INT_IMAGE_BUFFER:
			case GLenum.GL_INT_IMAGE_1D_ARRAY:
			case GLenum.GL_INT_IMAGE_2D_ARRAY:
			case GLenum.GL_INT_IMAGE_2D_MULTISAMPLE:
			case GLenum.GL_INT_IMAGE_2D_MULTISAMPLE_ARRAY:
			case GLenum.GL_UNSIGNED_INT_IMAGE_1D:
			case GLenum.GL_UNSIGNED_INT_IMAGE_2D:
			case GLenum.GL_UNSIGNED_INT_IMAGE_3D:
			case GLenum.GL_UNSIGNED_INT_IMAGE_2D_RECT:
			case GLenum.GL_UNSIGNED_INT_IMAGE_CUBE:
			case GLenum.GL_UNSIGNED_INT_IMAGE_BUFFER:
			case GLenum.GL_UNSIGNED_INT_IMAGE_1D_ARRAY:
			case GLenum.GL_UNSIGNED_INT_IMAGE_2D_ARRAY:
			case GLenum.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE:
			case GLenum.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY:
			case GLenum.GL_UNSIGNED_INT_ATOMIC_COUNTER:

			{
				return gl.glGetUniformi(programId, location);
			}
		}

		return null;
	}

	private static void initlizeTypeInfos(){
		if(!TYPE_TO_NAMES.isEmpty())
			return;

		TYPE_TO_NAMES.put(GLenum.GL_FLOAT, type("float", float.class, FLOAT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT, type("int", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT, type("uint", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE, type("double", double.class, DOUBLE, 8));

		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_VEC2, type("vec2", Vector2f.class, FLOAT, 2 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_VEC3, type("vec3", Vector3f.class, FLOAT, 3 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_VEC4, type("vec4", Vector4f.class, FLOAT, 4 << 2));

		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_VEC2, type("dvec2", null, DOUBLE, 2 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_VEC3, type("dvec3", null, DOUBLE, 3 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_VEC4, type("dvec4", null, DOUBLE, 4 << 2));

		TYPE_TO_NAMES.put(GLenum.GL_INT_VEC2, type("ivec2", Vector2i.class, FLOAT, 2 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_INT_VEC3, type("ivec3", null, INT, 3 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_INT_VEC4, type("ivec4", null, INT, 4 << 2));

		TYPE_TO_NAMES.put(GLenum.GL_BOOL, type("bool", boolean.class, BOOL, 4));
		TYPE_TO_NAMES.put(GLenum.GL_BOOL_VEC2, type("bvec2", Vector2i.class, BOOL, 2 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_BOOL_VEC3, type("bvec3", null, BOOL, 3 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_BOOL_VEC4, type("bvec4", null, BOOL, 4 << 2));

		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_VEC2, type("uvec2", Vector2i.class, FLOAT, 2 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_VEC3, type("uvec3", null, INT, 3 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_VEC4, type("uvec4", null, INT, 4 << 2));

		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_1D, type("sampler1D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D, type("sampler2D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_3D, type("sampler3D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_1D_SHADOW, type("sampler1DShdow", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D_SHADOW, type("sampler2DShdow", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_CUBE, type("samplerCube", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_1D_ARRAY, type("sampler1DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D_ARRAY, type("sampler2DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_1D_ARRAY_SHADOW, type("sampler1DArrayShadow", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D_ARRAY_SHADOW, type("sampler2DArrayShadow", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_CUBE_SHADOW, type("samplerCubeShadow", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_1D, type("isampler1D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_1D_ARRAY, type("isampler1DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_2D, type("isampler2D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_2D_ARRAY, type("isampler2DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_3D, type("isampler3D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_CUBE, type("isamplerCube", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_1D, type("usampler1D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_2D, type("usampler2D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_3D, type("usampler3D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_CUBE, type("usamplerCube", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_1D_ARRAY, type("usampler1DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_2D_ARRAY, type("usampler2DArray", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D_RECT, type("sampler2DRect", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_2D_RECT, type("isampler2DRect", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_2D_RECT, type("usampler2DRect", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D_RECT_SHADOW, type("sampler2DRectShadow", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_BUFFER, type("samplerBuffer", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_BUFFER, type("isamplerBuffer", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_BUFFER, type("usamplerBuffer", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D_MULTISAMPLE, type("sampler2DMS", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_2D_MULTISAMPLE_ARRAY, type("sampler2DMSArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_2D_MULTISAMPLE, type("isampler2DMS", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY, type("isampler2DMSArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE, type("usampler2DMS", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY, type("usampler2DMSArray", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_CUBE_MAP_ARRAY, type("samplerCubeArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_SAMPLER_CUBE_MAP_ARRAY_SHADOW, type("samplerCubeArrayShadow", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_SAMPLER_CUBE_MAP_ARRAY, type("isamplerCubeArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_SAMPLER_CUBE_MAP_ARRAY, type("usamplerCubeArray", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_1D, type("image1D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_1D, type("iimage1D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_1D, type("uimage1D", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_2D, type("image2D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_2D, type("iimage2D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_2D, type("uimage2D", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_3D, type("image3D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_3D, type("iimage3D", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_3D, type("uimage3D", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_2D_RECT, type("image2DRect", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_2D_RECT, type("iimage2DRect", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_2D_RECT, type("uimage2DRect", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_CUBE, type("imageCube", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_CUBE, type("iimageCube", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_CUBE, type("uimageCube", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_BUFFER, type("imageBuffer", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_BUFFER, type("iimageBuffer", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_BUFFER, type("uimageBuffer", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_1D_ARRAY, type("image1DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_1D_ARRAY, type("iimage1DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_1D_ARRAY, type("uimage1DArray", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_2D_ARRAY, type("image2DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_2D_ARRAY, type("iimage2DArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_2D_ARRAY, type("uimage2DArray", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_CUBE_MAP_ARRAY, type("imageCubeArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_CUBE_MAP_ARRAY, type("iimageCubeArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_CUBE_MAP_ARRAY, type("uimageCubeArray", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_2D_MULTISAMPLE, type("image2DMS", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_2D_MULTISAMPLE, type("iimage2DMS", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE, type("uimage2DMS", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_IMAGE_2D_MULTISAMPLE_ARRAY, type("image2DMSArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_INT_IMAGE_2D_MULTISAMPLE_ARRAY, type("iimage2DMSArray", int.class, INT, 4));
		TYPE_TO_NAMES.put(GLenum.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY, type("uimage2DMSArray", int.class, INT, 4));

		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT2, type("mat2", Matrix2f.class, FLOAT, 4 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT3, type("mat3", Matrix3f.class, FLOAT, 9 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT4, type("mat4", Matrix4f.class, FLOAT, 16 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT2x3, type("mat2x3", null, FLOAT, 6 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT2x4, type("mat2x4", null, FLOAT, 8 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT3x2, type("mat3x2", null, FLOAT, 6 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT3x4, type("mat3x4", null, FLOAT, 12 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT4x2, type("mat4x2", null, FLOAT, 8 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_FLOAT_MAT4x3, type("mat4x3", null, FLOAT, 12 << 2));

		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT2, type("dmat2", null, DOUBLE, 4 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT3, type("dmat3", null, DOUBLE, 9 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT4, type("dmat4", null, DOUBLE, 16 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT2x3, type("dmat2x3", null, DOUBLE, 6 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT2x4, type("dmat2x4", null, DOUBLE, 8 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT3x2, type("dmat3x2", null, DOUBLE, 6 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT3x4, type("dmat3x4", null, DOUBLE, 12 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT4x2, type("dmat4x2", null, DOUBLE, 8 << 2));
		TYPE_TO_NAMES.put(GLenum.GL_DOUBLE_MAT4x3, type("dmat4x3", null, DOUBLE, 12 << 2));
	}

	private static GLSLTypeInfo type(String name, Class<?> clazz, int dataType, int size){
		return new GLSLTypeInfo(name, clazz, dataType, size);
	}

	@CachaRes
	public static ProgramResources getProgramResources(int programId){
		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		if(!gl.glIsProgram(programId) && !gl.glIsProgramPipeline(programId))
			return null;

		ProgramResources resources = new ProgramResources();
		getUniformResources(gl, programId, resources);
		getUniformBlockResources(gl, programId, resources, GLenum.GL_UNIFORM_BLOCK);
		getUniformBlockResources(gl, programId, resources, GLenum.GL_SHADER_STORAGE_BLOCK);

		return resources;
	}

	private static void getUniformBlockResources(GLFuncProvider gl, int programId, ProgramResources resources, int programinterface){
		int num_active_uniforms = gl.glGetProgramInterfacei(programId, programinterface, GLenum.GL_ACTIVE_RESOURCES);
		UniformBlockType type;
		if(programinterface == GLenum.GL_UNIFORM_BLOCK){
			type = UniformBlockType.UNFIORM_BLOCK;
		}else if(programinterface == GLenum.GL_SHADER_STORAGE_BLOCK){
			type = UniformBlockType.UNIFORM_BUFFER;
		}else{
			type = null;
		}

		if(num_active_uniforms > 0){
			IntBuffer props = _BufferCache.buf0;
			IntBuffer length = _BufferCache.buf1;
			IntBuffer params = _BufferCache.buf2;

			for (int i = 0; i < num_active_uniforms; i++){
				// get the length of the uniform name.
				props.put(0, GLenum.GL_NAME_LENGTH);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);
				String buffer_name = gl.glGetProgramResourceName(programId, programinterface, i, params.get(0));
				GLCheck.checkError();

				props.put(0, GLenum.GL_BUFFER_BINDING);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);
				int buffer_binding = params.get(0);
				GLCheck.checkError();

				props.put(0, GLenum.GL_NUM_ACTIVE_VARIABLES);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);
				int num_active_variables = params.get(0);
				GLCheck.checkError();

				props.put(0, GLenum.GL_BUFFER_DATA_SIZE);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);
				int buffer_data_size = params.get(0);
				GLCheck.checkError();

				UniformBlockProperties blockProperties = new UniformBlockProperties();
				blockProperties.name = buffer_name;
				blockProperties.size = buffer_data_size;
				blockProperties.type = type;
				blockProperties.binding = buffer_binding;

				resources.uniformBlockProperties.add(blockProperties);
			}
		}
	}

	// Retrive all of the uniforms about the specifiied program, only support programinterface is GL_UNIFORM.
	private static void getUniformResources(GLFuncProvider gl, int programId, ProgramResources resources){
		GLCheck.checkError();
		final int programinterface = GLenum.GL_UNIFORM;
		int num_active_uniforms = gl.glGetProgramInterfacei(programId, programinterface, GLenum.GL_ACTIVE_RESOURCES);GLCheck.checkError();
		if(num_active_uniforms > 0) {
			IntBuffer props = _BufferCache.buf0;
			IntBuffer length = _BufferCache.buf1;
			IntBuffer params = _BufferCache.buf2;

			List<UniformProperty> uniformProperties = new ArrayList<>();
			for (int i = 0; i < num_active_uniforms; i++){
				// get the length of the uniform name.
				props.put(0, GLenum.GL_NAME_LENGTH);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);
				String uniformy_name = gl.glGetProgramResourceName(programId, programinterface, i, params.get(0));
				GLCheck.checkError();
				// get the type of the uniform.
				props.put(0, GLenum.GL_TYPE);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				int uniform_type = params.get(0);
//				String uniform_type_name = getGLSLTypeName(uniform_type);

				// get the array size of the uniform.
				props.put(0, GLenum.GL_ARRAY_SIZE);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				int uniform_array_size = params.get(0);

				// get the offset in the block of the uniform.
				props.put(0, GLenum.GL_OFFSET);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				int uniform_offset = params.get(0);

				// get the offset in the block of the uniform.
				props.put(0, GLenum.GL_BLOCK_INDEX);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				int uniform_block_index = params.get(0);

				// get the location of the uniform.
				props.put(0, GLenum.GL_LOCATION);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				int uniform_location = params.get(0);

				// get the array stride  of the uniform.
				props.put(0, GLenum.GL_ARRAY_STRIDE);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				int uniform_array_stride = params.get(0);

				// get the matrix stride  of the uniform.
				props.put(0, GLenum.GL_MATRIX_STRIDE);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				int uniform_matrix_stride = params.get(0);

				// get the matrix mojor  of the uniform.
				props.put(0, GLenum.GL_IS_ROW_MAJOR);
				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);GLCheck.checkError();
				boolean unfiorm_is_row_major = params.get(0) != 0;

				// get the atomic buffer index of the uniform.
//				props.put(0, GLenum.GL_IS_ROW_MAJOR);
//				gl.glGetProgramResourceiv(programId, programinterface, i, props, length, params);
//				int unfiorm_atomic_buffer_index = params.get(0);

				if(uniform_block_index <0){
					Object uniform_value = getUniformValue(programId, uniform_location, uniform_type, Math.max(1, uniform_array_size));GLCheck.checkError();
					UniformProperty uniformProperty = new UniformProperty();
					uniformProperty.isBelongBlock = false;
					uniformProperty.name = uniformy_name;
					uniformProperty.type = uniform_type;
					uniformProperty.arrayStride = uniform_array_stride;
					uniformProperty.isRowMajor = unfiorm_is_row_major;
					uniformProperty.location = uniform_location;
					uniformProperty.size = Math.max(1, uniform_array_size);
					uniformProperty.matrixStride = uniform_matrix_stride;
					uniformProperty.value = uniform_value;
					uniformProperty.offset = uniform_offset;

					uniformProperties.add(uniformProperty);
				}else{
					// other uniform block
//					resources.uniformBlockProperties.add(new UniformBlockProperties(uniform_block_index));
				}
			}

			resources.active_uniform_properties = uniformProperties.toArray(new UniformProperty[uniformProperties.size()]);
		}

		GLCheck.checkError();
	}

	// Lazy initliztion...
	private static final class _BufferCache{
		static final IntBuffer buf0 = BufferUtils.createIntBuffer(1);
		static final IntBuffer buf1 = BufferUtils.createIntBuffer(1);
		static final IntBuffer buf2 = BufferUtils.createIntBuffer(1);
	}

	public static void setBool(GLSLProgram prog, String name, boolean v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform1i(index, v?1:0);
	}

	public static void setFloat(GLSLProgram prog, String name, float v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glProgramUniform1f(prog.getProgram(), index, v);
	}

	public static void setFloat2(GLSLProgram prog, String name, float x, float y){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform2f(index, x, y);
	}

	public static void setFloat3(GLSLProgram prog, String name, float x, float y, float z){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform3f(index, x,y,z);
	}

	public static void setFloat4(GLSLProgram prog, String name, float x, float y, float z, float w){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform4f(index, x,y,z, w);
	}

	public static void setMat4(GLSLProgram prog, String name, Matrix4f v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniformMatrix4fv(index, false, CacheBuffer.wrap(v));
	}

	public static void setMat4(GLSLProgram prog, String name, Matrix4f[] v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniformMatrix4fv(index, false, CacheBuffer.wrap(v));
	}

	public static void setFloat2(GLSLProgram prog, String name, ReadableVector2f v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform2f(index, v.getX(), v.getY());
	}

	public static void setFloat3(GLSLProgram prog, String name, ReadableVector3f v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform3f(index, v.getX(), v.getY(), v.getZ());
	}

	public static void setFloat4(GLSLProgram prog, String name, ReadableVector4f v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform4f(index, v.getX(),v.getY(),v.getZ(), v.getW());
	}

	public static void setFloat4(GLSLProgram prog, String name, ReadableVector4f[] v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0){
			GLFuncProviderFactory.getGLFuncProvider().glUniform4fv(index, CacheBuffer.wrap(v));
		}
	}

	public static void setInt(GLSLProgram prog, String name, int v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform1i(index, v);
	}

	public static void setUInt(GLSLProgram prog, String name, int v){
		int index = prog.getUniformLocation(name, true);
		if(index >=0)
			GLFuncProviderFactory.getGLFuncProvider().glUniform1ui(index, v);
	}

	public static void fenceSync(){
		GLCheck.checkError();

		GLFuncProvider gl = GLFuncProviderFactory.getGLFuncProvider();
		long s = gl.glFenceSync();

		while (gl.glClientWaitSync(s, 0, 1_000_000) == GLenum.GL_TIMEOUT_EXPIRED){
			/*try { TODO This cause the rendering unsmoothly.
				Thread.currentThread().sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}*/
		}

		gl.glDeleteSync(s);
	}
}
