package jet.opengl.demos.nvidia.waves.samples;

import jet.opengl.postprocessing.common.GLCheck;
import jet.opengl.postprocessing.common.GLFuncProvider;
import jet.opengl.postprocessing.common.GLFuncProviderFactory;
import jet.opengl.postprocessing.util.CacheBuffer;

/*public*/ class IsUniformData {
	static boolean out_print = true;
	
	int mvpOffset = -1;  //mat4 g_ModelViewProjectionMatrix;
	int lmvpOffset = -1; // mat4 g_LightModelViewProjectionMatrix;
	int mvOffset = -1;   // mat4 g_ModelViewMatrix;
	int dmvpOffset = -1; // mat4 g_DepthModelViewProjectionMatrix;
	int cpOffset = -1;   // vec3 g_CameraPosition;
	int fcOffset = -1;   // int g_FrustumCullInHS;
	int cdOffset = -1;   // vec3 g_CameraDirection;
	int tbrOffset = -1;  // float g_TerrainBeingRendered;
	 
	int wbtsOffset = -1; // vec2 g_WaterBumpTexcoordShift;
	int stfOffset = -1;  // float g_StaticTessFactor;
	int dtfOffset = -1;  // float g_DynamicTessFactor;
	int udLODOffset = -1;// float g_UseDynamicLOD;
	int sccOffset = -1;  // int   g_SkipCausticsCalculation;
	int rcOffset = -1;   // float g_RenderCaustics;
	int hscsOffset = -1; // float g_HalfSpaceCullSign;
	int hscpOffset = -1; // float g_HalfSpaceCullPosition;
	 
	int lpOffset = -1;   // vec3  g_LightPosition;
	int ssiOffset = -1;  // vec2  g_ScreenSizeInv;
	int znearOffset = -1;// float	   g_ZNear;
	int zfarOffset = -1; // float	   g_ZFar;

//	uniform mat4 g_WorldToTopDownTextureMatrix;
//	uniform float g_Time;
//	uniform float g_BaseGerstnerWavelength;
//	uniform float g_BaseGerstnerParallelness;
//	uniform float g_BaseGerstnerSpeed;
//	uniform float g_BaseGerstnerAmplitude;
//	uniform vec2  g_WindDirection;
//	uniform float g_GerstnerSteepness;
//	uniform int g_enableShoreEffects;
//	uniform int g_ApplyFog;

	int tdtmOffset = -1; // g_WorldToTopDownTextureMatrix
	int timeOffset = -1; // g_Time
	int baseGerstnerWavelengthOffset = -1; //g_BaseGerstnerWavelength
	int baseGerstnerParallelnessOffset = -1; //g_BaseGerstnerParallelness
	int baseGerstnerSpeedOffset = -1; //g_BaseGerstnerSpeed
	int baseGerstnerAmplitudeOffset = -1; //g_BaseGerstnerAmplitude
	int windDirectionOffset = -1; //g_WindDirection
	int gerstnerSteepnessOffset = -1; //g_GerstnerSteepness
	int enableShoreOffset = -1; //g_enableShoreEffects
	int applyFogOffset = -1; //g_ApplyFog
	int heightFieldSizeOffset = -1;
	int wireFrameOffset = -1;

	int programId;
	String debug_name;
	
	private GLFuncProvider gl;
	
	public IsUniformData(String debug_name, int programId) {
		gl= GLFuncProviderFactory.getGLFuncProvider();
		this.debug_name = debug_name;
		this.programId = programId;

		tdtmOffset = getUniformIndex("g_WorldToTopDownTextureMatrix");
		timeOffset = getUniformIndex("g_Time");
		baseGerstnerWavelengthOffset = getUniformIndex("g_BaseGerstnerWavelength");
		baseGerstnerParallelnessOffset = getUniformIndex("g_BaseGerstnerParallelness");
		baseGerstnerSpeedOffset = getUniformIndex("g_BaseGerstnerSpeed");
		baseGerstnerAmplitudeOffset = getUniformIndex("g_BaseGerstnerAmplitude");
		windDirectionOffset = getUniformIndex("g_WindDirection");
		gerstnerSteepnessOffset = getUniformIndex("g_GerstnerSteepness");
		enableShoreOffset = getUniformIndex("g_enableShoreEffects");
		applyFogOffset = getUniformIndex("g_ApplyFog");

		mvpOffset = getUniformIndex("g_ModelViewProjectionMatrix");
		lmvpOffset = getUniformIndex("g_LightModelViewProjectionMatrix");
		mvOffset = getUniformIndex("g_ModelViewMatrix");
		dmvpOffset = getUniformIndex("g_DepthModelViewProjectionMatrix");
		cpOffset = getUniformIndex("g_CameraPosition");
		fcOffset = getUniformIndex("g_FrustumCullInHS");
		cdOffset = getUniformIndex("g_CameraDirection");
		tbrOffset = getUniformIndex("g_TerrainBeingRendered");
		wbtsOffset = getUniformIndex("g_WaterBumpTexcoordShift");
		stfOffset = getUniformIndex("g_StaticTessFactor");
		dtfOffset = getUniformIndex("g_DynamicTessFactor");
		udLODOffset = getUniformIndex("g_UseDynamicLOD");
		sccOffset = getUniformIndex("g_SkipCausticsCalculation");
		rcOffset = getUniformIndex("g_RenderCaustics");
		hscsOffset = getUniformIndex("g_HalfSpaceCullSign");
		hscpOffset = getUniformIndex("g_HalfSpaceCullPosition");
		lpOffset = getUniformIndex("g_LightPosition");
		ssiOffset = getUniformIndex("g_ScreenSizeInv");
		znearOffset = getUniformIndex("g_ZNear");
		zfarOffset = getUniformIndex("g_ZFar");
		heightFieldSizeOffset = getUniformIndex("g_HeightFieldSize");
		wireFrameOffset = getUniformIndex("g_Wireframe");
	}
	
	void setParameters(IsParameters params){
		if(mvpOffset != -1)
			gl.glUniformMatrix4fv(mvpOffset, false, CacheBuffer.wrap(params.g_ModelViewProjectionMatrix));
		
		if(lmvpOffset != -1)
			gl.glUniformMatrix4fv(lmvpOffset, false, CacheBuffer.wrap(params.g_LightModelViewProjectionMatrix));
		if(mvOffset != -1)
			gl.glUniformMatrix4fv(mvOffset, false, CacheBuffer.wrap(params.g_ModelViewMatrix));
		if(dmvpOffset != -1)
			gl.glUniformMatrix4fv(dmvpOffset, false, CacheBuffer.wrap(params.g_DepthModelViewProjectionMatrix));
		if(cpOffset != -1)
			gl.glUniform3f(cpOffset, params.g_CameraPosition.x, params.g_CameraPosition.y, params.g_CameraPosition.z);
		if(fcOffset != -1)
			gl.glUniform1i(fcOffset, params.g_FrustumCullInHS ? 1 : 0);
		if(cdOffset != -1)
			gl.glUniform3f(cdOffset, params.g_CameraDirection.x, params.g_CameraDirection.y, params.g_CameraDirection.z);
		if(tbrOffset != -1)
			gl.glUniform1f(tbrOffset, params.g_TerrainBeingRendered);
		if(wbtsOffset != -1)
			gl.glUniform2f(wbtsOffset, params.g_WaterBumpTexcoordShift.x, params.g_WaterBumpTexcoordShift.y);
		if(stfOffset != -1)
			gl.glUniform1f(stfOffset, params.g_StaticTessFactor);
		if(dtfOffset != -1)
			gl.glUniform1f(dtfOffset, params.g_DynamicTessFactor);
		if(udLODOffset != -1)
			gl.glUniform1f(udLODOffset, params.g_UseDynamicLOD ? 1 : 0);
		if(sccOffset != -1)
			gl.glUniform1i(sccOffset, params.g_SkipCausticsCalculation);
		if(rcOffset != -1)
			gl.glUniform1f(rcOffset, params.g_RenderCaustics ? 1: 0);
		if(hscsOffset != -1)
			gl.glUniform1f(hscsOffset, params.g_HalfSpaceCullSign);
		if(hscpOffset != -1)
			gl.glUniform1f(hscpOffset, params.g_HalfSpaceCullPosition);
		if(lpOffset != -1)
			gl.glUniform3f(lpOffset, params.g_LightPosition.x, params.g_LightPosition.y, params.g_LightPosition.z);
		if(ssiOffset != -1)
			gl.glUniform2f(ssiOffset, params.g_ScreenSizeInv.x, params.g_ScreenSizeInv.y);
		if(znearOffset != -1)
			gl.glUniform1f(znearOffset, params.g_ZNear);
		if(zfarOffset != -1)
			gl.glUniform1f(zfarOffset, params.g_ZFar);
		if(heightFieldSizeOffset != -1)
			gl.glUniform1f(heightFieldSizeOffset, params.g_HeightFieldSize);

		if(wireFrameOffset != -1)
			gl.glUniform1f(wireFrameOffset, params.g_Wireframe ? 1.0f: 0.0f);

		GLCheck.checkError();
//		int tdtmOffset = -1; // g_WorldToTopDownTextureMatrix
//		int timeOffset = -1; // g_Time
//		int baseGerstnerWavelengthOffset = -1; //g_BaseGerstnerWavelength
//		int baseGerstnerParallelnessOffset = -1; //g_BaseGerstnerParallelness
//		int baseGerstnerSpeedOffset = -1; //g_BaseGerstnerSpeed
//		int baseGerstnerAmplitudeOffset = -1; //g_BaseGerstnerAmplitude
//		int windDirectionOffset = -1; //g_WindDirection
//		int gerstnerSteepnessOffset = -1; //g_GerstnerSteepness
//		int enableShoreOffset = -1; //g_enableShoreEffects
//		int applyFogOffset = -1; //g_ApplyFog

		GLCheck.checkError();
		if(tdtmOffset != -1) {
			gl.glUniformMatrix4fv(tdtmOffset, false, CacheBuffer.wrap(params.g_WorldToTopDownTextureMatrix));
			GLCheck.checkError();
		}

		if(timeOffset != -1) {
			gl.glUniform1f(timeOffset, params.g_Time);
			GLCheck.checkError();
		}

		if(baseGerstnerWavelengthOffset != -1) {
			gl.glUniform1f(baseGerstnerWavelengthOffset, params.g_BaseGerstnerWavelength);
			GLCheck.checkError();
		}

		if(baseGerstnerParallelnessOffset != -1) {
			gl.glUniform1f(baseGerstnerParallelnessOffset, params.g_BaseGerstnerParallelness);
			GLCheck.checkError();
		}

		if(baseGerstnerSpeedOffset != -1) {
			gl.glUniform1f(baseGerstnerSpeedOffset, params.g_BaseGerstnerSpeed);
			GLCheck.checkError();
		}

		if(baseGerstnerAmplitudeOffset != -1)
			gl.glUniform1f(baseGerstnerAmplitudeOffset, params.g_BaseGerstnerAmplitude);

		if(gerstnerSteepnessOffset != -1) {
			gl.glUniform1f(gerstnerSteepnessOffset, params.g_GerstnerSteepness);
			GLCheck.checkError();
		}

		if(enableShoreOffset != -1) {
			gl.glUniform1i(enableShoreOffset, params.g_enableShoreEffects);
			GLCheck.checkError();
		}

		if(applyFogOffset != -1) {
			gl.glUniform1i(applyFogOffset, params.g_ApplyFog);
			GLCheck.checkError();
		}

		if(windDirectionOffset != -1) {
			gl.glUniform2f(windDirectionOffset, params.g_WindDirection.x, params.g_WindDirection.y);
			GLCheck.checkError();
		}
	}
	
	private int getUniformIndex(String name){
		int idx = gl.glGetUniformLocation(programId, name);
		if(out_print){
			if(idx >=0){
				System.out.println(debug_name + " contains the uniform: " + name);
			}else{
//				System.out.println(debug_name + " doesn't contain the uniform: " + name);
			}
		}
		
		return idx;
	}
	
	public static void main(String[] args) {
		genSetUniform();
	}
	
	private static void genSetUniform(){
		String[] offsets = {
				"mvpOffset", "lmvpOffset", "mvOffset", "cpOffset", "fcOffset", "cdOffset", "tbrOffset",
				"wbtsOffset", "stfOffset", "dtfOffset", "udLODOffset", "sccOffset", "rcOffset",
				"hscsOffset", "hscpOffset", "lpOffset", "ssiOffset", "znearOffset", "zfarOffset"
			};
		
		String[] names = {
				"g_ModelViewProjectionMatrix", "g_LightModelViewProjectionMatrix", "g_ModelViewMatrix",
				"g_CameraPosition", "g_FrustumCullInHS", "g_CameraDirection", "g_TerrainBeingRendered",
				"g_WaterBumpTexcoordShift", "g_StaticTessFactor","g_DynamicTessFactor",
				"g_UseDynamicLOD", "g_SkipCausticsCalculation","g_RenderCaustics",
				"g_HalfSpaceCullSign","g_HalfSpaceCullPosition","g_LightPosition", "g_ScreenSizeInv",
				"g_ZNear", "g_ZFar"
			};
		
		String pattern = "if(%s != -1)\n" +
				 "\tgl.glUniform1f(%s, params.%s);";
		
		for(int i = 0; i < offsets.length; i++){
			System.out.println(String.format(pattern, offsets[i], offsets[i],names[i]));
		}
	}
	
	private static void genGetUniform(){
		String[] offsets = {
				"mvpOffset", "lmvpOffset", "mvOffset", "cpOffset", "fcOffset", "cdOffset", "tbrOffset",
				"wbtsOffset", "stfOffset", "dtfOffset", "udLODOffset", "sccOffset", "rcOffset",
				"hscsOffset", "hscpOffset", "lpOffset", "ssiOffset", "znearOffset", "zfarOffset"
			};
		
		String[] names = {
				"g_ModelViewProjectionMatrix", "g_LightModelViewProjectionMatrix", "g_ModelViewMatrix",
				"g_CameraPosition", "g_FrustumCullInHS", "g_CameraDirection", "g_TerrainBeingRendered",
				"g_WaterBumpTexcoordShift", "g_StaticTessFactor","g_DynamicTessFactor",
				"g_UseDynamicLOD", "g_SkipCausticsCalculation","g_RenderCaustics",
				"g_HalfSpaceCullSign","g_HalfSpaceCullPosition","g_LightPosition", "g_ScreenSizeInv",
				"g_ZNear", "g_ZFar"
			};
		
		String pattern = "%s = getUniformIndex(\"%s\");";
		
		for(int i = 0; i < offsets.length; i++){
			System.out.println(String.format(pattern, offsets[i], names[i]));
		}
	}
}
