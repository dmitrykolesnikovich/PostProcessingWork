// Copyright 1998-2019 Epic Games, Inc. All Rights Reserved.

#pragma once

#include "Common.ush"

/*=============================================================================
	MonteCarlo.usf: Monte Carlo integration of distributions
=============================================================================*/

// [ Duff et al. 2017, "Building an Orthonormal Basis, Revisited" ]
float3x3 GetTangentBasis( float3 TangentZ )
{
	const float Sign = TangentZ.z >= 0 ? 1 : -1;
	const float a = -rcp( Sign + TangentZ.z );
	const float b = TangentZ.x * TangentZ.y * a;

	float3 TangentX = { 1 + Sign * a * Pow2( TangentZ.x ), Sign * b, -Sign * TangentZ.x };
	float3 TangentY = { b,  Sign + a * Pow2( TangentZ.y ), -TangentZ.y };

	return float3x3( TangentX, TangentY, TangentZ );
}

float3 TangentToWorld( float3 Vec, float3 TangentZ )
{
	return mul( Vec, GetTangentBasis( TangentZ ) );
}

float3 WorldToTangent(float3 Vec, float3 TangentZ)
{
	return mul(GetTangentBasis(TangentZ), Vec);
}

float2 Hammersley( uint Index, uint NumSamples, uint2 Random )
{
	float E1 = frac( (float)Index / NumSamples + float( Random.x & 0xffff ) / (1<<16) );
	float E2 = float( ReverseBits32(Index) ^ Random.y ) * 2.3283064365386963e-10;
	return float2( E1, E2 );
}

float2 Hammersley16( uint Index, uint NumSamples, uint2 Random )
{
	float E1 = frac( (float)Index / NumSamples + float( Random.x ) * (1.0 / 65536.0) );
	float E2 = float( ( ReverseBits32(Index) >> 16 ) ^ Random.y ) * (1.0 / 65536.0);
	return float2( E1, E2 );
}

// http://extremelearning.com.au/a-simple-method-to-construct-isotropic-quasirandom-blue-noise-point-sequences/
float2 R2Sequence( uint Index )
{
	const float Phi = 1.324717957244746;
	const float2 a = float2( 1.0 / Phi, 1.0 / Pow2(Phi) );
	return frac( a * Index );
}

// R2 Jittered point set
// These seem to be garbage so use at your own risk. Jitter is not large enough for low sample counts. Larger jitter overlaps neighboring samples unevenly.
float2 JitteredR2( uint Index, uint NumSamples, float2 Jitter, float JitterAmount = 0.5 )
{
	const float Phi = 1.324717957244746;
	const float2 a = float2( 1.0 / Phi, 1.0 / Pow2(Phi) );
	const float d0 = 0.76;
	const float i0 = 0.7;

	return frac( a * Index + ( JitterAmount * 0.5 * d0 * sqrt(PI) * rsqrt( NumSamples ) ) * Jitter );
}

// R2 Jittered point sequence. Progressive
float2 JitteredR2( uint Index, float2 Jitter, float JitterAmount = 0.5 )
{
	const float Phi = 1.324717957244746;
	const float2 a = float2( 1.0 / Phi, 1.0 / Pow2(Phi) );
	const float d0 = 0.76;
	const float i0 = 0.7;

	return frac( a * Index + ( JitterAmount * 0.25 * d0 * sqrt(PI) * rsqrt( Index - i0 ) ) * Jitter );
}

///////
// Typical usage of the Sobol functions for one or more points per pixel
//   uint2 SobolBase = SobolPixel(uint2(SvPosition.xy));                    // First sample for this pixel
//   for(int i = 0; i < N; ++i)
//     float2 Point = float2(SobolIndex(SobolBase, i)) / 0x10000;           // Points as [0,1) x [0,1)
//
// Typical usage for one or more points per frame
//   uint2 SobolBase = SobolPixel(uint2(SvPosition.xy));                    // Frame 0, point 0 for this pixel
//   uint2 SobolFrame = SobolIndex(SobolBase, View.StateFrameIndexMod8, 3); // Least significant bits for frame
//   for(int i = 0; i < N; ++i)
//     float2 Point = float2(SobolIndex(SobolFrame, i << 3)) / 0x10000;     // Higher-order bits for point within frame
//
// For additional independent point sets
//   uint2 SobolBase2 = SobolBase ^ RandSeed; // where RandSeed is a uint2 with values in 0 to 0xffff

// Compute a Sobol-distributed point from a 256x256 pixel grid one pixel in that grid
// @param Pixel Pixel/cell position in the 256x256 grid
// @return Sobol position relative to the pixel corner, with components in the range 0 to 0xffff
uint2 SobolPixel(uint2 Pixel)
{
	// look up for pixel
	int3 SobolLo = int3(Pixel & 0xfu, 0);
	int3 SobolHi = int3((Pixel >> 4u) & 0xfu, 0) + int3(16, 0, 0);
	uint Packed = View.SobolSamplingTexture.Load(SobolLo) ^ View.SobolSamplingTexture.Load(SobolHi);
	return uint2(Packed, Packed << 8u) & 0xff00u;
}

// Evaluate additional Sobol points within a pixel
// @param Base  Base Sobol point for this pixel
// @param Index Which 2D Sobol point to return
// @param Bits  Optional max bits in index (to avoid extra calculation)
// @return Sobol position in the range 0 to 0xffff
uint2 SobolIndex(uint2 Base, int Index, int Bits = 10)
{
	uint2 SobolNumbers[10] = {
		uint2(0x8680u, 0x4c80u), uint2(0xf240u, 0x9240u), uint2(0x8220u, 0x0e20u), uint2(0x4110u, 0x1610u), uint2(0xa608u, 0x7608u),
		uint2(0x8a02u, 0x280au), uint2(0xe204u, 0x9e04u), uint2(0xa400u, 0x4682u), uint2(0xe300u, 0xa74du), uint2(0xb700u, 0x9817u),
	};

	uint2 Result = Base;
	UNROLL for (int b = 0; b < 10 && b < Bits; ++b)
	{
		Result ^= (Index & (1 << b)) ? SobolNumbers[b] : 0;
	}
	return Result;
}

/** Returns an unique sobol sample for the frame. */
uint2 ComputePixelUniqueSobolRandSample(uint2 PixelCoord)
{
	const uint TemporalBits = 10;
	uint FrameIndexMod1024 = ReverseBitsN(GetPowerOfTwoModulatedFrameIndex(1 << TemporalBits), TemporalBits);

	uint2 SobolBase = SobolPixel(PixelCoord);
	return SobolIndex(SobolBase, FrameIndexMod1024, TemporalBits);
}


///////

float2 UniformSampleDisk( float2 E )
{
	float Theta = 2 * PI * E.x;
	float Radius = sqrt( E.y );
	return Radius * float2( cos( Theta ), sin( Theta ) );
}

float2 UniformSampleDiskConcentric( float2 E )
{
	float2 p = 2 * E - 1;
	float Radius;
	float Phi;
	if( abs( p.x ) > abs( p.y ) )
	{
		Radius = p.x;
		Phi = (PI/4) * (p.y / p.x);
	}
	else
	{
		Radius = p.y;
		Phi = (PI/2) - (PI/4) * (p.x / p.y);
	}
	return float2( Radius * cos( Phi ), Radius * sin( Phi ) );
}

// based on the approximate equal area transform from
// http://marc-b-reynolds.github.io/math/2017/01/08/SquareDisc.html
float2 UniformSampleDiskConcentricApprox( float2 E )
{
	float2 sf = E * sqrt(2.0) - sqrt(0.5);	// map 0..1 to -sqrt(0.5)..sqrt(0.5)
	float2 sq = sf*sf;
	float root = sqrt(2.0*max(sq.x, sq.y) - min(sq.x, sq.y));
	if (sq.x > sq.y)
	{
		sf.x = sf.x > 0 ? root : -root;
	}
	else
	{
		sf.y = sf.y > 0 ? root : -root;
	}
	return sf;
}

float4 UniformSampleSphere( float2 E )
{
	float Phi = 2 * PI * E.x;
	float CosTheta = 1 - 2 * E.y;
	float SinTheta = sqrt( 1 - CosTheta * CosTheta );

	float3 H;
	H.x = SinTheta * cos( Phi );
	H.y = SinTheta * sin( Phi );
	H.z = CosTheta;

	float PDF = 1.0 / (4 * PI);

	return float4( H, PDF );
}

float4 UniformSampleHemisphere( float2 E )
{
	float Phi = 2 * PI * E.x;
	float CosTheta = E.y;
	float SinTheta = sqrt( 1 - CosTheta * CosTheta );

	float3 H;
	H.x = SinTheta * cos( Phi );
	H.y = SinTheta * sin( Phi );
	H.z = CosTheta;

	float PDF = 1.0 / (2 * PI);

	return float4( H, PDF );
}

float4 CosineSampleHemisphere( float2 E )
{
	float Phi = 2 * PI * E.x;
	float CosTheta = sqrt( E.y );
	float SinTheta = sqrt( 1 - CosTheta * CosTheta );

	float3 H;
	H.x = SinTheta * cos( Phi );
	H.y = SinTheta * sin( Phi );
	H.z = CosTheta;

	float PDF = CosTheta * (1.0 /  PI);

	return float4( H, PDF );
}

float4 CosineSampleHemisphere( float2 E, float3 N )
{
	float3 H = UniformSampleSphere( E ).xyz;
	H = normalize( N + H );

	float PDF = H.z * (1.0 /  PI);

	return float4( H, PDF );
}

float4 UniformSampleCone( float2 E, float CosThetaMax )
{
	float Phi = 2 * PI * E.x;
	float CosTheta = lerp( CosThetaMax, 1, E.y );
	float SinTheta = sqrt( 1 - CosTheta * CosTheta );

	float3 L;
	L.x = SinTheta * cos( Phi );
	L.y = SinTheta * sin( Phi );
	L.z = CosTheta;

	float PDF = 1.0 / ( 2 * PI * (1 - CosThetaMax) );

	return float4( L, PDF );
}

float4 ImportanceSampleBlinn( float2 E, float a2 )
{
	float n = 2 / a2 - 2;

	float Phi = 2 * PI * E.x;
	float CosTheta = ClampedPow( E.y, 1 / (n + 1) );
	float SinTheta = sqrt( 1 - CosTheta * CosTheta );

	float3 H;
	H.x = SinTheta * cos( Phi );
	H.y = SinTheta * sin( Phi );
	H.z = CosTheta;

	float D = (n+2) / (2*PI) * ClampedPow( CosTheta, n );
	float PDF = D * CosTheta;

	return float4( H, PDF );
}

float4 ImportanceSampleGGX( float2 E, float a2 )
{
	float Phi = 2 * PI * E.x;
	float CosTheta = sqrt( (1 - E.y) / ( 1 + (a2 - 1) * E.y ) );
	float SinTheta = sqrt( 1 - CosTheta * CosTheta );

	float3 H;
	H.x = SinTheta * cos( Phi );
	H.y = SinTheta * sin( Phi );
	H.z = CosTheta;

	float d = ( CosTheta * a2 - CosTheta ) * CosTheta + 1;
	float D = a2 / ( PI*d*d );
	float PDF = D * CosTheta;

	return float4( H, PDF );
}

// [ Heitz 2018, "Sampling the GGX Distribution of Visible Normals" ]
// http://jcgt.org/published/0007/04/01/

float4 ImportanceSampleVisibleGGX( float2 E, float a2, float3 V )
{
	// TODO float2 alpha for anisotropic
	float a = sqrt(a2);

	// stretch
	float3 Vh = normalize( float3( a * V.xy, V.z ) );

	// Orthonormal basis
	float3 Tangent0 = (Vh.z < 0.9999) ? normalize( cross( float3(0, 0, 1), Vh ) ) : float3(1, 0, 0);
	float3 Tangent1 = cross( Vh, Tangent0 );

	float Radius = sqrt( E.x );
	float Phi = 2 * PI * E.y;

	float2 p = Radius * float2( cos( Phi ), sin( Phi ) );
	float s = 0.5 + 0.5 * Vh.z;
	p.y = (1 - s) * sqrt( 1 - p.x * p.x ) + s * p.y;

	float3 H;
	H  = p.x * Tangent0;
	H += p.y * Tangent1;
	H += sqrt( saturate( 1 - dot( p, p ) ) ) * Vh;

	// unstretch
	H = normalize( float3( a * H.xy, max(0.0, H.z) ) );

	float NoV = V.z;
	float NoH = H.z;
	float VoH = dot(V, H);

	float d = (NoH * a2 - NoH) * NoH + 1;
	float D = a2 / (PI*d*d);

	float G_SmithV = 2 * NoV / (NoV + sqrt(NoV * (NoV - NoV * a2) + a2));

	float PDF = G_SmithV * VoH * D / NoV;

	return float4(H, PDF);
}

// Multiple importance sampling power heuristic of two functions with a power of two.
// [Veach 1997, "Robust Monte Carlo Methods for Light Transport Simulation"]
float MISWeight( uint Num, float PDF, uint OtherNum, float OtherPDF )
{
	float Weight = Num * PDF;
	float OtherWeight = OtherNum * OtherPDF;
	return Weight * Weight / (Weight * Weight + OtherWeight * OtherWeight);
}