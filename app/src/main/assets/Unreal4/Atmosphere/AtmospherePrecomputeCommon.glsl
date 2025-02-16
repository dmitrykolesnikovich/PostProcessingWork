// Copyright 1998-2019 Epic Games, Inc. All Rights Reserved.

/*=============================================================================
	AtmospherePrecomputeCommon.usf: Functions and variables only used in precomputation in editor

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

#ifndef __ATMOSPHERE_PRECOMPUTE_COMMON__
#define __ATMOSPHERE_PRECOMPUTE_COMMON__

/** Integration Parameters */
const int TransmittanceIntegralSamples = 500;
const int InscatterIntegralSamples = 50;
const int IrradianceIntegralSamplesHalf = 16; // 32 / 2, division for integer has some problem in nVidia Card
const int InscatterSphericalIntegralSamples = 16;

const float DeltaPhi = PI / float(InscatterSphericalIntegralSamples);
const float DeltaTheta = PI / float(InscatterSphericalIntegralSamples);

uniform float FirstOrder;

/** Textures only for precomputation on editor */
uniform sampler2D AtmosphereDeltaETexture;
//SamplerState AtmosphereDeltaETextureSampler;
uniform sampler3D AtmosphereDeltaSRTexture;
//SamplerState AtmosphereDeltaSRTextureSampler;
uniform sampler3D AtmosphereDeltaSMTexture;
//SamplerState AtmosphereDeltaSMTextureSampler;
uniform sampler3D AtmosphereDeltaJTexture;
//SamplerState AtmosphereDeltaJTextureSampler;

#endif