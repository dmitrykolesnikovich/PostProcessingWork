#include "../../../shader_libs/PostProcessingHLSLCompatiable.glsl"
#include "AVSM_Node_defs.h"

layout(location = 0) in float3 In_Pos;
layout(location = 1) in float3 In_Norm;
layout(location = 2) in float3 In_Uv;

layout(binding = 0) uniform cbPerModelValues
{
    float4x4 World /*: WORLD*/;
    float4x4 WorldViewProjection /*: WORLDVIEWPROJECTION*/;
    float4x4 InverseWorld /*: INVERSEWORLD*/;
    float4   LightDirection;
    float4   EyePosition;
    float4x4 LightWorldViewProjection;
};

out PS_INPUT
{
//    float4 Pos      : SV_POSITION;
    float3 Norm     ;// NORMAL;
    float4 LightUv  ;// TEXCOORD1;
    float3 Position ;// TEXCOORD2; // Object space position
}_output;

out gl_PerVertex
{
    vec4 gl_Position;
};

//PS_INPUT VSMain( VS_INPUT input )
void main()
{
    gl_Position     = mul( float4( In_Pos, 1.0f), WorldViewProjection );
    _output.Position = mul( float4( In_Pos, 1.0f), World ).xyz;
    // TODO: transform the light into object space instead of the normal into world space
    _output.Norm = mul( In_Norm, float3x3(World) );
    _output.LightUv   = mul( float4( In_Pos, 1.0f), LightWorldViewProjection );
}