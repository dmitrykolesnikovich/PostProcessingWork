#include "ASSAOCommon.frag"

layout(location = 0) out float Out_fColor0; 
layout(location = 1) out float Out_fColor1;

layout (rgba8, binding = 0) uniform image2D g_NormalsOutputUAV;

void main()
{
	float2 inPos = gl_FragCoord.xy;
	int2 baseCoords = int2(inPos.xy) * 2;
    float2 upperLeftUV = (inPos.xy - 0.25) * g_ASSAOConsts.Viewport2xPixelSize;

    int3 baseCoord = int3( int2(inPos.xy) * 2, 0 );
    float z0 = ScreenSpaceToViewSpaceDepth( texelFetchOffset( g_DepthSource, baseCoord.xy, baseCoord.z, int2( 0, 0 ) ).x );
    float z1 = ScreenSpaceToViewSpaceDepth( texelFetchOffset( g_DepthSource, baseCoord.xy, baseCoord.z, int2( 1, 0 ) ).x );
    float z2 = ScreenSpaceToViewSpaceDepth( texelFetchOffset( g_DepthSource, baseCoord.xy, baseCoord.z, int2( 0, 1 ) ).x );
    float z3 = ScreenSpaceToViewSpaceDepth( texelFetchOffset( g_DepthSource, baseCoord.xy, baseCoord.z, int2( 1, 1 ) ).x );

    Out_fColor0 = z0;
    Out_fColor1 = z3;

    float pixZs[4][4];

    // middle 4
    pixZs[1][1] = z0;
    pixZs[2][1] = z1;
    pixZs[1][2] = z2;
    pixZs[2][2] = z3;
    // left 2
    pixZs[0][1] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2( -1, 0 ) ).x ); // g_PointClampSampler
    pixZs[0][2] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2( -1, 1 ) ).x ); 
    // right 2
    pixZs[3][1] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2(  2, 0 ) ).x ); 
    pixZs[3][2] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2(  2, 1 ) ).x ); 
    // top 2
    pixZs[1][0] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2(  0, -1 ) ).x );
    pixZs[2][0] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2(  1, -1 ) ).x );
    // bottom 2
    pixZs[1][3] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2(  0,  2 ) ).x );
    pixZs[2][3] = ScreenSpaceToViewSpaceDepth(  textureLodOffset( g_DepthSource, upperLeftUV, 0.0, int2(  1,  2 ) ).x );

    float4 edges0 = CalculateEdges( pixZs[1][1], pixZs[0][1], pixZs[2][1], pixZs[1][0], pixZs[1][2] );
    float4 edges1 = CalculateEdges( pixZs[2][1], pixZs[1][1], pixZs[3][1], pixZs[2][0], pixZs[2][2] );
    float4 edges2 = CalculateEdges( pixZs[1][2], pixZs[0][2], pixZs[2][2], pixZs[1][1], pixZs[1][3] );
    float4 edges3 = CalculateEdges( pixZs[2][2], pixZs[1][2], pixZs[3][2], pixZs[2][1], pixZs[2][3] );

    float3 pixPos[4][4];

    // there is probably a way to optimize the math below; however no approximation will work, has to be precise.

    // middle 4
    pixPos[1][1] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 0.0,  0.0 ), pixZs[1][1] );
    pixPos[2][1] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 1.0,  0.0 ), pixZs[2][1] );
    pixPos[1][2] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 0.0,  1.0 ), pixZs[1][2] );
    pixPos[2][2] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 1.0,  1.0 ), pixZs[2][2] );
    // left 2
    pixPos[0][1] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( -1.0,  0.0), pixZs[0][1] );
    //pixPos[0][2] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( -1.0,  1.0), pixZs[0][2] );
    // right 2                                                                                     
    //pixPos[3][1] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2(  2.0,  0.0), pixZs[3][1] );
    pixPos[3][2] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2(  2.0,  1.0), pixZs[3][2] );
    // top 2                                                                                       
    pixPos[1][0] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 0.0, -1.0 ), pixZs[1][0] );
    //pixPos[2][0] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 1.0, -1.0 ), pixZs[2][0] );
    // bottom 2                                                                                    
    //pixPos[1][3] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 0.0,  2.0 ), pixZs[1][3] );
    pixPos[2][3] = NDCToViewspace( upperLeftUV + g_ASSAOConsts.ViewportPixelSize * float2( 1.0,  2.0 ), pixZs[2][3] );

    float3 norm0 = CalculateNormal( edges0, pixPos[1][1], pixPos[0][1], pixPos[2][1], pixPos[1][0], pixPos[1][2] );
    float3 norm3 = CalculateNormal( edges3, pixPos[2][2], pixPos[1][2], pixPos[3][2], pixPos[2][1], pixPos[2][3] );

 //   g_NormalsOutputUAV[ baseCoords + int2( 0, 0 ) ] = float4( norm0 * 0.5 + 0.5, 0.0 );
 //   g_NormalsOutputUAV[ baseCoords + int2( 1, 1 ) ] = float4( norm3 * 0.5 + 0.5, 0.0 );
    
    imageStore(g_NormalsOutputUAV, baseCoords + int2( 0, 0 ), float4( norm0 * 0.5 + 0.5, 0.0 ));
    imageStore(g_NormalsOutputUAV, baseCoords + int2( 1, 1 ), float4( norm3 * 0.5 + 0.5, 0.0 ));
}