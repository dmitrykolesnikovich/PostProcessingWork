// Copyright 1998-2019 Epic Games, Inc. All Rights Reserved.

/*=============================================================================
	AtmosphereCommon.usf: Functions and variables shared by both rendering and precomputation

	This code contains embedded portions of free sample source code from
	http://www-evasion.imag.fr/Membres/Eric.Bruneton/PrecomputedAtmosphericScattering2.zip, Author: Eric Bruneton,
	08/16/2011, Copyright (c) 2008 INRIA, All Rights Reserved, which have been altered from their original version.

	Permission is granted to anyone to use this software for any purpose, including commercial applications, and to alter it and redistribute it freely, subject to the following restrictions:

    1. Redistributions of source code must retain the above copyright notice,
	   this list of conditions and the following disclaimer.
    2. Redistributions in binary form must reproduce the above copyright notice,
	   this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.
    3. Neither the name of the copyright holders nor the names of its
       contributors may be used to endorse or promote products derived from
       this software without specific prior written permission.

=============================================================================*/
#include "../../shader_libs/PostProcessingHLSLCompatiable.glsl"

#ifndef __ATMOSPHERE_COMMON__
#define __ATMOSPHERE_COMMON__

#ifndef COMPILER_METAL
#define COMPILER_METAL 0
#endif

#ifndef MAX_SHADER_LANGUAGE_VERSION
#define MAX_SHADER_LANGUAGE_VERSION 5
#endif

#ifndef IS_MATERIAL_SHADER
#define IS_MATERIAL_SHADER 0
#endif

#if COMPILER_METAL && MAX_SHADER_LANGUAGE_VERSION <= 3
// For inexplicable reasons until we can debug shaders and *really* see what's what we need to abs sqrt input for the functions in this shader file only.
// As best as the vendors can tell us Metal has *slightly* different precision in some results which means we are giving sqrt negative & zero values.
// This should now only be true on Metal 1.2 and earlier.
#define sqrt(...) sqrt(abs(__VA_ARGS__))
#endif

const float AverageGroundRelectance = 0.1f; // Simple approximation ground reflection light

/** Magic numbers are based on paper (http://hal.inria.fr/docs/00/28/87/58/PDF/article.pdf) */
/** Rayleigh scattering terms */
const float3 BetaRayleighScattering = float3(5.8e-3f, 1.35e-2f, 3.31e-2f); // Equation 1, REK 04

/** Mie scattering terms */
const float HeightScaleMie = 1.2f; // 1.2 km, for Mie scattering
const float3 BetaMieScattering = float3(4e-3f, 4e-3f, 4e-3f); // Equation 4
const float BetaRatio = 0.9f; // Figure 6, BetaMScattering/BetaMExtinction = 0.9
const float3 BetaMieExtinction = BetaMieScattering / BetaRatio.rrr;
const float MieG = 0.8f; // Equation 4

const float RadiusGround = 6360;
const float RadiusAtmosphere = 6430;
const float RadiusLimit = 6431;

const int IrradianceTexWidth = 64;
const int IrradianceTexHeight = 16;

const int InscatterMuNum = 128;
const int InscatterMuSNum = 32;
const int InscatterNuNum = 8;

#define TRANSMITTANCE_NON_LINEAR			1
#define INSCATTER_NON_LINEAR				1
#define ATMOSPHERIC_TEXTURE_SAMPLE_FIX		1

#ifndef ATMOSPHERIC_NO_SUN_DISK
#define	ATMOSPHERIC_NO_SUN_DISK				0
#endif

#ifndef ATMOSPHERIC_NO_GROUND_SCATTERING
#define	ATMOSPHERIC_NO_GROUND_SCATTERING	0
#endif

#ifndef ATMOSPHERIC_NO_DECAY
#define	ATMOSPHERIC_NO_DECAY				0
#endif

#ifndef RENDERFLAG_DISABLE_SUN_DISK
#define RENDERFLAG_DISABLE_SUN_DISK			1		// E_DisableSunDisk = 1,
#endif

#ifndef RENDERFLAG_DISABLE_GROUND_SCATTERING
#define RENDERFLAG_DISABLE_GROUND_SCATTERING	2	// E_DisableGroundScattering = 2,
#endif

/** Textures for rendering */
#if IS_MATERIAL_SHADER
    // for materials, these are in the view UB
	#define AtmosphereTransmittanceTexture			View.AtmosphereTransmittanceTexture
	#define AtmosphereTransmittanceTextureSampler	View.AtmosphereTransmittanceTextureSampler
	#define AtmosphereIrradianceTexture				View.AtmosphereIrradianceTexture
	#define AtmosphereIrradianceTextureSampler		View.AtmosphereIrradianceTextureSampler
	#define AtmosphereInscatterTexture				View.AtmosphereInscatterTexture
	#define AtmosphereInscatterTextureSampler		View.AtmosphereInscatterTextureSampler
#else
	// these are only used for the precomputation shader; which doesn't have a view UB
	layout(binding = 0) uniform sampler2D AtmosphereTransmittanceTexture;
//	SamplerState AtmosphereTransmittanceTextureSampler;
	layout(binding = 1) uniform sampler2D AtmosphereIrradianceTexture;
//	SamplerState AtmosphereIrradianceTextureSampler;
	layout(binding = 2) uniform sampler3D AtmosphereInscatterTexture;
//	SamplerState AtmosphereInscatterTextureSampler;
	#define ATMOSPHERIC_TEXTURE_PREFIX
#endif

#if 0
struct FAtmosphereVSOutput
{
	float2 OutTexCoord : TEXCOORD0;
    float4 OutPosition : SV_POSITION;
#if USING_VERTEX_SHADER_LAYER
    uint LayerIndex : SV_RenderTargetArrayIndex;
#else
    uint LayerIndex : TEXCOORD1;
#endif
};
#endif


float2 GetTransmittanceUV(float Radius, float Mu)
{
    float U, V;
#if TRANSMITTANCE_NON_LINEAR
	V = sqrt((Radius - RadiusGround) / (RadiusAtmosphere - RadiusGround));
	U = atan((Mu + 0.15) / (1.0 + 0.15) * tan(1.5)) / 1.5;
#else
	V = (Radius - RadiusGround) / (RadiusAtmosphere - RadiusGround);
	U = (Mu + 0.15) / (1.0 + 0.15);
#endif
    return float2(U, V);
}

void GetTransmittanceRMuS(float2 UV, out float Radius, out float MuS)
{
    Radius = UV.y;
    MuS = UV.x;
#if TRANSMITTANCE_NON_LINEAR
    Radius = RadiusGround + (Radius * Radius) * (RadiusAtmosphere - RadiusGround);
    MuS = -0.15 + tan(1.5 * MuS) / tan(1.5) * (1.0 + 0.15);
#else
    r = RadiusGround + r * (RadiusAtmosphere - RadiusGround);
    muS = -0.15 + muS * (1.0 + 0.15);
#endif
}

float2 GetIrradianceUV(float Radius, float MuS)
{
    float V = (Radius - RadiusGround) / (RadiusAtmosphere - RadiusGround);
    float U = (MuS + 0.2) / (1.0 + 0.2);
    return float2(U, V);
}

void GetIrradianceRMuS(float2 UV, out float Radius, out float MuS)
{
    Radius = RadiusGround + (UV.y * float(IrradianceTexHeight) - 0.5) / (float(IrradianceTexHeight) - 1.0) * (RadiusAtmosphere - RadiusGround);
    MuS = -0.2 + (UV.x * float(IrradianceTexWidth) - 0.5) / (float(IrradianceTexWidth) - 1.0) * (1.0 + 0.2);
}

float4 Texture4DSample(Texture3D Texture, SamplerState TextureSampler, float Radius, float Mu, float MuS, float Nu)
{
    float H = sqrt(RadiusAtmosphere * RadiusAtmosphere - RadiusGround * RadiusGround);
    float Rho = sqrt(Radius * Radius - RadiusGround * RadiusGround);
#if INSCATTER_NON_LINEAR
    float RMu = Radius * Mu;
    float Delta = RMu * RMu - Radius * Radius + RadiusGround * RadiusGround;
    float4 TexOffset = RMu < 0.0 && Delta > 0.0 ? float4(1.0, 0.0, 0.0, 0.5 - 0.5 / float(InscatterMuNum)) : float4(-1.0, H * H, H, 0.5 + 0.5 / float(InscatterMuNum));
	float MuR = 0.5 / float(View.AtmosphericFogInscatterAltitudeSampleNum) + Rho / H * (1.0 - 1.0 / float(View.AtmosphericFogInscatterAltitudeSampleNum));
    float MuMu = TexOffset.w + (RMu * TexOffset.x + sqrt(Delta + TexOffset.y)) / (Rho + TexOffset.z) * (0.5 - 1.0 / float(InscatterMuNum));
    // paper formula
    //float MuMuS = 0.5 / float(InscatterMuSNum) + max((1.0 - exp(-3.0 * MuS - 0.6)) / (1.0 - exp(-3.6)), 0.0) * (1.0 - 1.0 / float(InscatterMuSNum));
    // better formula
    float MuMuS = 0.5 / float(InscatterMuSNum) + (atan(max(MuS, -0.1975) * tan(1.26 * 1.1)) / 1.1 + (1.0 - 0.26)) * 0.5 * (1.0 - 1.0 / float(InscatterMuSNum));
#else
	float MuR = 0.5 / float(View.AtmosphericFogInscatterAltitudeSampleNum) + Rho / H * (1.0 - 1.0 / float(View.AtmosphericFogInscatterAltitudeSampleNum));
    float MuMu = 0.5 / float(InscatterMuNum) + (Mu + 1.0) * 0.5f * (1.0 - 1.0 / float(InscatterMuNum));
    float MuMuS = 0.5 / float(InscatterMuSNum) + max(MuS + 0.2, 0.0) / 1.2 * (1.0 - 1.0 / float(InscatterMuSNum));
#endif
    float LerpValue = (Nu + 1.0) * 0.5f * (float(InscatterNuNum) - 1.0);
    float MuNu = floor(LerpValue);
    LerpValue = LerpValue - MuNu;

    return Texture3DSampleLevel(Texture, TextureSampler, float3((MuNu + MuMuS) / float(InscatterNuNum), MuMu, MuR), 0) * (1.0 - LerpValue) +
           Texture3DSampleLevel(Texture, TextureSampler, float3((MuNu + MuMuS + 1.0) / float(InscatterNuNum), MuMu, MuR), 0) * LerpValue;
}

float Mod(float X, float Y)
{
	return X - Y * floor(X/Y);
}

void GetMuMuSNu(float2 UV, float Radius, float4 DhdH, out float Mu, out float MuS, out float Nu)
{
    float X = UV.x * float(InscatterMuSNum * InscatterNuNum) - 0.5;
    float Y = UV.y * float(InscatterMuNum) - 0.5;
#if INSCATTER_NON_LINEAR
    if (Y < float(InscatterMuNum) * 0.5f)
	{
        float D = 1.0 - Y / (float(InscatterMuNum) * 0.5f - 1.0);
        D = min(max(DhdH.z, D * DhdH.w), DhdH.w * 0.999);
        Mu = (RadiusGround * RadiusGround - Radius * Radius - D * D) / (2.0 * Radius * D);
        Mu = min(Mu, -sqrt(1.0 - (RadiusGround / Radius) * (RadiusGround / Radius)) - 0.001);
    }
	else
	{
        float D = (Y - float(InscatterMuNum) * 0.5f) / (float(InscatterMuNum) * 0.5f - 1.0);
        D = min(max(DhdH.x, D * DhdH.y), DhdH.y * 0.999);
        Mu = (RadiusAtmosphere * RadiusAtmosphere - Radius * Radius - D * D) / (2.0 * Radius * D);
    }
    MuS = Mod(X, float(InscatterMuSNum)) / (float(InscatterMuSNum) - 1.0);
    // paper formula
    //MuS = -(0.6 + log(1.0 - MuS * (1.0 -  exp(-3.6)))) / 3.0;
    // better formula
    MuS = tan((2.0 * MuS - 1.0 + 0.26) * 1.1) / tan(1.26 * 1.1);
    Nu = -1.0 + floor(X / float(InscatterMuSNum)) / (float(InscatterNuNum) - 1.0) * 2.0;
#else
    Mu = -1.0 + 2.0 * Y / (float(InscatterMuNum) - 1.0);
    MuS = Mod(X, float(InscatterMuSNum)) / (float(InscatterMuSNum) - 1.0);
    MuS = -0.2 + MuS * 1.2;
    Nu = -1.0 + floor(X / float(InscatterMuSNum)) / (float(InscatterNuNum) - 1.0) * 2.0;
#endif
}

/**
 * Nearest intersection of ray r,mu with ground or top atmosphere boundary
 * mu=cos(ray zenith angle at ray origin)
 */
float Limit(float Radius, float Mu)
{
    float Dout = -Radius * Mu + sqrt(Radius * Radius * (Mu * Mu - 1.0) + RadiusLimit * RadiusLimit);
    float Delta2 = Radius * Radius * (Mu * Mu - 1.0) + RadiusGround * RadiusGround;
    if (Delta2 >= 0.0)
	{
        float Din = -Radius * Mu - sqrt(Delta2);
        if (Din >= 0.0)
		{
            Dout = min(Dout, Din);
        }
    }
    return Dout;
}

/**
 * Transmittance(=transparency) of atmosphere for infinite ray (r,mu)
 * (mu=cos(view zenith angle)), intersections with ground ignored
 */
float3 Transmittance(float Radius, float Mu)
{
	float2 UV = GetTransmittanceUV(Radius, Mu);
	return Texture2DSampleLevel(AtmosphereTransmittanceTexture, AtmosphereTransmittanceTextureSampler, UV, 0).rgb;
}

/**
 * Transmittance(=transparency) of atmosphere for infinite ray (r,mu)
 * (mu=cos(view zenith angle)), or zero if ray intersects ground
 */
float3 TransmittanceWithShadow(float Radius, float Mu)
{
	// Need to correct calculation based on shadow feature, currently don't consider
    //return Mu < -sqrt(1.0 - (RadiusGround / Radius) * (RadiusGround / Radius)) ? float3(0.f, 0.f, 0.f) : Transmittance(Radius, Mu);
	return Transmittance(Radius, Mu);
}

/**
 * Transmittance(=transparency) of atmosphere between x and x0
 * Assume segment x,x0 not intersecting ground
 * D = Distance between x and x0, mu=cos(zenith angle of [x,x0) ray at x)
 */
float3 TransmittanceWithDistance(float Radius, float Mu, float D)
{
    float3 Result;
    float R1 = sqrt(Radius * Radius + D * D + 2.0 * Radius * Mu * D);
    float Mu1 = (Radius * Mu + D) / R1;
    if (Mu > 0.0)
	{
        Result = min(Transmittance(Radius, Mu) / Transmittance(R1, Mu1), 1.0);
    }
	else
	{
        Result = min(Transmittance(R1, -Mu1) / Transmittance(Radius, -Mu), 1.0);
    }
    return Result;
}

/**
 * Transmittance(=transparency) of atmosphere between x and x0
 * Assume segment x,x0 not intersecting ground
 * Radius=||x||, Mu=cos(zenith angle of [x,x0) ray at x), v=unit direction vector of [x,x0) ray
 */
float3 TransmittanceWithDistance(float Radius, float Mu, float3 V, float3 X0)
{
    float3 Result;
    float R1 = length(X0);
    float Mu1 = dot(X0, V) / Radius;
    if (Mu > 0.0)
	{
        Result = min(Transmittance(Radius, Mu) / Transmittance(R1, Mu1), 1.0);
    }
	else
	{
        Result = min(Transmittance(R1, -Mu1) / Transmittance(Radius, -Mu), 1.0);
    }
    return Result;
}

/**
 * Optical depth for ray (r,mu) of length d, using analytic formula
 * (mu=cos(view zenith angle)), intersections with ground ignored
 * H=height scale of exponential density function
 */
float OpticalDepthWithDistance(float H, float Radius, float Mu, float D)
{
	float ParticleDensity = 6.2831; // REK 04, Table 2
    float A = sqrt((0.5/H)*Radius);
    float2 A01 = A * float2(Mu, Mu + D / Radius);
    float2 A01Sign = sign(A01);
    float2 A01Squared = A01*A01;
    float X = A01Sign.y > A01Sign.x ? exp(A01Squared.x) : 0.0;
    float2 Y = A01Sign / (2.3193*abs(A01) + sqrt(1.52*A01Squared + 4.0)) * float2(1.0, exp(-D/H*(D/(2.0*Radius)+Mu)));
    return sqrt((ParticleDensity*H)*Radius) * exp((RadiusGround-Radius)/H) * (X + dot(Y, float2(1.0, -1.0)));
}

float3 Irradiance(Texture2D Texture, SamplerState TextureSampler, float r, float muS)
{
    float2 UV = GetIrradianceUV(r, muS);
	return Texture2DSampleLevel(Texture, TextureSampler, UV, 0).rgb;
}

/** Rayleigh phase function */
float PhaseFunctionR(float Mu)
{
    return (3.0 / (16.0 * PI)) * (1.0 + Mu * Mu);
}

/** Mie phase function */
float PhaseFunctionM(float Mu)
{
	return 1.5 * 1.0 / (4.0 * PI) * (1.0 - MieG * MieG) * pow( abs(1.0 + (MieG * MieG) - 2.0 * MieG * Mu), -3.0/2.0) * (1.0 + Mu * Mu) / (2.0 + MieG * MieG);
}

/** Approximated single Mie scattering (cf. approximate Cm in paragraph "Angular precision") */
float3 GetMie(float4 RayMie)
{
	// RayMie.rgb=C*, RayMie.w=Cm,r
	return RayMie.rgb * RayMie.w / max(RayMie.r, 1e-4) * (BetaRayleighScattering.rrr / BetaRayleighScattering.rgb);
}

/** Transmittance(=transparency) of atmosphere for ray (r,mu) of length d
 * (mu=cos(view zenith angle)), intersections with ground ignored
 * uses analytic formula instead of transmittance texture, REK 04, Atmospheric Transparency
 */
float3 AnalyticTransmittance(float R, float Mu, float D)
{
    return exp(- BetaRayleighScattering * OpticalDepthWithDistance(View.AtmosphericFogHeightScaleRayleigh, R, Mu, D) - BetaMieExtinction * OpticalDepthWithDistance(HeightScaleMie, R, Mu, D));
}

const float HeightOffset = 0.01f;

/** inscattered light along ray x+tv, when sun in direction s (=S[L]-T(x,x0)S[L]|x0) */
float3 GetInscatterColor(float FogDepth, float3 X, float T, float3 V, float3 S, float Radius, float Mu, out float3 Attenuation, bool bIsSceneGeometry)
{
    float3 Result = float3(0.f, 0.f, 0.f); 	// X in space and ray looking in space, initialize
	Attenuation = float3(1.f, 1.f, 1.f);

	float D = -Radius * Mu - sqrt(Radius * Radius * (Mu * Mu - 1.0) + RadiusAtmosphere * RadiusAtmosphere);
    if (D > 0.0)
	{
		// if X in space and ray intersects atmosphere
        // move X to nearest intersection of ray with top atmosphere boundary
        X += D * V;
        T -= D;
        Mu = (Radius * Mu + D) / RadiusAtmosphere;
        Radius = RadiusAtmosphere;
    }

	float Epsilon = 0.005f;

	if (Radius < RadiusGround + HeightOffset + Epsilon)
	{
		float Diff = (RadiusGround + HeightOffset + Epsilon) - Radius;
		X -= Diff * V;
		T -= Diff;
		Radius = RadiusGround + HeightOffset + Epsilon;
		Mu = dot(X, V) / Radius;
	}

	if (Radius <= RadiusAtmosphere && FogDepth > 0.f)
	{
		float3 X0 = X + T * V;
		float R0 = length(X0);
		// if ray intersects atmosphere
		float Nu = dot(V, S);
		float MuS = dot(X, S) / Radius;

		float MuHorizon = -sqrt(1.0 - (RadiusGround / Radius) * (RadiusGround / Radius));

		if (bIsSceneGeometry)
		{
			Mu = max(Mu, MuHorizon + Epsilon + 0.15);
		}
		else
		{
			Mu = max(Mu, MuHorizon + Epsilon);
		}

		float MuOriginal = Mu;

		float BlendRatio = 0.0f;

		if (bIsSceneGeometry)
		{
			BlendRatio = saturate(exp(-V.z) - 0.5);
			if (BlendRatio < 1.f)
			{
				V.z = max(V.z, 0.15);
				V = normalize(V);
				float3 X1 = X + T * V;
				float R1 = length(X1);
				Mu = dot(X1, V) / R1;
			}
		}

		float PhaseR = PhaseFunctionR(Nu);
		float PhaseM = PhaseFunctionM(Nu);
		float4 Inscatter = max(Texture4DSample(AtmosphereInscatterTexture, AtmosphereInscatterTextureSampler, Radius, Mu, MuS, Nu), 0.0);

		if (T > 0.0)
		{
#if ATMOSPHERIC_TEXTURE_SAMPLE_FIX
			// avoids imprecision problems in transmittance computations based on textures
			Attenuation = AnalyticTransmittance(Radius, Mu, T);
#else
			Attenuation = TransmittanceWithDistance(Radius, Mu, V, X0);
#endif

			float Mu0 = dot(X0, V) / R0;
			float MuS0 = dot(X0, S) / R0;

			if (bIsSceneGeometry)
			{
				R0 = max(R0, Radius);
			}

			if (R0 > RadiusGround + HeightOffset)
			{
				if (BlendRatio < 1.f)
				{
					Inscatter = max(Inscatter - Attenuation.rgbr * Texture4DSample(AtmosphereInscatterTexture, AtmosphereInscatterTextureSampler, R0, Mu0, MuS0, Nu), 0.0);
#if ATMOSPHERIC_TEXTURE_SAMPLE_FIX
					// avoids imprecision problems near horizon by interpolating between two points above and below horizon
					if (!bIsSceneGeometry )
					{
						if (abs(Mu - MuHorizon) < Epsilon)
						{
							float Alpha = ((Mu - MuHorizon) + Epsilon) * 0.5f / Epsilon;

							Mu = MuHorizon - Epsilon;
							R0 = sqrt(Radius * Radius + T * T + 2.0 * Radius * T * Mu);
							Mu0 = (Radius * Mu + T) / R0;

							Mu0 = max(MuHorizon + Epsilon, Mu0);
							float4 Inscatter0 = Texture4DSample(AtmosphereInscatterTexture, AtmosphereInscatterTextureSampler, Radius, Mu, MuS, Nu);
							float4 Inscatter1 = Texture4DSample(AtmosphereInscatterTexture, AtmosphereInscatterTextureSampler, R0, Mu0, MuS0, Nu);
							float4 InscatterA = max(Inscatter0 - Attenuation.rgbr * Inscatter1, 0.0);

							Mu = MuHorizon + Epsilon;
							R0 = sqrt(Radius * Radius + T * T + 2.0 * Radius * T * Mu);

							Mu0 = (Radius * Mu + T) / R0;
							Mu0 = max(MuHorizon + Epsilon, Mu0);
							Inscatter0 = Texture4DSample(AtmosphereInscatterTexture, AtmosphereInscatterTextureSampler, Radius, Mu, MuS, Nu);
							Inscatter1 = Texture4DSample(AtmosphereInscatterTexture, AtmosphereInscatterTextureSampler, R0, Mu0, MuS0, Nu);
							float4 InscatterB = max(Inscatter0 - Attenuation.rgbr * Inscatter1, 0.0);

							Inscatter = lerp(InscatterA, InscatterB, Alpha);
						}
					}
					else if (BlendRatio > 0.f)
					{
						Inscatter = lerp(Inscatter,
							(1.f - Attenuation.rgbr) * max(Texture4DSample(AtmosphereInscatterTexture, AtmosphereInscatterTextureSampler, Radius, MuOriginal, MuS, Nu), 0.0),
							BlendRatio);
					}
#endif
				}
				else
				{
					Inscatter = (1.f - Attenuation.rgbr) * Inscatter;
				}
			}
		}
#if ATMOSPHERIC_TEXTURE_SAMPLE_FIX
        // avoids imprecision problems in Mie scattering when sun is below horizon
        Inscatter.w *= smoothstep(0.00, 0.02, MuS);
#endif
        Result = max(Inscatter.rgb * PhaseR + GetMie(Inscatter) * PhaseM, 0.0);
    }

	return Result;
}

/**
  * Ground radiance at end of ray x+tv, when sun in direction s
  * attenuated bewteen ground and viewer (=R[L0]+R[L*])
  */
float3 GetGroundColor(float4 SceneColor, float3 X, float T, float3 V, float3 S, float Radius, float3 Attenuation, bool bIsSceneGeometry)
{
    float3 Result = float3(0.f, 0.f, 0.f); 	// ray looking at the sky (for intial value)
    if (T > 0.0)
	{
		// if ray hits ground surface
        // ground Reflectance at end of ray, X0
        float3 X0 = X + T * V;
        float R0 = length(X0);
        float3 N = X0 / R0;
		N = X0 / R0;
		SceneColor.xyz = saturate(SceneColor.xyz + 0.05);

        float4 Reflectance = SceneColor * float4(0.2, 0.2, 0.2, 1.0);

        // direct sun light (radiance) reaching X0
        float MuS = dot(N, S);
        float3 SunLight = bIsSceneGeometry ? float3(0.f, 0.f, 0.f) : TransmittanceWithShadow(R0, MuS);

        // precomputed sky light (irradiance) (=E[L*]) at X0
        float3 GroundSkyLight = Irradiance(AtmosphereIrradianceTexture, AtmosphereIrradianceTextureSampler, R0, MuS);

        // light reflected at X0 (=(R[L0]+R[L*])/T(X,X0))
        //
		float3 GroundColor = (Reflectance.rgb * (max(MuS, 0.0) * SunLight + GroundSkyLight)) / PI;

        // water specular color due to SunLight
        if (!bIsSceneGeometry && Reflectance.w > 0.0)
		{
            float3 H = normalize(S - V);
            float Fresnel = 0.02 + 0.98 * pow(1.0 - dot(-V, H), 5.0);
            float WaterBrdf = Fresnel * pow(max(dot(H, N), 0.0), 150.0);
            GroundColor += Reflectance.w * max(WaterBrdf, 0.0) * SunLight;
        }

		Result = Attenuation * GroundColor; //=R[L0]+R[L*]
    }
    return Result;
}

/** Direct sun light for ray x+tv, when sun in direction s (=L0) */
float3 GetSunColor(float3 X, float T, float3 V, float3 S, float Radius, float Mu)
{
	float3 TransmittanceValue = Radius <= RadiusAtmosphere ? TransmittanceWithShadow(Radius, Mu) : float3(1.0, 1.0, 1.0); // T(X,xo)
    if (T > 0.0)
	{
        return float3(0.f, 0.f, 0.f);
    }
	else
	{
		float SunIntensity = step(cos(PI * View.AtmosphericFogSunDiscScale / 180.0), dot(V, S)); // Lsun
        return TransmittanceValue * SunIntensity; // Eq (9)
    }
}

float4 GetAtmosphericFog(float3 ViewPosition, float3 ViewVector, float SceneDepth, float3 SceneColor)
{
	float Scale = 0.00001f * View.AtmosphericFogDistanceScale;
	ViewPosition.z = (ViewPosition.z - View.AtmosphericFogGroundOffset) * View.AtmosphericFogAltitudeScale;
	ViewPosition *= Scale;
	ViewPosition.z += RadiusGround + HeightOffset;

	float Radius = length(ViewPosition);

	float3 V = normalize(ViewVector.xyz);

	float Mu = dot(ViewPosition, V) / Radius;

	float T = -Radius * Mu - sqrt(Radius * Radius * (Mu * Mu - 1.0) + RadiusGround * RadiusGround);

	float DepthThreshold = 100.f * View.AtmosphericFogDistanceScale; // 100km limit
	SceneDepth *= Scale;

	float FogDepth = max(0.f, SceneDepth - View.AtmosphericFogStartDistance);
	float ShadowFactor = 1.f; // shadow approximation
	float DistanceRatio = min(FogDepth * 0.1f / View.AtmosphericFogStartDistance, 1.f);
	bool bIsSceneGeometry = (SceneDepth < DepthThreshold); // Assume as scene geometry
	if (bIsSceneGeometry)
	{
		ShadowFactor = DistanceRatio * View.AtmosphericFogPower;
		T = max(SceneDepth + View.AtmosphericFogDistanceOffset, 1.f);
	}

	float3 Attenuation;

	float3 InscatterColor = GetInscatterColor(FogDepth, ViewPosition, T, V, View.AtmosphericFogSunDirection, Radius, Mu, Attenuation, bIsSceneGeometry); //S[L]-T(ViewPosition,xs)S[l]|xs
#if BASEPASS_ATMOSPHERIC_FOG // Transluceny rendering just follows normal render flag
	float3 GroundColor = 0.f;
	BRANCH
	if ((View.AtmosphericFogRenderMask & RENDERFLAG_DISABLE_GROUND_SCATTERING) == 0)
	{
		GroundColor = GetGroundColor(float4(SceneColor.xyz, 1.f), ViewPosition, T, V, View.AtmosphericFogSunDirection, Radius, Attenuation, bIsSceneGeometry); //R[L0]+R[L*]
	}
#else
	#if MATERIAL_ATMOSPHERIC_FOG || ATMOSPHERIC_NO_GROUND_SCATTERING // Material doesn't need this ground scattering for now
		float3 GroundColor = 0.f;
	#else
		float3 GroundColor = GetGroundColor(float4(SceneColor.xyz, 1.f), ViewPosition, T, V, View.AtmosphericFogSunDirection, Radius, Attenuation, bIsSceneGeometry); //R[L0]+R[L*]
	#endif
#endif

#if BASEPASS_ATMOSPHERIC_FOG // Transluceny rendering just follows normal render flag
	float3 Sun = 0.f;
	BRANCH
	if ((View.AtmosphericFogRenderMask & RENDERFLAG_DISABLE_SUN_DISK) == 0)
	{
		Sun = GetSunColor(ViewPosition, T, V, View.AtmosphericFogSunDirection, Radius, Mu); //L0
	}
#else
	#if MATERIAL_ATMOSPHERIC_FOG || ATMOSPHERIC_NO_SUN_DISK // Material doesn't need to render the sun disk
		float3 Sun = float3(0.0);
	#else
		float3 Sun = GetSunColor(ViewPosition, T, V, View.AtmosphericFogSunDirection, Radius, Mu); //L0
	#endif
#endif

	// Decay color
	float3 OriginalColor = Sun + GroundColor + InscatterColor;
	OriginalColor = View.AtmosphericFogSunPower * ShadowFactor * View.AtmosphericFogSunColor.rgb * OriginalColor;
	float4 OutColor = float4(OriginalColor, lerp(saturate(Attenuation.r * View.AtmosphericFogDensityScale - View.AtmosphericFogDensityOffset), 1.f, (1.f - DistanceRatio)) );
	return OutColor;
}

float4 CalculateVertexAtmosphericFog(float3 WorldPosition, float3 InCameraPosition)
{
	float3 ViewVector = WorldPosition - InCameraPosition;
	float Distance = length(ViewVector);
	return GetAtmosphericFog(InCameraPosition, ViewVector, Distance, float3(0.f, 0.f, 0.f));
}

void Integrand(float Radius, float Mu, float MuS, float Nu, float T, out float3 Ray, out float3 Mie)
{
    Ray = float3(0, 0, 0);
    Mie = float3(0, 0, 0);
    float Ri = sqrt(Radius * Radius + T * T + 2.0 * Radius * Mu * T);
    float MuSi = (Nu * T + MuS * Radius) / Ri;
	Ri = max(RadiusGround, Ri);
	if (MuSi >= -sqrt(1.0 - RadiusGround * RadiusGround / (Ri * Ri)) )
	{
		float3 Ti = TransmittanceWithDistance(Radius, Mu, T) * Transmittance(Ri, MuSi);
		Ray = exp(-(Ri - RadiusGround) / View.AtmosphericFogHeightScaleRayleigh) * Ti;
		Mie = exp(-(Ri - RadiusGround) / HeightScaleMie) * Ti;
	}
}

#if COMPILER_METAL && MAX_SHADER_LANGUAGE_VERSION <= 3
// Do not export our workaround outside this file as it might not be desirable.
#undef sqrt
#endif

#endif
