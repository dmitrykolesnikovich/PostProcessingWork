#include "Scattering.frag"

float GetAverageSceneLuminance()
{
#if AUTO_EXPOSURE
//    float fAveLogLum = g_tex2DAverageLuminance.Load( int3(0,0,0) );
	float fAveLogLum = texelFetch(g_tex2DAverageLuminance, int2(0,0), 0).r;
#else
    float fAveLogLum =  0.1;
#endif
    fAveLogLum = max(0.05, fAveLogLum); // Average luminance is an approximation to the key of the scene
    return fAveLogLum;
}

float3 Uncharted2Tonemap(float3 x)
{
    // http://www.gdcvault.com/play/1012459/Uncharted_2__HDR_Lighting
    // http://filmicgames.com/archives/75 - the coefficients are from here
    float A = 0.15; // Shoulder Strength
    float B = 0.50; // Linear Strength
    float C = 0.10; // Linear Angle
    float D = 0.20; // Toe Strength
    float E = 0.02; // Toe Numerator
    float F = 0.30; // Toe Denominator
    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F; // E/F = Toe Angle
}

float3 ToneMap(in float3 f3Color)
{
    float fAveLogLum = GetAverageSceneLuminance();
    
    //const float middleGray = 1.03 - 2 / (2 + log10(fAveLogLum+1));
    const float middleGray = g_fMiddleGray;
    // Compute scale factor such that average luminance maps to middle gray
    float fLumScale = middleGray / fAveLogLum;
    
    f3Color = max(f3Color, 0);
    float fInitialPixelLum = max(dot(RGB_TO_LUMINANCE, f3Color), 1e-10);
    float fScaledPixelLum = fInitialPixelLum * fLumScale;
    float3 f3ScaledColor = f3Color * fLumScale;

    float whitePoint = g_fWhitePoint;

#if TONE_MAPPING_MODE == TONE_MAPPING_MODE_EXP
    
    float  fToneMappedLum = 1.0 - exp( -fScaledPixelLum );
    return fToneMappedLum * pow(f3Color / fInitialPixelLum, float3(g_fLuminanceSaturation));

#elif TONE_MAPPING_MODE == TONE_MAPPING_MODE_REINHARD || TONE_MAPPING_MODE == TONE_MAPPING_MODE_REINHARD_MOD

    // http://www.cs.utah.edu/~reinhard/cdrom/tonemap.pdf
    // http://imdoingitwrong.wordpress.com/2010/08/19/why-reinhard-desaturates-my-blacks-3/
    // http://content.gpwiki.org/index.php/D3DBook:High-Dynamic_Range_Rendering

    float  L_xy = fScaledPixelLum;
#   if TONE_MAPPING_MODE == TONE_MAPPING_MODE_REINHARD
        float  fToneMappedLum = L_xy / (1 + L_xy);
#   else
	    float  fToneMappedLum = L_xy * (1 + L_xy / (whitePoint*whitePoint)) / (1 + L_xy);
#   endif
	return fToneMappedLum * pow(f3Color / fInitialPixelLum, float3(g_fLuminanceSaturation));

#elif TONE_MAPPING_MODE == TONE_MAPPING_MODE_UNCHARTED2

    // http://filmicgames.com/archives/75
    float ExposureBias = 2.0f;
    float3 curr = Uncharted2Tonemap(ExposureBias*f3ScaledColor);
    float3 whiteScale = 1.0f/Uncharted2Tonemap(float3(whitePoint));
    return curr*whiteScale;

#elif TONE_MAPPING_MODE == TONE_MAPPING_FILMIC_ALU

    // http://www.gdcvault.com/play/1012459/Uncharted_2__HDR_Lighting
    float3 f3ToneMappedColor = max(0, f3ScaledColor - 0.004f);
    f3ToneMappedColor = (f3ToneMappedColor * (6.2f * f3ToneMappedColor + 0.5f)) / 
                        (f3ToneMappedColor * (6.2f * f3ToneMappedColor + 1.7f)+ 0.06f);
    // result has 1/2.2 gamma baked in
    return pow(f3ToneMappedColor, 2.2f);

#elif TONE_MAPPING_MODE == TONE_MAPPING_LOGARITHMIC
    
    // http://www.mpi-inf.mpg.de/resources/tmo/logmap/logmap.pdf
    float fToneMappedLum = log10(1.0 + fScaledPixelLum) / log10(1.0 + whitePoint);
	return fToneMappedLum * pow(f3Color / fInitialPixelLum, g_fLuminanceSaturation);

#elif TONE_MAPPING_MODE == TONE_MAPPING_ADAPTIVE_LOG

    // http://www.mpi-inf.mpg.de/resources/tmo/logmap/logmap.pdf
    float Bias = 0.85;
    float fToneMappedLum = 
        1.0 / log10(1.0 + whitePoint) *
        log(1.0 + fScaledPixelLum) / log( 2.0 + 8.0 * pow( fScaledPixelLum / whitePoint, log(Bias) / log(0.5f)) );
	return fToneMappedLum * pow(f3Color / fInitialPixelLum, g_fLuminanceSaturation);

#endif
}