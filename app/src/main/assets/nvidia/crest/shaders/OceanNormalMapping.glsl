// Crest Ocean System

// This file is subject to the MIT License as seen in the root of this folder structure (LICENSE)

#if _APPLYNORMALMAPPING_ON

uniform float _NormalsStrength;
uniform float _NormalsScale;

float2 SampleNormalMaps(float2 worldXZUndisplaced, float lodAlpha)
{
    const float2 v0 = float2(0.94, 0.34), v1 = float2(-0.85, -0.53);
    const float lodDataGridSize = _GeomData.x;
    float nstretch = _NormalsScale * lodDataGridSize; // normals scaled with geometry
    const float spdmulL = _GeomData.z;
    float2 norm =
    UnpackNormal(tex2D(_Normals, (v0*_CrestTime*spdmulL + worldXZUndisplaced) / nstretch)).xy +
    UnpackNormal(tex2D(_Normals, (v1*_CrestTime*spdmulL + worldXZUndisplaced) / nstretch)).xy;

    // blend in next higher scale of normals to obtain continuity
    const float farNormalsWeight = _InstanceData.y;
    const float nblend = lodAlpha * farNormalsWeight;
    if (nblend > 0.001)
    {
        // next lod level
        nstretch *= 2.;
        const float spdmulH = _GeomData.w;
        norm = lerp(norm,
        UnpackNormal(tex2D(_Normals, (v0*_CrestTime*spdmulH + worldXZUndisplaced) / nstretch)).xy +
        UnpackNormal(tex2D(_Normals, (v1*_CrestTime*spdmulH + worldXZUndisplaced) / nstretch)).xy,
        nblend);
    }

    // approximate combine of normals. would be better if normals applied in local frame.
    return _NormalsStrength * norm;
}

void ApplyNormalMapsWithFlow(float2 worldXZUndisplaced, float2 flow, float lodAlpha, inout float3 io_n)
{
    const float float_period = 1;
    const float period = float_period * 2;
    float sample1_offset = fmod(_CrestTime, period);
    float sample1_weight = sample1_offset / float_period;
    if (sample1_weight > 1.0) sample1_weight = 2.0 - sample1_weight;
    float sample2_offset = fmod(_CrestTime + float_period, period);
    float sample2_weight = 1.0 - sample1_weight;

    // In order to prevent flow from distorting the UVs too much,
    // we fade between two samples of normal maps so that for each
    // sample the UVs can be reset
    float2 io_n_1 = SampleNormalMaps(worldXZUndisplaced - (flow * sample1_offset), lodAlpha);
    float2 io_n_2 = SampleNormalMaps(worldXZUndisplaced - (flow * sample2_offset), lodAlpha);
    io_n.xz += sample1_weight * io_n_1;
    io_n.xz += sample2_weight * io_n_2;
    io_n = normalize(io_n);
}

#endif // _APPLYNORMALMAPPING_ON
