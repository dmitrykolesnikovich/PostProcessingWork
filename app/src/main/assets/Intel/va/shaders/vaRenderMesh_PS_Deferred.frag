
#include "vaShared.glsl"

#include "vaSimpleShadowMap.glsl"

in RenderMeshStandardVertexInput
{
//    float4 Position             : SV_Position;
    float4 Color                ;//: COLOR;
    float4 ViewspacePos         ;//: TEXCOORD0;
    float4 ViewspaceNormal      ;//: NORMAL0;
    float4 ViewspaceTangent     ;//: NORMAL1;
    float4 ViewspaceBitangent   ;//: NORMAL2;
    float4 Texcoord0            ;//: TEXCOORD1;
}_input;

layout(binding = RENDERMESH_TEXTURE_SLOT0) uniform sampler2D g_textureAlbedo;
layout(binding = RENDERMESH_TEXTURE_SLOT1) uniform sampler2D g_textureNormal;
layout(binding = RENDERMESH_TEXTURE_SLOT2) uniform sampler2D g_textureSpecular;

void GetNormalAndTangentSpace( /*const GenericSceneVertexTransformed input,*/ out float3 normal, out float3x3 tangentSpace )
{
#if VA_RMM_HASNORMALMAPTEXTURE
//    float3 normal       = UnpackNormal( g_textureNormal.Sample( g_samplerAnisotropicWrap, input.Texcoord0.xy ) );
    normal          = UnpackNormal(texture(g_textureNormal, _input.Texcoord0.xy).xyz);
#else
    normal          = float3( 0.0, 0.0, 1.0 );
#endif

    tangentSpace    = float3x3(
			                    normalize( _input.ViewspaceTangent.xyz   ),
			                    normalize( _input.ViewspaceBitangent.xyz ),
                                normalize( _input.ViewspaceNormal.xyz    ) );

    normal          = mul( normal, tangentSpace );
    normal          = normalize( normal );
}

LocalMaterialValues GetLocalMaterialValues( /*const in GenericSceneVertexTransformed input, */const bool isFrontFace )
{
    LocalMaterialValues ret;

    ret.IsFrontFace     = isFrontFace;
    ret.Albedo          = _input.Color;

    GetNormalAndTangentSpace( /*input,*/ ret.Normal, ret.TangentSpace );

#if VA_RMM_HASALBEDOTEXTURE
//    ret.Albedo *= g_textureAlbedo.Sample( g_samplerAnisotropicWrap, _input.Texcoord0.xy );
    ret.Albedo *= texture(g_textureAlbedo, _input.Texcoord0.xy);
#endif

    return ret;
}

layout(location = 0) out vec4 Out_Color0;
layout(location = 1) out vec4 Out_Color1;
layout(early_fragment_tests) in;

void main()
{
    bool isFrontFace = gl_FrontFacing;
    LocalMaterialValues lmv = GetLocalMaterialValues(/* input,*/ isFrontFace );

    #if VA_RMM_ALPHATEST
        if( lmv.Albedo.a < 0.5 ) // g_RenderMeshMaterialGlobal.AlphaCutoff
            discard;
    #endif

    GBufferOutput _output = EncodeGBuffer( lmv );

    Out_Color0 = _output.Albedo;
    Out_Color1 = _output.Normal;
}