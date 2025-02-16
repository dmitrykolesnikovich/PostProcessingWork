#version 430
/**/

#extension GL_ARB_shading_language_include : enable
#include "../../../shader_libs/HBAO/PostProcessingHBAOCommon.h"

layout(std140,binding=UBO_SCENE) uniform sceneBuffer {
  SceneData   scene;
};

in Interpolants {
  vec3 pos;
  vec3 normal;
  flat vec4 color;
} IN;

layout(location=0,index=0) out vec4 out_Color;

void main()
{
  vec3  light = normalize(vec3(-1,2,1));
  float intensity = dot(normalize(IN.normal),light) * 0.5 + 0.5;
  vec4  color = IN.color * mix(vec4(0,0.25,0.75,0),vec4(1,1,1,0),intensity);
  
  out_Color = color;
}

/*-----------------------------------------------------------------------
  Copyright (c) 2014, NVIDIA. All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:
   * Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
   * Neither the name of its contributors may be used to endorse 
     or promote products derived from this software without specific
     prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS ``AS IS'' AND ANY
  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
  OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
-----------------------------------------------------------------------*/