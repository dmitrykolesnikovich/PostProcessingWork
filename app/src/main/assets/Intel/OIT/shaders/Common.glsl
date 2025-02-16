/////////////////////////////////////////////////////////////////////////////////////////////
// Copyright 2017 Intel Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
/////////////////////////////////////////////////////////////////////////////////////////////

#ifndef H_COMMON
#define H_COMMON

#include "../../../shader_libs/PostProcessingHLSLCompatiable.glsl"

//////////////////////////////////////////////
// Defines
//////////////////////////////////////////////

///////////////////////
// Resources
///////////////////////

//////////////////////////////////////////////
// Constants
//////////////////////////////////////////////

struct UIConstants
{
    uint faceNormals;
    uint lightingOnly;
};

layout(binding = 0) uniform/*cbuffer*/ PerFrameConstants
{
    float4x4    mCameraWorldViewProj;
    float4x4    mCameraWorldView;
    float4x4    mCameraViewProj;
    float4x4    mCameraProj;
    float4      mCameraPos;
    float4x4    mLightWorldViewProj;
    float4x4    mCameraViewToWorld;
    float4x4    mCameraViewToLightProj;
    float4x4    mCameraViewToLightView;
    float4      mLightDir;
    float4      mGeometryAlpha;
    float4      mAlphaThreshold;

    UIConstants mUI;
};


// data that we can read or derived from the surface shader outputs
struct SurfaceData
{
    float3 positionView;         // View space position
    float3 positionViewDX;       // Screen space derivatives
    float3 positionViewDY;       // of view space position
    float3 normal;               // View space normal
    float4 albedo;
    float2 lightTexCoord;        // Texture coordinates in light space, [0, 1]
    float2 lightTexCoordDX;      // Screen space partial derivatives
    float2 lightTexCoordDY;      // of light space texture coordinates.
    float  lightSpaceZ;          // Z coordinate (depth) of surface in light space
};


//////////////////////////////////////////////
// Full screen pass
//////////////////////////////////////////////

struct FullScreenTriangleVSOut
{
    float4 positionViewport /*: SV_Position*/;
    float4 positionClip     /*: positionClip*/;
    float2 texCoord         /*: texCoord*/;
};

//////////////////////////////////////////////
// Helper Functions
//////////////////////////////////////////////

float linstep(float min, float max, float v)
{
    return saturate((v - min) / (max - min));
}

float2 ProjectIntoLightTexCoord(float3 positionView)
{
    float4 positionLight = mul(float4(positionView, 1.0f), mCameraViewToLightProj);
    float2 texCoord = (positionLight.xy / positionLight.w) * float2(0.5f) + float2(0.5f);
    return texCoord;
}

#if 1
#define UnpackRGBA(x)  unpackUnorm4x8(x)
#define PackRGBA(x)    packUnorm4x8(x)
#else
float4 UnpackRGBA(uint packedInput)
{
    float4 unpackedOutput;
	uint4 p = uint4((packedInput & 0xFFU),
				    (packedInput >> 8U) & 0xFFU,
				    (packedInput >> 16U) & 0xFFU,
				    (packedInput >> 24U));

	unpackedOutput = float4(p) / 255.0;
	return unpackedOutput;
}

uint PackRGBA(float4 unpackedInput)
{
	uint4 u = uint4(saturate(unpackedInput) * 255.0);
    uint  packedOutput = (u.w << 24U) | (u.z << 16U) | (u.y << 8U) | u.x;
    return packedOutput;
}
#endif

#endif // H_COMMON