#include "Common.glsl"
#include "LightingCommon.glsl"

#ifndef INVERSE_SQUARED_FALLOFF
#define INVERSE_SQUARED_FALLOFF 1
#endif

out float4 OutScattering;
void main()
{
    OutScattering = float4(0);

    uint3 GridCoordinate = uint3(gl_FragCoord.xy, gl_Layer);
    uint3 GridSizeInt = uint3(VolumetricFog_GridSize);

    // Somehow pixels are being rasterized outside of the viewport on a 970 GTX, perhaps due to use of a geometry shader bypassing the viewport scissor.
    // This triggers the HistoryMissSuperSampleCount path causing significant overhead for shading off-screen pixels.
    if (all(lessThan(GridCoordinate, GridSizeInt)))
    {
        float VolumetricScatteringIntensity = LightData.VolumetricScatteringIntensity;

        float3 L = float3(0);
        float3 ToLight = float3(0);

        uint NumSuperSamples = 1;

#if USE_TEMPORAL_REPROJECTION
        float3 HistoryUV = ComputeVolumeUV(ComputeCellWorldPosition(GridCoordinate, float3(.5f)), UnjitteredPrevWorldToClip);
        float HistoryAlpha = HistoryWeight;

//        FLATTEN
        if (any(lessThan(HistoryUV, float3(0,0,0))) || any(greaterThan(HistoryUV, float3(1,1,1))))
        {
            HistoryAlpha = 0;
        }
        NumSuperSamples = HistoryAlpha < .001f ? HistoryMissSuperSampleCount : 1;
#endif

        for (uint SampleIndex = 0; SampleIndex < NumSuperSamples; SampleIndex++)
        {
            float3 CellOffset = FrameJitterOffsets[SampleIndex].xyz;
            //float CellOffset = .5f;

            float SceneDepth;
            float3 WorldPosition = ComputeCellWorldPosition(GridCoordinate, CellOffset, SceneDepth);
            float3 CameraVector = normalize(WorldPosition - WorldCameraOrigin);

            float CellRadius = length(WorldPosition - ComputeCellWorldPosition(GridCoordinate + uint3(1, 1, 1), CellOffset));
            // Bias the inverse squared light falloff based on voxel size to prevent aliasing near the light source
            float DistanceBias = max(CellRadius * InverseSquaredLightDistanceBiasScale, 1);

            float3 LightColor = LightData.Color;
            float LightMask = GetLocalLightAttenuation(WorldPosition, LightData, ToLight, L);

            float Lighting;
            /*if( LightData.bRectLight )  TODO Can't goto this brach
            {
                FRect Rect = GetRect(ToLight, LightData);

                Lighting = IntegrateLight(Rect, RectTexture);
            }
            else*/
            {
                FCapsuleLight Capsule = GetCapsule(ToLight, LightData);
                Capsule.DistBiasSqr = DistanceBias * DistanceBias;

                Lighting = IntegrateLight(Capsule, LightData.bInverseSquared);
                // TODO here directly make the Lighting to 1 may be get a better result.
//                Lighting = 1;
            }

            float CombinedAttenuation = Lighting * LightMask;
            float ShadowFactor = 1.0f;

            if (CombinedAttenuation > 0)
            {
                ShadowFactor = ComputeVolumeShadowing(WorldPosition, LightData.bRadialLight && !LightData.bSpotLight, LightData.bSpotLight);
            }

            OutScattering.rgb += LightColor * (PhaseFunction(PhaseG, dot(L, -CameraVector)) * CombinedAttenuation * ShadowFactor * VolumetricScatteringIntensity);
            // To debug culling
            //OutScattering.rgb += DeferredLightUniforms.Color * .0000001f;
        }

        OutScattering.rgb /= float(NumSuperSamples);
    }
}