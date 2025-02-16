// Copyright 1998-2019 Epic Games, Inc. All Rights Reserved.

/*=============================================================================
	GlobalDistanceFieldShared.usf
=============================================================================*/
#include "../UE4Common.glsl"

#ifndef IS_MATERIAL_SHADER
#define IS_MATERIAL_SHADER 0
#endif

#if 1 //FEATURE_LEVEL >= FEATURE_LEVEL_SM5
#if IS_MATERIAL_SHADER
#define MaxGlobalDistance				View.MaxGlobalDistance
#else
uniform float MaxGlobalDistance;
#endif

//@todo - get the clipmap branches to compile under gl
#if SM5_PROFILE || PS4_PROFILE || METAL_SM5_PROFILE || METAL_SM5_NOTESS_PROFILE || METAL_MRT_PROFILE || GL4_PROFILE || VULKAN_PROFILE_SM5

// Must match C++
#define MAX_GLOBAL_DF_CLIPMAPS 4

#if IS_MATERIAL_SHADER
    // for materials, these are in the view UB
	#define GlobalDistanceFieldTexture0		View.GlobalDistanceFieldTexture0
	#define GlobalDistanceFieldTexture1		View.GlobalDistanceFieldTexture1
	#define GlobalDistanceFieldTexture2		View.GlobalDistanceFieldTexture2
	#define GlobalDistanceFieldTexture3		View.GlobalDistanceFieldTexture3
	#define GlobalDistanceFieldSampler0		View.GlobalDistanceFieldSampler0
	#define GlobalDistanceFieldSampler1		View.GlobalDistanceFieldSampler1
	#define GlobalDistanceFieldSampler2		View.GlobalDistanceFieldSampler2
	#define GlobalDistanceFieldSampler3		View.GlobalDistanceFieldSampler3
	#define GlobalVolumeCenterAndExtent		View.GlobalVolumeCenterAndExtent
	#define GlobalVolumeWorldToUVAddAndMul	View.GlobalVolumeWorldToUVAddAndMul
	#define GlobalVolumeDimension			View.GlobalVolumeDimension
	#define GlobalVolumeTexelSize			View.GlobalVolumeTexelSize
#else
	// these are only used for the precomputation shaders; which don't have a view UB
	uniform sampler3D GlobalDistanceFieldTexture0;
	uniform sampler3D GlobalDistanceFieldTexture1;
	uniform sampler3D GlobalDistanceFieldTexture2;
	uniform sampler3D GlobalDistanceFieldTexture3;
//	SamplerState GlobalDistanceFieldSampler0;
//	SamplerState GlobalDistanceFieldSampler1;
//	SamplerState GlobalDistanceFieldSampler2;
//	SamplerState GlobalDistanceFieldSampler3;
	uniform float4 GlobalVolumeCenterAndExtent[MAX_GLOBAL_DF_CLIPMAPS];
	uniform float4 GlobalVolumeWorldToUVAddAndMul[MAX_GLOBAL_DF_CLIPMAPS];
	uniform float GlobalVolumeDimension;
	uniform float GlobalVolumeTexelSize;
#endif

/*#if SUPPORTS_INDEPENDENT_SAMPLERS
	#define SharedGlobalDistanceFieldSampler0 GlobalDistanceFieldSampler0
	#define SharedGlobalDistanceFieldSampler1 GlobalDistanceFieldSampler0
	#define SharedGlobalDistanceFieldSampler2 GlobalDistanceFieldSampler0
	#define SharedGlobalDistanceFieldSampler3 GlobalDistanceFieldSampler0
#else
	#define SharedGlobalDistanceFieldSampler0 GlobalDistanceFieldSampler0
	#define SharedGlobalDistanceFieldSampler1 GlobalDistanceFieldSampler1
	#define SharedGlobalDistanceFieldSampler2 GlobalDistanceFieldSampler2
	#define SharedGlobalDistanceFieldSampler3 GlobalDistanceFieldSampler3
#endif*/

float4 SampleGlobalDistanceField(int ClipmapIndex, float3 UV)
{
	if (ClipmapIndex == 0)
	{
		return Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, UV, 0);
	}
	else if (ClipmapIndex == 1)
	{
		return Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, UV, 0);
	}
	else if (ClipmapIndex == 2)
	{
		return Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, UV, 0);
	}
	else
	{
		return Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, UV, 0);
	}
}

float3 ComputeGlobalUV(float3 WorldPosition, uint ClipmapIndex)
{
	//return ((WorldPosition - GlobalVolumeCenterAndExtent[ClipmapIndex].xyz + GlobalVolumeScollOffset[ClipmapIndex].xyz) / (GlobalVolumeCenterAndExtent[ClipmapIndex].w * 2) + .5f);
	float4 WorldToUVAddAndMul = GlobalVolumeWorldToUVAddAndMul[ClipmapIndex];
	return WorldPosition * WorldToUVAddAndMul.www + WorldToUVAddAndMul.xyz;
}

float GetDistanceToNearestSurfaceGlobalClipmap(float3 WorldPosition, uint ClipmapIndex, float OuterClipmapFade)
{
	float3 GlobalUV = ComputeGlobalUV(WorldPosition, ClipmapIndex);
	float DistanceToSurface = 0;
	if (ClipmapIndex == 0)
	{
		DistanceToSurface = Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, GlobalUV, 0).x;
	}
	else if (ClipmapIndex == 1)
	{
		DistanceToSurface = Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, GlobalUV, 0).x;
	}
	else if (ClipmapIndex == 2)
	{
		DistanceToSurface = Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, GlobalUV, 0).x;
	}
	else if (ClipmapIndex == 3)
	{
		DistanceToSurface = Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, GlobalUV, 0).x;
		DistanceToSurface = lerp(MaxGlobalDistance, DistanceToSurface, OuterClipmapFade);
	}
	return DistanceToSurface;
}

float GetDistanceToNearestSurfaceGlobal(float3 WorldPosition)
{
	float DistanceToSurface = MaxGlobalDistance;
	float DistanceFromClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[0].xyz, GlobalVolumeCenterAndExtent[0].www, WorldPosition);

	//@todo - would be nice to atlas to get rid of branches and multiple samplers but the partial update scrolling relies on wrap addressing
//	BRANCH
	if (DistanceFromClipmap > GlobalVolumeCenterAndExtent[0].w * GlobalVolumeTexelSize)
	{
		DistanceToSurface = GetDistanceToNearestSurfaceGlobalClipmap(WorldPosition, 0, 0);
	}
	else
	{
		DistanceFromClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[1].xyz, GlobalVolumeCenterAndExtent[1].www, WorldPosition);

		BRANCH
		if (DistanceFromClipmap > GlobalVolumeCenterAndExtent[1].w * GlobalVolumeTexelSize)
		{
			DistanceToSurface = GetDistanceToNearestSurfaceGlobalClipmap(WorldPosition, 1, 0);
		}
		else
		{
			DistanceFromClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[2].xyz, GlobalVolumeCenterAndExtent[2].www, WorldPosition);
			float DistanceFromLastClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[3].xyz, GlobalVolumeCenterAndExtent[3].www, WorldPosition);

//			BRANCH
			if (DistanceFromClipmap > GlobalVolumeCenterAndExtent[2].w * GlobalVolumeTexelSize)
			{
				DistanceToSurface = GetDistanceToNearestSurfaceGlobalClipmap(WorldPosition, 2, 0);
			}
			else if (DistanceFromLastClipmap > GlobalVolumeCenterAndExtent[3].w * GlobalVolumeTexelSize)
			{
				// Fade to max distance in the last 10% of the largest clipmap
				float OuterClipmapFade = saturate(DistanceFromLastClipmap * 10 * GlobalVolumeWorldToUVAddAndMul[3].w);
				DistanceToSurface = GetDistanceToNearestSurfaceGlobalClipmap(WorldPosition, 3, OuterClipmapFade);
			}
		}
	}

	return DistanceToSurface;
}

float3 GetDistanceFieldGradientGlobalClipmap(float3 WorldPosition, uint ClipmapIndex)
{
	float3 GlobalUV = ComputeGlobalUV(WorldPosition, ClipmapIndex);

	float R = 0;
	float L = 0;
	float F = 0;
	float B = 0;
	float U = 0;
	float D = 0;

	if (ClipmapIndex == 0)
	{
		R = Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, float3(GlobalUV.x + GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		L = Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, float3(GlobalUV.x - GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		F = Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, float3(GlobalUV.x, GlobalUV.y + GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		B = Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, float3(GlobalUV.x, GlobalUV.y - GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		U = Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z + GlobalVolumeTexelSize), 0).x;
		D = Texture3DSampleLevel(GlobalDistanceFieldTexture0, SharedGlobalDistanceFieldSampler0, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z - GlobalVolumeTexelSize), 0).x;
	}
	else if (ClipmapIndex == 1)
	{
		R = Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, float3(GlobalUV.x + GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		L = Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, float3(GlobalUV.x - GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		F = Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, float3(GlobalUV.x, GlobalUV.y + GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		B = Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, float3(GlobalUV.x, GlobalUV.y - GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		U = Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z + GlobalVolumeTexelSize), 0).x;
		D = Texture3DSampleLevel(GlobalDistanceFieldTexture1, SharedGlobalDistanceFieldSampler1, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z - GlobalVolumeTexelSize), 0).x;
	}
	else if (ClipmapIndex == 2)
	{
		R = Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, float3(GlobalUV.x + GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		L = Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, float3(GlobalUV.x - GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		F = Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, float3(GlobalUV.x, GlobalUV.y + GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		B = Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, float3(GlobalUV.x, GlobalUV.y - GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		U = Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z + GlobalVolumeTexelSize), 0).x;
		D = Texture3DSampleLevel(GlobalDistanceFieldTexture2, SharedGlobalDistanceFieldSampler2, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z - GlobalVolumeTexelSize), 0).x;
	}
	else if (ClipmapIndex == 3)
	{
		R = Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, float3(GlobalUV.x + GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		L = Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, float3(GlobalUV.x - GlobalVolumeTexelSize, GlobalUV.y, GlobalUV.z), 0).x;
		F = Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, float3(GlobalUV.x, GlobalUV.y + GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		B = Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, float3(GlobalUV.x, GlobalUV.y - GlobalVolumeTexelSize, GlobalUV.z), 0).x;
		U = Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z + GlobalVolumeTexelSize), 0).x;
		D = Texture3DSampleLevel(GlobalDistanceFieldTexture3, SharedGlobalDistanceFieldSampler3, float3(GlobalUV.x, GlobalUV.y, GlobalUV.z - GlobalVolumeTexelSize), 0).x;
	}

	float Extent = GlobalVolumeCenterAndExtent[ClipmapIndex].w;
	float3 Gradient = .5f * float3(R - L, F - B, U - D) / Extent;
	return Gradient;
}

float3 GetDistanceFieldGradientGlobal(float3 WorldPosition)
{
	float3 Gradient = float3(0, 0, .001f);
	float DistanceFromClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[0].xyz, GlobalVolumeCenterAndExtent[0].www, WorldPosition);
	// Don't sample near the border to avoid sampling out of bounds during partial differencing
	float BorderTexels = GlobalVolumeTexelSize * 4;

//	BRANCH
	if (DistanceFromClipmap > GlobalVolumeCenterAndExtent[0].w * BorderTexels)
	{
		Gradient = GetDistanceFieldGradientGlobalClipmap(WorldPosition, 0);
	}
	else
	{
		DistanceFromClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[1].xyz, GlobalVolumeCenterAndExtent[1].www, WorldPosition);

		BRANCH
		if (DistanceFromClipmap > GlobalVolumeCenterAndExtent[1].w * BorderTexels)
		{
			Gradient = GetDistanceFieldGradientGlobalClipmap(WorldPosition, 1);
		}
		else
		{
			DistanceFromClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[2].xyz, GlobalVolumeCenterAndExtent[2].www, WorldPosition);
			float DistanceFromLastClipmap = ComputeDistanceFromBoxToPointInside(GlobalVolumeCenterAndExtent[3].xyz, GlobalVolumeCenterAndExtent[3].www, WorldPosition);

//			BRANCH
			if (DistanceFromClipmap > GlobalVolumeCenterAndExtent[2].w * BorderTexels)
			{
				Gradient = GetDistanceFieldGradientGlobalClipmap(WorldPosition, 2);
			}
			else if (DistanceFromLastClipmap > GlobalVolumeCenterAndExtent[3].w * BorderTexels)
			{
				Gradient = GetDistanceFieldGradientGlobalClipmap(WorldPosition, 3);
			}
		}
	}

	return Gradient;
}

#else

float GetDistanceToNearestSurfaceGlobal(float3 WorldPosition)
{
	return MaxGlobalDistance;
}

float3 GetDistanceFieldGradientGlobal(float3 WorldPosition)
{
	return float3(0, 0, .001f);
}

#endif
#endif // FEATURE_LEVEL >= FEATURE_LEVEL_SM5