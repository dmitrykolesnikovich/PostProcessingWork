/*=============================================================================
	LightGridCommon.glsl
=============================================================================*/

#define INSTANCED_STEREO 0
#if INSTANCED_STEREO
    #if MATERIALBLENDING_ANY_TRANSLUCENT
    #define ForwardLightDataISR TranslucentBasePass.Shared.ForwardISR
    #else
    #define ForwardLightDataISR OpaqueBasePass.Shared.ForwardISR
    #endif
#endif

struct LightGridData
{
    uint LightGridPixelSizeShift;
    float3 LightGridZParams;
    int3 CulledGridSize;
};

LightGridData GetLightGridData(uint EyeIndex)
{
    LightGridData Result;

    #if INSTANCED_STEREO
    BRANCH
    if (EyeIndex == 0)
    {
        #endif

        Result.LightGridPixelSizeShift = ForwardLightData.LightGridPixelSizeShift;
        Result.LightGridZParams = ForwardLightData.LightGridZParams;
        Result.CulledGridSize = ForwardLightData.CulledGridSize;

        #if INSTANCED_STEREO
    }
    else
    {
        Result.LightGridPixelSizeShift = ForwardLightDataISR.LightGridPixelSizeShift;
        Result.LightGridZParams = ForwardLightDataISR.LightGridZParams;
        Result.CulledGridSize = ForwardLightDataISR.CulledGridSize;
    }
        #endif

    return Result;
}

uint ComputeLightGridCellIndex(uint2 PixelPos, float SceneDepth, uint EyeIndex)
{
    const LightGridData GridData = GetLightGridData(EyeIndex);
    uint ZSlice = uint(max(0, log2(SceneDepth * GridData.LightGridZParams.x + GridData.LightGridZParams.y) * GridData.LightGridZParams.z));
    ZSlice = min(ZSlice, uint(GridData.CulledGridSize.z - 1));
    uint3 GridCoordinate = uint3(PixelPos >> GridData.LightGridPixelSizeShift, ZSlice);
    uint GridIndex = (GridCoordinate.z * GridData.CulledGridSize.y + GridCoordinate.y) * GridData.CulledGridSize.x + GridCoordinate.x;
    return GridIndex;
}

uint ComputeLightGridCellIndex(uint2 PixelPos, float SceneDepth)
{
    return ComputeLightGridCellIndex(PixelPos, SceneDepth, 0);
}

#ifndef NUM_CULLED_LIGHTS_GRID_STRIDE
#define NUM_CULLED_LIGHTS_GRID_STRIDE 0
#endif

#ifndef LOCAL_LIGHT_DATA_STRIDE
#define LOCAL_LIGHT_DATA_STRIDE 5
#endif

uint GetNumLocalLights(uint EyeIndex)
{
    #if INSTANCED_STEREO
    return (EyeIndex == 0) ? ForwardLightData.NumLocalLights : ForwardLightDataISR.NumLocalLights;
    #else
    return ForwardLightData.NumLocalLights;
    #endif
}

struct FCulledLightsGridData
{
    uint NumLocalLights;
    uint DataStartIndex;
};

FCulledLightsGridData GetCulledLightsGrid(uint GridIndex, uint EyeIndex)
{
    FCulledLightsGridData Result;

    #if INSTANCED_STEREO
    BRANCH
    if (EyeIndex == 0)
    {
    #endif

        Result.NumLocalLights = min(ForwardLightData.NumCulledLightsGrid[GridIndex * NUM_CULLED_LIGHTS_GRID_STRIDE + 0], ForwardLightData.NumLocalLights);
        Result.DataStartIndex = ForwardLightData.NumCulledLightsGrid[GridIndex * NUM_CULLED_LIGHTS_GRID_STRIDE + 1];

    #if INSTANCED_STEREO
    }
    else
    {
        Result.NumLocalLights = min(ForwardLightDataISR.NumCulledLightsGrid[GridIndex * NUM_CULLED_LIGHTS_GRID_STRIDE + 0], ForwardLightDataISR.NumLocalLights);
        Result.DataStartIndex = ForwardLightDataISR.NumCulledLightsGrid[GridIndex * NUM_CULLED_LIGHTS_GRID_STRIDE + 1];
    }
    #endif

    return Result;
}

struct FDirectionalLightData
{
    uint HasDirectionalLight;
    uint DirectionalLightShadowMapChannelMask;
    float2 DirectionalLightDistanceFadeMAD;
    float3 DirectionalLightColor;
    float3 DirectionalLightDirection;
};

FDirectionalLightData GetDirectionalLightData(uint EyeIndex)
{
    FDirectionalLightData Result;

    #if INSTANCED_STEREO
    BRANCH
    if (EyeIndex == 0)
    {
        #endif

        Result.HasDirectionalLight = ForwardLightData.HasDirectionalLight;
        Result.DirectionalLightShadowMapChannelMask = ForwardLightData.DirectionalLightShadowMapChannelMask;
        Result.DirectionalLightDistanceFadeMAD = ForwardLightData.DirectionalLightDistanceFadeMAD;
        Result.DirectionalLightColor = ForwardLightData.DirectionalLightColor;
        Result.DirectionalLightDirection = ForwardLightData.DirectionalLightDirection;

        #if INSTANCED_STEREO
    }
    else
    {
        Result.HasDirectionalLight = ForwardLightDataISR.HasDirectionalLight;
        Result.DirectionalLightShadowMapChannelMask = ForwardLightDataISR.DirectionalLightShadowMapChannelMask;
        Result.DirectionalLightDistanceFadeMAD = ForwardLightDataISR.DirectionalLightDistanceFadeMAD;
        Result.DirectionalLightColor = ForwardLightDataISR.DirectionalLightColor;
        Result.DirectionalLightDirection = ForwardLightDataISR.DirectionalLightDirection;
    }
        #endif

    return Result;
}

struct FLocalLightData
{
    float4 LightPositionAndInvRadius;
    float4 LightColorAndFalloffExponent;
    float4 SpotAnglesAndSourceRadiusPacked;
    float4 LightDirectionAndShadowMask;
    float4 LightTangentAndSoftSourceRadius;
};

FLocalLightData GetLocalLightData(uint GridIndex, uint EyeIndex)
{
    FLocalLightData Result;

    #if INSTANCED_STEREO
    BRANCH
    if (EyeIndex == 0)
    {
    #endif

        uint LocalLightIndex = ForwardLightData.CulledLightDataGrid[GridIndex];
        uint LocalLightBaseIndex = LocalLightIndex * LOCAL_LIGHT_DATA_STRIDE;
        Result.LightPositionAndInvRadius = ForwardLightData.ForwardLocalLightBuffer[LocalLightBaseIndex + 0];
        Result.LightColorAndFalloffExponent = ForwardLightData.ForwardLocalLightBuffer[LocalLightBaseIndex + 1];
        Result.LightDirectionAndShadowMask = ForwardLightData.ForwardLocalLightBuffer[LocalLightBaseIndex + 2];
        Result.SpotAnglesAndSourceRadiusPacked = ForwardLightData.ForwardLocalLightBuffer[LocalLightBaseIndex + 3];
        Result.LightTangentAndSoftSourceRadius = ForwardLightData.ForwardLocalLightBuffer[LocalLightBaseIndex + 4];

    #if INSTANCED_STEREO
    }
    else
    {
        uint LocalLightIndex = ForwardLightDataISR.CulledLightDataGrid[GridIndex];
        uint LocalLightBaseIndex = LocalLightIndex * LOCAL_LIGHT_DATA_STRIDE;
        Result.LightPositionAndInvRadius = ForwardLightDataISR.ForwardLocalLightBuffer[LocalLightBaseIndex + 0];
        Result.LightColorAndFalloffExponent = ForwardLightDataISR.ForwardLocalLightBuffer[LocalLightBaseIndex + 1];
        Result.LightDirectionAndShadowMask = ForwardLightDataISR.ForwardLocalLightBuffer[LocalLightBaseIndex + 2];
        Result.SpotAnglesAndSourceRadiusPacked = ForwardLightDataISR.ForwardLocalLightBuffer[LocalLightBaseIndex + 3];
        Result.LightTangentAndSoftSourceRadius = ForwardLightDataISR.ForwardLocalLightBuffer[LocalLightBaseIndex + 4];
    }
    #endif

    return Result;
}