
//--------------------------------------------------------------------------------------
// Copyright 2013 Intel Corporation
// All Rights Reserved
//
// Permission is granted to use, copy, distribute and prepare derivative works of this
// software for any purpose and without fee, provided, that the above copyright notice
// and this statement appear in all copies.  Intel makes no representations about the
// suitability of this software for any purpose.  THIS SOFTWARE IS PROVIDED "AS IS."
// INTEL SPECIFICALLY DISCLAIMS ALL WARRANTIES, EXPRESS OR IMPLIED, AND ALL LIABILITY,
// INCLUDING CONSEQUENTIAL AND OTHER INDIRECT DAMAGES, FOR THE USE OF THIS SOFTWARE,
// INCLUDING LIABILITY FOR INFRINGEMENT OF ANY PROPRIETARY RIGHTS, AND INCLUDING THE
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.  Intel does not
// assume any responsibility for any errors which may appear in this software nor any
// responsibility to update it.
//--------------------------------------------------------------------------------------
#include "PostProcessingLightScatteringCommon.frag"

#ifndef INITIAL_SAMPLE_STEP
#   define INITIAL_SAMPLE_STEP 128
#endif

#ifndef THREAD_GROUP_SIZE
#   define THREAD_GROUP_SIZE max(INITIAL_SAMPLE_STEP, 32)
#endif

layout (rg16ui, binding = 0) uniform uimage2D g_rwtex2DInterpolationSource;

// In my first implementation I used group shared memory to store camera space z
// values. This was a very low-performing method
// After that I tried using arrays of bool flags instead, but this did not help very much
// since memory bandwidth was almost the same (on GPU each bool consumes 4 bytes)
// Finally, I came up with packing 32 flags into single uint value.
// This not only enables using 32x times less memory, but also enables very efficient
// test if depth break is present in the section
const uint g_uiNumPackedFlags = THREAD_GROUP_SIZE/32;
shared uint g_uiPackedCamSpaceDiffFlags[ g_uiNumPackedFlags ];

layout (local_size_x = THREAD_GROUP_SIZE) in;
void main()
{
	uvec3 Gid = gl_WorkGroupID;
	uvec3 GTid = gl_LocalInvocationID;
	
	// Each thread group processes one slice
    uint uiSliceInd = Gid.y;
    // Compute global index of the first sample in the thread group
    // Each group processes THREAD_GROUP_SIZE samples in the slice
    uint uiGroupStartGlobalInd = Gid.x * THREAD_GROUP_SIZE;
    uint uiSampleInd = GTid.x; // Sample index in the group
    // Compute global index of this sample which is required to fetch the sample's coordinates
    uint uiGlobalSampleInd = uiGroupStartGlobalInd + uiSampleInd;
    // Load location of the current sample using global sample index
//    float2 f2SampleLocationPS = g_tex2DCoordinates.Load( uint3(uiGlobalSampleInd, uiSliceInd, 0) );
	float2 f2SampleLocationPS = texelFetch(g_tex2DCoordinates, int2(uiGlobalSampleInd, uiSliceInd), 0).xy;
    
    bool bIsValidThread = all( lessThan (abs(f2SampleLocationPS), float2(1+1e-4)) );
    
    // Initialize flags with zeroes
    if( GTid.x < g_uiNumPackedFlags )
        g_uiPackedCamSpaceDiffFlags[GTid.x] = 0;

    GroupMemoryBarrierWithGroupSync();

    // Let each thread in the group compute its own flag
    // Note that if the sample is located behind the screen, its flag will be set to zero
    // Besides, since g_tex2DEpipolarCamSpaceZ is cleared with invalid coordinates, the difference
    // flag between valid and invalid locations will also be zero. Thus the sample next to invalid will always
    // be marked as ray marching sample
//    [branch]
    if( bIsValidThread )
    {
        // Load camera space Z for this sample and for its right neighbor (remeber to use global sample index)
        bool bFlag;
//        float fCamSpaceZ =            g_tex2DEpipolarCamSpaceZ.Load( uint3(uiGlobalSampleInd,   uiSliceInd, 0) );
//        float fRightNeighbCamSpaceZ = g_tex2DEpipolarCamSpaceZ.Load( uint3(uiGlobalSampleInd+1, uiSliceInd, 0) );
		float fCamSpaceZ 			  = texelFetch(g_tex2DEpipolarCamSpaceZ, int2(uiGlobalSampleInd,     uiSliceInd), 0).r;
		float fRightNeighbCamSpaceZ   = texelFetch(g_tex2DEpipolarCamSpaceZ, int2(uiGlobalSampleInd + 1, uiSliceInd), 0).r;
        // Compare the difference with the threshold
        bFlag = abs(fCamSpaceZ - fRightNeighbCamSpaceZ) < g_fRefinementThreshold;
        
        // Set appropriate flag using INTERLOCKED Or:
        InterlockedOr( g_uiPackedCamSpaceDiffFlags[uiSampleInd/32], uint(bFlag) << (uiSampleInd%32) );  // TODO
    }

    // Synchronize threads in the group
    GroupMemoryBarrierWithGroupSync();
    
    // Skip invalid threads. This can be done only after the synchronization
    if( !bIsValidThread )
        return;

    //                                 uiInitialSampleStep
    //       uiSampleInd             |<--------->|
    //          |                    |           |
    //       X  *  *  *  X  *  *  *  X  *  *  *  X           X - locations of initial samples
    //       |           |
    //       |           uiInitialSample1Ind
    //      uiInitialSample0Ind
    //
    // Find two closest initial ray marching samples
    uint uiInitialSampleStep = INITIAL_SAMPLE_STEP;
    uint uiInitialSample0Ind = (uiSampleInd / uiInitialSampleStep) * uiInitialSampleStep;
    // Use denser sampling near the epipole to account for high variation
    // Note that sampling near the epipole is very cheap since only a few steps
    // are required to perform ray marching
    uint uiInitialSample0GlobalInd = uiInitialSample0Ind + uiGroupStartGlobalInd;
//    float2 f2InitialSample0Coords = g_tex2DCoordinates.Load( uint3(uiInitialSample0GlobalInd, uiSliceInd, 0) );
	float2 f2InitialSample0Coords = texelFetch(g_tex2DCoordinates, int2(uiInitialSample0GlobalInd, uiSliceInd), 0).xy;
    if( uiInitialSample0GlobalInd/float(MAX_SAMPLES_IN_SLICE) < 0.1 && 
        length(f2InitialSample0Coords - g_f4LightScreenPos.xy) < 0.3 )
    {
        uiInitialSampleStep = max( INITIAL_SAMPLE_STEP / m_uiEpipoleSamplingDensityFactor, 1 );
        uiInitialSample0Ind = (uiSampleInd / uiInitialSampleStep) * uiInitialSampleStep;
    }
    uint uiInitialSample1Ind = uiInitialSample0Ind + uiInitialSampleStep;

    // Remeber that the last sample in each epipolar slice must be ray marching one
    uint uiInterpolationTexWidth, uiInterpolationTexHeight;
//    g_rwtex2DInterpolationSource.GetDimensions(uiInterpolationTexWidth, uiInterpolationTexHeight);
	int2 iDstImageSize = imageSize(g_rwtex2DInterpolationSource);
	uiInterpolationTexWidth = iDstImageSize.x;
	uiInterpolationTexHeight = iDstImageSize.y;
	
    if( Gid.x == uiInterpolationTexWidth/THREAD_GROUP_SIZE - 1 )
        uiInitialSample1Ind = min(uiInitialSample1Ind, THREAD_GROUP_SIZE-1);

    uint uiLeftSrcSampleInd  = uiSampleInd;
    uint uiRightSrcSampleInd = uiSampleInd;

    // Do nothing if sample is one of initial samples. In this case the sample will be 
    // interpolated from itself
    if( uiSampleInd > uiInitialSample0Ind && uiSampleInd < uiInitialSample1Ind )
    {
        // Load group shared memory to the thread local memory
        uint uiPackedCamSpaceDiffFlags[ g_uiNumPackedFlags ];
        for(uint i=0; i < g_uiNumPackedFlags; ++i)
            uiPackedCamSpaceDiffFlags[i] = g_uiPackedCamSpaceDiffFlags[i];
    
        // Check if there are no depth breaks in the whole section
        // In such case all the flags are set
        bool bNoDepthBreaks = true;
#if INITIAL_SAMPLE_STEP < 32
        {
            // Check if all uiInitialSampleStep flags starting from
            // position uiInitialSample0Ind are set:
            uint iFlagPackOrder = uiInitialSample0Ind / 32;
            uint iFlagOrderInPack = uiInitialSample0Ind % 32;
            uint uiFlagPack = uiPackedCamSpaceDiffFlags[iFlagPackOrder];
            uint uiAllFlagsMask = ((1<<uiInitialSampleStep) - 1);
            if( ((uiFlagPack >> iFlagOrderInPack) & uiAllFlagsMask) != uiAllFlagsMask )
                bNoDepthBreaks = false;
        }
#else
        {
            for(uint i=0; i < g_uiNumPackedFlags; ++i)
                if( uiPackedCamSpaceDiffFlags[i] != 0xFFFFFFFFU )
                    // If at least one flag is not set, there is a depth break on this section
                    bNoDepthBreaks = false;
        }
#endif

        if( bNoDepthBreaks )
        {
            // If there are no depth breaks, we can skip all calculations
            // and use initial sample locations as interpolation sources:
            uiLeftSrcSampleInd = uiInitialSample0Ind;
            uiRightSrcSampleInd = uiInitialSample1Ind;
        }
        else
        {
            // Find left interpolation source
            {
                // Note that i-th flag reflects the difference between i-th and (i+1)-th samples:
                // Flag[i] = abs(fCamSpaceZ[i] - fCamSpaceZ[i+1]) < g_fRefinementThreshold;
                // We need to find first depth break starting from iFirstDepthBreakToTheLeftInd sample
                // and going to the left up to uiInitialSample0Ind
                uint iFirstDepthBreakToTheLeftInd = uiSampleInd-1u;
                //                                                              iFirstDepthBreakToTheLeftInd
                //                                                                     |
                //                                                                     V
                //      0  1  2  3                       30 31   32 33     ....   i-1  i  i+1 ....  63   64
                //   |                                         |                           1  1  1  1  |
                //          uiPackedCamSpaceDiffFlags[0]             uiPackedCamSpaceDiffFlags[1]
                //
                //   iFlagOrderInPack == i % 32

                int iFlagPackOrder = int(iFirstDepthBreakToTheLeftInd) / 32;
                int iFlagOrderInPack = int(iFirstDepthBreakToTheLeftInd) % 32;
                uint uiFlagPack = uiPackedCamSpaceDiffFlags[iFlagPackOrder];
                // To test if there is a depth break in the current flag pack,
                // we must check all flags starting from the iFlagOrderInPack
                // downward to 0 position. We must skip all flags from iFlagOrderInPack+1 to 31
                if( iFlagOrderInPack < 31 )
                {
                    // Set all higher flags to 1, so that they will be skipped
                    // Note that if iFlagOrderInPack == 31, there are no flags to skip
                    // Note also that (U << 32) != 0 as it can be expected. (U << 32) == U instead
                    uiFlagPack |= ( uint(0x0FFFFFFFFU) << uint(iFlagOrderInPack+1) );
                }
                // Find first zero flag starting from iFlagOrderInPack position. Since all
                // higher bits are set, they will be effectivelly skipped
                int iFirstUnsetFlagPos = firstbithigh( uint(~uiFlagPack) );
                // firstbithigh(0) == +INT_MAX
                if( !(0 <= iFirstUnsetFlagPos && iFirstUnsetFlagPos < 32) )
                    // There are no set flags => proceed to the next uint flag pack
                    iFirstUnsetFlagPos = -1;
                iFirstDepthBreakToTheLeftInd -= iFlagOrderInPack - iFirstUnsetFlagPos;

#if INITIAL_SAMPLE_STEP > 32
                // Check the remaining full flag packs
                iFlagPackOrder--;
                while( iFlagPackOrder >= 0 && iFirstUnsetFlagPos == -1 )
                {
                    uiFlagPack = uiPackedCamSpaceDiffFlags[iFlagPackOrder];
                    iFirstUnsetFlagPos = firstbithigh( uint(~uiFlagPack) );
                    if( !(0 <= iFirstUnsetFlagPos && iFirstUnsetFlagPos < 32) )
                        iFirstUnsetFlagPos = -1;
                    iFirstDepthBreakToTheLeftInd -= 31 - iFirstUnsetFlagPos;
                    iFlagPackOrder--;
                }
#endif
                // Ray marching sample is located next to the identified depth break:
                uiLeftSrcSampleInd = max( uint(iFirstDepthBreakToTheLeftInd + 1), uiInitialSample0Ind );
            }

            // Find right interpolation source using symmetric method
            {
                // We need to find first depth break starting from iRightSrcSampleInd and
                // going to the right up to the uiInitialSample1Ind
                uiRightSrcSampleInd = uiSampleInd;
                uint iFlagPackOrder = uiRightSrcSampleInd / 32;
                uint iFlagOrderInPack = uiRightSrcSampleInd % 32;
                uint uiFlagPack = uiPackedCamSpaceDiffFlags[iFlagPackOrder];
                // We need to find first unset flag in the current flag pack
                // starting from iFlagOrderInPack position and up to the 31st bit
                // Set all lower order bits to 1 so that they are skipped during
                // the test:
                if( iFlagOrderInPack > 0 )
                    uiFlagPack |= ( (1 << uint(iFlagOrderInPack))-1 );
                // Find first zero flag:
                int iFirstUnsetFlagPos = firstbitlow( uint(~uiFlagPack) );
                if( !(0 <= iFirstUnsetFlagPos && iFirstUnsetFlagPos < 32) )
                    iFirstUnsetFlagPos = 32;
                uiRightSrcSampleInd += iFirstUnsetFlagPos - iFlagOrderInPack;

#if INITIAL_SAMPLE_STEP > 32
                // Check the remaining full flag packs
                iFlagPackOrder++;
                while( iFlagPackOrder < int(g_uiNumPackedFlags) && iFirstUnsetFlagPos == 32 )
                {
                    uiFlagPack = uiPackedCamSpaceDiffFlags[iFlagPackOrder];
                    iFirstUnsetFlagPos = firstbitlow( uint(~uiFlagPack) );
                    if( !(0 <= iFirstUnsetFlagPos && iFirstUnsetFlagPos < 32) )
                        iFirstUnsetFlagPos = 32;
                    uiRightSrcSampleInd += iFirstUnsetFlagPos;
                    iFlagPackOrder++;
                }
#endif
                uiRightSrcSampleInd = min(uiRightSrcSampleInd, uiInitialSample1Ind);
            }
        }

        // If at least one interpolation source is the same as the sample itself, the
        // sample is ray marching sample and is interpolated from itself:
        if(uiLeftSrcSampleInd == uiSampleInd || uiRightSrcSampleInd == uiSampleInd )
            uiLeftSrcSampleInd = uiRightSrcSampleInd = uiSampleInd;
    }

 //   g_rwtex2DInterpolationSource[ uint2(uiGlobalSampleInd, uiSliceInd) ] = uint2(uiGroupStartGlobalInd + uiLeftSrcSampleInd, uiGroupStartGlobalInd + uiRightSrcSampleInd);
	imageStore(g_rwtex2DInterpolationSource, int2(uiGlobalSampleInd, uiSliceInd), uint4(uiGroupStartGlobalInd + uiLeftSrcSampleInd, uiGroupStartGlobalInd + uiRightSrcSampleInd,0,0));
}
