// Copyright 1998-2019 Epic Games, Inc. All Rights Reserved.

/*=============================================================================
	ShadowFilteringCommon.usf: Contains functions to filter a shadowmap, shared between forward/deferred shading.
=========================================================================*/

//#pragma once
//
//#include "PixelQuadMessagePassing.ush"

#ifndef FEATURE_GATHER4
#define FEATURE_GATHER4 1
#endif

struct FPCFSamplerSettings
{
//    Texture2D		ShadowDepthTexture;
//    SamplerState	ShadowDepthTextureSampler;

//XY - Pixel size of shadowmap
//ZW - Inverse pixel size of shadowmap
    float4			ShadowBufferSize;

// SceneDepth in lightspace.
    float			SceneDepth;

    float			TransitionScale;

// set by the caller, constant for the code so only one code branch should be compiled
    bool			bSubsurface;

// Whether to treat shadow depths near 1 as unshadowed.  This is useful when the shadowmap does not contain all the points being shadowed.
    bool			bTreatMaxDepthUnshadowed;

// only used if bSubsurface is true
    float			DensityMulConstant;

// only used if bSubsurface is true
    float2			ProjectionDepthBiasParameters;
};

// linear PCF, input 3x3
// @param Values0 in row 0 from left to right: x,y,z,w
// @param Values1 in row 1 from left to right: x,y,z,w
// @param Values2 in row 2 from left to right: x,y,z,w
float PCF2x2(float2 Fraction, float3 Values0, float3 Values1, float3 Values2)
{
    float3 Results;

    Results.x = Values0.x * (1.0f - Fraction.x);
    Results.y = Values1.x * (1.0f - Fraction.x);
    Results.z = Values2.x * (1.0f - Fraction.x);
    Results.x += Values0.y;
    Results.y += Values1.y;
    Results.z += Values2.y;
    Results.x += Values0.z * Fraction.x;
    Results.y += Values1.z * Fraction.x;
    Results.z += Values2.z * Fraction.x;

    return saturate(0.25f * dot(Results, half3(1.0f - Fraction.y, 1.0f, Fraction.y)));
}

// linear PCF, input 4x4
// @param Values0 in row 0 from left to right: x,y,z,w
// @param Values1 in row 1 from left to right: x,y,z,w
// @param Values2 in row 2 from left to right: x,y,z,w
// @param Values3 in row 3 from left to right: x,y,z,w
float PCF3x3(float2 Fraction, float4 Values0, float4 Values1, float4 Values2, float4 Values3)
{
    float4 Results;

    Results.x = Values0.x * (1.0f - Fraction.x);
    Results.y = Values1.x * (1.0f - Fraction.x);
    Results.z = Values2.x * (1.0f - Fraction.x);
    Results.w = Values3.x * (1.0f - Fraction.x);
    Results.x += Values0.y;
    Results.y += Values1.y;
    Results.z += Values2.y;
    Results.w += Values3.y;
    Results.x += Values0.z;
    Results.y += Values1.z;
    Results.z += Values2.z;
    Results.w += Values3.z;
    Results.x += Values0.w * Fraction.x;
    Results.y += Values1.w * Fraction.x;
    Results.z += Values2.w * Fraction.x;
    Results.w += Values3.w * Fraction.x;

    return saturate(dot(Results, float4(1.0f - Fraction.y, 1.0f, 1.0f, Fraction.y)) * (1.0f / 9.0f));
}


// linear PCF, input 4x4
// using Gather: xyzw in counter clockwise order starting with the sample to the lower left of the queried location
// @param Values0 left top
// @param Values1 right top
// @param Values2 left bottom
// @param Values3 right bottom
float PCF3x3gather(float2 Fraction, float4 Values0, float4 Values1, float4 Values2, float4 Values3)
{
    float4 Results;

    Results.x = Values0.w * (1.0 - Fraction.x);
    Results.y = Values0.x * (1.0 - Fraction.x);
    Results.z = Values2.w * (1.0 - Fraction.x);
    Results.w = Values2.x * (1.0 - Fraction.x);
    Results.x += Values0.z;
    Results.y += Values0.y;
    Results.z += Values2.z;
    Results.w += Values2.y;
    Results.x += Values1.w;
    Results.y += Values1.x;
    Results.z += Values3.w;
    Results.w += Values3.x;
    Results.x += Values1.z * Fraction.x;
    Results.y += Values1.y * Fraction.x;
    Results.z += Values3.z * Fraction.x;
    Results.w += Values3.y * Fraction.x;

    return dot( Results, float4( 1.0 - Fraction.y, 1.0, 1.0, Fraction.y) * ( 1.0 / 9.0) );
}

// horizontal PCF, input 6x2
float2 HorizontalPCF5x2(float2 Fraction, float4 Values00, float4 Values20, float4 Values40)
{
    float Results0;
    float Results1;

    Results0 = Values00.w * (1.0 - Fraction.x);
    Results1 = Values00.x * (1.0 - Fraction.x);
    Results0 += Values00.z;
    Results1 += Values00.y;
    Results0 += Values20.w;
    Results1 += Values20.x;
    Results0 += Values20.z;
    Results1 += Values20.y;
    Results0 += Values40.w;
    Results1 += Values40.x;
    Results0 += Values40.z * Fraction.x;
    Results1 += Values40.y * Fraction.x;

    return float2(Results0, Results1);
}

// lowest quality ith PCF
float PCF1x1(float2 Fraction, float4 Values00)
{
    float2 HorizontalLerp00 = lerp(Values00.wx, Values00.zy, Fraction.xx);

    return lerp(HorizontalLerp00.x, HorizontalLerp00.y, Fraction.y);
}

float4 CalculateOcclusion(float4 ShadowmapDepth, FPCFSamplerSettings Settings)
{
    if (Settings.bSubsurface)
    {
        // Determine the distance that the light traveled through the subsurface object
        // This assumes that anything between this subsurface pixel and the light was also a subsurface material,
        // As a result, subsurface materials receive leaked light based on how transparent they are
        float4 Thickness = max(Settings.SceneDepth - ShadowmapDepth, float4(0));
        float4 Occlusion = saturate(exp(-Thickness * Settings.DensityMulConstant));

        // Never shadow from depths that were never written to (max depth value)
//        return ShadowmapDepth > .99f ? 1 : Occlusion;
        float4 result;
        for(int i = 0; i < 4; i++)
        {
            result[i]= ShadowmapDepth[i] > .99f ? 1 : Occlusion[i];
        }
        return result;
    }
    else
    {
        // The standard comparison is SceneDepth < ShadowmapDepth
        // Using a soft transition based on depth difference
        // Offsets shadows a bit but reduces self shadowing artifacts considerably
        float TransitionScale = Settings.TransitionScale;

        // Unoptimized Math: saturate((ShadowmapDepth - Settings.SceneDepth) * TransitionScale + 1);
        // Rearranged the math so that per pixel constants can be optimized from per sample constants.
        float ConstantFactor = (Settings.SceneDepth * TransitionScale - 1);
        float4 ShadowFactor = saturate(ShadowmapDepth * TransitionScale - ConstantFactor);
        if (Settings.bTreatMaxDepthUnshadowed)
        {
//            ShadowFactor = saturate(ShadowFactor + (ShadowmapDepth > .99f));
            for(int i = 0; i < 4; i++)
            {
                ShadowFactor[i] = saturate(ShadowFactor[i] + float(ShadowmapDepth[i] > .99f));
            }
        }
        return ShadowFactor;
    }

}

float3 CalculateOcclusion(float3 ShadowmapDepth, FPCFSamplerSettings Settings)
{
    if (Settings.bSubsurface)
    {
        // Determine the distance that the light traveled through the subsurface object
        // This assumes that anything between this subsurface pixel and the light was also a subsurface material,
        // As a result, subsurface materials receive leaked light based on how transparent they are
        float3 Thickness = max(Settings.SceneDepth - (ShadowmapDepth - Settings.ProjectionDepthBiasParameters.x), 0);
        float3 Occlusion = saturate(exp(-Thickness * Settings.DensityMulConstant));
        // Never shadow from depths that were never written to (max depth value)
//        return ShadowmapDepth > .99f ? 1 : Occlusion;  todo
        float3 result;
        result.x = ShadowmapDepth.x > .99 ? 1 : Occlusion.x;
        result.y = ShadowmapDepth.y > .99 ? 1 : Occlusion.y;
        result.z = ShadowmapDepth.z > .99 ? 1 : Occlusion.z;
        return result;
    }
    else
    {
        // The standard comparison is Settings.SceneDepth < ShadowmapDepth
        // Using a soft transition based on depth difference
        // Offsets shadows a bit but reduces self shadowing artifacts considerably
        float TransitionScale = Settings.TransitionScale;

        // Unoptimized Math: saturate((ShadowmapDepth - Settings.SceneDepth) * TransitionScale + 1);
        // Rearranged the math so that per pixel constants can be optimized from per sample constants.
        float ConstantFactor = (Settings.SceneDepth * TransitionScale - 1);
        float3 ShadowFactor = saturate(ShadowmapDepth * TransitionScale - ConstantFactor);

        if (Settings.bTreatMaxDepthUnshadowed)
        {
//            ShadowFactor = saturate(ShadowFactor + (ShadowmapDepth > .99f));  todo
            ShadowFactor.x = saturate(ShadowFactor.x + float(ShadowmapDepth.x > .99f));
            ShadowFactor.y = saturate(ShadowFactor.y + float(ShadowmapDepth.y > .99f));
            ShadowFactor.z = saturate(ShadowFactor.z + float(ShadowmapDepth.z > .99f));
        }

        return ShadowFactor;
    }
}

float4 Texture2DSampleLevel(sampler2D tex, vec2 uv, float lod)
{
    return textureLod(tex, uv, lod);
}

void FetchRowOfThree(float2 Sample00TexelCenter, float VerticalOffset, out float3 Values0, FPCFSamplerSettings Settings)
{
    Values0.x = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(0, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values0.y = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(1, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values0.z = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(2, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values0 = CalculateOcclusion(Values0, Settings);
}

void FetchRowOfFour(float2 Sample00TexelCenter, float VerticalOffset, out float4 Values0, FPCFSamplerSettings Settings)
{
    Values0.x = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(0, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values0.y = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(1, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values0.z = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(2, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values0.w = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(3, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values0 = CalculateOcclusion(Values0, Settings);
}

void FetchRowOfThreeAfterFour(float2 Sample00TexelCenter, float VerticalOffset, out float3 Values1, FPCFSamplerSettings Settings)
{
    Values1.x = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(4, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values1.y = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(5, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values1.z = Texture2DSampleLevel(ShadowDepthTexture, (Sample00TexelCenter + float2(6, VerticalOffset)) * Settings.ShadowBufferSize.zw, 0).r;
    Values1 = CalculateOcclusion(Values1, Settings);
}

// break this out for forward rendering as it's not part of ManualPCF's set of shadowquality settings.
float Manual2x2PCF(float2 ShadowPosition, FPCFSamplerSettings Settings)
{
    float2 TexelPos = ShadowPosition * Settings.ShadowBufferSize.xy;
    float2 Fraction = frac(TexelPos);
    float2 TexelCenter = floor(TexelPos) + 0.5f;	// bias to get reliable texel center content

    float2 Sample00TexelCenter = TexelCenter - float2(1, 1);

    float3 SamplesValues0, SamplesValues1, SamplesValues2;

    FetchRowOfThree(Sample00TexelCenter, 0, SamplesValues0, Settings);
    FetchRowOfThree(Sample00TexelCenter, 1, SamplesValues1, Settings);
    FetchRowOfThree(Sample00TexelCenter, 2, SamplesValues2, Settings);

    return PCF2x2(Fraction, SamplesValues0, SamplesValues1, SamplesValues2);
}

float ManualNoFiltering(float2 ShadowPosition, FPCFSamplerSettings Settings)
{
    // very low quality but very good performance, useful to profile, 1 sample, not using gather4
    return CalculateOcclusion(Texture2DSampleLevel(ShadowDepthTexture, ShadowPosition, 0).rrr, Settings).r;
}

float Manual1x1PCF(float2 ShadowPosition, FPCFSamplerSettings Settings)
{
    float2 TexelPos = ShadowPosition * Settings.ShadowBufferSize.xy - 0.5f;	// bias to be consistent with texture filtering hardware
    float2 Fraction = frac(TexelPos);
    float2 TexelCenter = floor(TexelPos) + 0.5f;	// bias to get reliable texel center content

    // using Gather: xyzw in counter clockwise order starting with the sample to the lower left of the queried location
    float4 Samples;

#if FEATURE_GATHER4
//    Samples = textureGatherOffset(ShadowDepthTexture, TexelCenter * Settings.ShadowBufferSize.zw);
    Samples = textureGather(ShadowDepthTexture, TexelCenter * Settings.ShadowBufferSize.zw);
#else
    Samples.x = Texture2DSampleLevel(ShadowDepthTexture, (TexelCenter.xy + float2(0, 1)) * Settings.ShadowBufferSize.zw, 0).r;
    Samples.y = Texture2DSampleLevel(ShadowDepthTexture, (TexelCenter.xy + float2(1, 1)) * Settings.ShadowBufferSize.zw, 0).r;
    Samples.z = Texture2DSampleLevel(ShadowDepthTexture, (TexelCenter.xy + float2(1, 0)) * Settings.ShadowBufferSize.zw, 0).r;
    Samples.w = Texture2DSampleLevel(ShadowDepthTexture, (TexelCenter.xy + float2(0, 0)) * Settings.ShadowBufferSize.zw, 0).r;
#endif

    float4 Values00 = CalculateOcclusion(Samples, Settings);
    return PCF1x1(Fraction, Values00);
}


float ManualPCF(float2 ShadowPosition, FPCFSamplerSettings Settings)
{

#if SHADOW_QUALITY == 1
    return ManualNoFiltering(ShadowPosition, Settings);
#endif  // End SHADOW_QUALITY==1

#if SHADOW_QUALITY == 2
    // low quality, 2x2 samples, using and not using gather4
    return Manual1x1PCF(ShadowPosition, Settings);
#endif  // End SHADOW_QUALITY==2

#if SHADOW_QUALITY == 3
    // medium quality, 4x4 samples, using and not using gather4
    {
        float2 TexelPos = ShadowPosition * Settings.ShadowBufferSize.xy - 0.5f;	// bias to be consistent with texture filtering hardware
        float2 Fraction = frac(TexelPos);
        float2 TexelCenter = floor(TexelPos) + 0.5f;	// bias to get reliable texel center content
        {
            float2 Sample00TexelCenter = TexelCenter - float2(1, 1);

            float4 SampleValues0, SampleValues1, SampleValues2, SampleValues3;

            #if FEATURE_GATHER4
            float2 SamplePos = TexelCenter * Settings.ShadowBufferSize.zw;	// bias to get reliable texel center content
            SampleValues0 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(-1, -1)), Settings);
            SampleValues1 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(1, -1)), Settings);
            SampleValues2 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(-1, 1)), Settings);
            SampleValues3 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(1, 1)), Settings);
            return PCF3x3gather(Fraction, SampleValues0, SampleValues1, SampleValues2, SampleValues3);
            #else // FEATURE_GATHER4
            FetchRowOfFour(Sample00TexelCenter, 0, SampleValues0, Settings);
            FetchRowOfFour(Sample00TexelCenter, 1, SampleValues1, Settings);
            FetchRowOfFour(Sample00TexelCenter, 2, SampleValues2, Settings);
            FetchRowOfFour(Sample00TexelCenter, 3, SampleValues3, Settings);
            return PCF3x3(Fraction, SampleValues0, SampleValues1, SampleValues2, SampleValues3);
            #endif // FEATURE_GATHER4
        }
    }
#endif   // End SHADOW_QUALITY==3

#if FEATURE_GATHER4
    // high quality, 6x6 samples, using gather4
    {
        float2 TexelPos = ShadowPosition * Settings.ShadowBufferSize.xy - 0.5f;	// bias to be consistent with texture filtering hardware
        float2 Fraction = frac(TexelPos);
        float2 TexelCenter = floor(TexelPos);
        float2 SamplePos = (TexelCenter + 0.5f) * Settings.ShadowBufferSize.zw;	// bias to get reliable texel center content

        float Results;

        float4 Values00 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(-2, -2)), Settings);
        float4 Values20 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(0, -2)), Settings);
        float4 Values40 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(2, -2)), Settings);

        float2 Row0 = HorizontalPCF5x2(Fraction, Values00, Values20, Values40);
        Results = Row0.x * (1.0f - Fraction.y) + Row0.y;

        float4 Values02 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(-2, 0)), Settings);
        float4 Values22 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(0, 0)), Settings);
        float4 Values42 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(2, 0)), Settings);

        float2 Row1 = HorizontalPCF5x2(Fraction, Values02, Values22, Values42);
        Results += Row1.x + Row1.y;

        float4 Values04 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(-2, 2)), Settings);
        float4 Values24 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(0, 2)), Settings);
        float4 Values44 = CalculateOcclusion(textureGatherOffset(ShadowDepthTexture, SamplePos, int2(2, 2)), Settings);

        float2 Row2 = HorizontalPCF5x2(Fraction, Values04, Values24, Values44);
        Results += Row2.x + Row2.y * Fraction.y;

        return 0.04f * Results;
    }

#else // FEATURE_GATHER4

    // high quality, 7x7 samples, not using gather4 (todo: ideally we make this 6x6 to get same results with gather code)
    {
        float2 Fraction = frac(ShadowPosition * Settings.ShadowBufferSize.xy);
        float2 Sample00TexelCenter = floor(ShadowPosition * Settings.ShadowBufferSize.xy) - float2(3, 3);

        // Fetch 7x7 shadowmap point samples
        // Do 6x6 PCF samples, sharing the point samples between neighboring PCF samples
        float4 Results;

        float4 SampleValues03;
        float4 SampleValues13;

        {
            float4 SampleValues10;
            float4 SampleValues11;
            float4 SampleValues12;

            // Group work to minimize temporary registers and to split texture work with PCF ALU operations to hide texture latency
            // Without this layout (all texture lookups at the beginning, PCF ALU's at the end) this shader was 4x slower on Nvidia cards
            {
                float4 SampleValues00;
                FetchRowOfFour(Sample00TexelCenter, 0, SampleValues00, Settings);
                SampleValues10.x = SampleValues00.w;

                float4 SampleValues01;
                FetchRowOfFour(Sample00TexelCenter, 1, SampleValues01, Settings);
                SampleValues11.x = SampleValues01.w;

                float4 SampleValues02;
                FetchRowOfFour(Sample00TexelCenter, 2, SampleValues02, Settings);
                SampleValues12.x = SampleValues02.w;

                FetchRowOfFour(Sample00TexelCenter, 3, SampleValues03, Settings);
                SampleValues13.x = SampleValues03.w;
                Results.x = PCF3x3(Fraction, SampleValues00, SampleValues01, SampleValues02, SampleValues03);
            }

            {
                FetchRowOfThreeAfterFour(Sample00TexelCenter, 0, SampleValues10.yzw, Settings);
                FetchRowOfThreeAfterFour(Sample00TexelCenter, 1, SampleValues11.yzw, Settings);
                FetchRowOfThreeAfterFour(Sample00TexelCenter, 2, SampleValues12.yzw, Settings);
                FetchRowOfThreeAfterFour(Sample00TexelCenter, 3, SampleValues13.yzw, Settings);
                Results.y = PCF3x3(Fraction, SampleValues10, SampleValues11, SampleValues12, SampleValues13);
            }
        }

        {
            float4 SampleValues14;
            float4 SampleValues15;
            float4 SampleValues16;

            {
                float4 SampleValues04;
                FetchRowOfFour(Sample00TexelCenter, 4, SampleValues04, Settings);
                SampleValues14.x = SampleValues04.w;

                float4 SampleValues05;
                FetchRowOfFour(Sample00TexelCenter, 5, SampleValues05, Settings);
                SampleValues15.x = SampleValues05.w;

                float4 SampleValues06;
                FetchRowOfFour(Sample00TexelCenter, 6, SampleValues06, Settings);
                SampleValues16.x = SampleValues06.w;

                Results.z = PCF3x3(Fraction, SampleValues03, SampleValues04, SampleValues05, SampleValues06);
            }

            {
                FetchRowOfThreeAfterFour(Sample00TexelCenter, 4, SampleValues14.yzw, Settings);
                FetchRowOfThreeAfterFour(Sample00TexelCenter, 5, SampleValues15.yzw, Settings);
                FetchRowOfThreeAfterFour(Sample00TexelCenter, 6, SampleValues16.yzw, Settings);
                Results.w = PCF3x3(Fraction, SampleValues13, SampleValues14, SampleValues15, SampleValues16);
            }
        }

        return dot(Results, .25f);
    }
#endif	// FEATURE_GATHER4
}