#include "PostProcessingCommonPS.frag"

/*
DoF with bokeh GLSL shader v2.4
by Martins Upitis (martinsh) (devlog-martinsh.blogspot.com)
http://blenderartists.org/forum/showthread.php?237488-GLSL-depth-of-field-with-bokeh-v2-4-%28update%29

----------------------
The shader is Blender Game Engine ready, but it should be quite simple to adapt for your engine.

This work is licensed under a Creative Commons Attribution 3.0 Unported License.
So you are free to share, modify and adapt it for your needs, and even use it for commercial use.
I would also love to hear about a project you are using it.

Have fun,
Martins
----------------------

changelog:

2.4:
- physically accurate DoF simulation calculated from "focalDepth" ,"focalLength", "f-stop" and "CoC" parameters.
- option for artist controlled DoF simulation calculated only from "focalDepth" and individual controls for near and far blur
- added "circe of confusion" (CoC) parameter in mm to accurately simulate DoF with different camera sensor or film sizes
- cleaned up the code
- some optimization

2.3:
- new and physically little more accurate DoF
- two extra input variables - focal length and aperture iris diameter
- added a debug visualization of focus point and focal range

2.1:
- added an option for pentagonal bokeh shape
- minor fixes

2.0:
- variable sample count to increase quality/performance
- option to blur depth buffer to reduce hard edges
- option to dither the samples with noise or pattern
- bokeh chromatic aberration/fringing
- bokeh bias to bring out bokeh edges
- image thresholding to bring out highlights when image is out of focus

*/

uniform sampler2D g_RenderedTexture;
uniform sampler2D g_DepthTexture;
//uniform float bgl_RenderedTextureWidth;
//uniform float bgl_RenderedTextureHeight;

// g_Uniforms[0].xy: RenderedTextureSize;
// g_Uniforms[0].zw: camera range, [znear, zfar]
// g_Uniforms[1].x: focalDepth;
// g_Uniforms[1].y: focalLength;
// g_Uniforms[1].z: fstop
uniform vec4 g_Uniforms[2];

#define PI  3.14159265

float width = g_Uniforms[0].x; //texture width
float height = g_Uniforms[0].y; //texture height

vec2 texel = vec2(1.0/width,1.0/height);

//uniform variables from external script

/*uniform*/ float focalDepth = g_Uniforms[1].x;  //focal distance value in meters, but you may use autofocus option below
/*uniform*/ float focalLength = g_Uniforms[1].y; //focal length in mm
/*uniform*/ float fstop = g_Uniforms[1].z; //f-stop value
/*uniform*/ bool showFocus = false; //show debug focus point and focal range (red = focal point, green = focal range)

/*
make sure that these two values are the same for your camera, otherwise distances will be wrong.
*/

float znear = g_Uniforms[0].z; //camera clipping start
float zfar = g_Uniforms[0].w; //camera clipping end

//------------------------------------------
//user variables

int samples = 3; //samples on the first ring
int rings = 3; //ring count

bool manualdof = false; //manual dof calculation
float ndofstart = 1.0; //near dof blur start
float ndofdist = 2.0; //near dof blur falloff distance
float fdofstart = 1.0; //far dof blur start
float fdofdist = 3.0; //far dof blur falloff distance

float CoC = 0.03;//circle of confusion size in mm (35mm film = 0.03mm)

bool vignetting = true; //use optical lens vignetting?
float vignout = 1.3; //vignetting outer border
float vignin = 0.0; //vignetting inner border
float vignfade = 22.0; //f-stops till vignete fades

bool autofocus = false; //use autofocus in shader? disable if you use external focalDepth value
vec2 focus = vec2(0.5,0.5); // autofocus point on screen (0.0,0.0 - left lower corner, 1.0,1.0 - upper right)
float maxblur = 1.4; //clamp value of max blur (0.0 = no blur,1.0 default)

float threshold = 0.5; //highlight threshold;
float gain = 2.0; //highlight gain;

float bias = 0.5; //bokeh edge bias
float fringe = 0.7; //bokeh chromatic aberration/fringing

bool noise = true; //use noise instead of pattern for sample dithering
float namount = 0.0001; //dither amount

bool depthblur = true; //blur the depth buffer?
float dbsize = 1.25; //depthblursize

/*
next part is experimental
not looking good with small sample and ring count
looks okay starting from samples = 4, rings = 4
*/

bool pentagon = false; //use pentagon as bokeh shape?
float feather = 0.4; //pentagon shape feather

//------------------------------------------


float penta(vec2 coords) //pentagonal shape
{
	float scale = float(rings) - 1.3;
	vec4  HS0 = vec4( 1.0,         0.0,         0.0,  1.0);
	vec4  HS1 = vec4( 0.309016994, 0.951056516, 0.0,  1.0);
	vec4  HS2 = vec4(-0.809016994, 0.587785252, 0.0,  1.0);
	vec4  HS3 = vec4(-0.809016994,-0.587785252, 0.0,  1.0);
	vec4  HS4 = vec4( 0.309016994,-0.951056516, 0.0,  1.0);
	vec4  HS5 = vec4( 0.0        ,0.0         , 1.0,  1.0);

	vec4  one = vec4( 1.0 );

	vec4 P = vec4((coords),vec2(scale, scale));

	vec4 dist = vec4(0.0);
	float inorout = -4.0;

	dist.x = dot( P, HS0 );
	dist.y = dot( P, HS1 );
	dist.z = dot( P, HS2 );
	dist.w = dot( P, HS3 );

	dist = smoothstep( -feather, feather, dist );

	inorout += dot( dist, one );

	dist.x = dot( P, HS4 );
	dist.y = HS5.w - abs( P.z );

	dist = smoothstep( -feather, feather, dist );
	inorout += dist.x;

	return clamp( inorout, 0.0, 1.0 );
}

float bdepth(vec2 coords) //blurring depth
{
	float d = 0.0;
	float kernel[9];
	vec2 offset[9];

	vec2 wh = vec2(texel.x, texel.y) * dbsize;

	offset[0] = vec2(-wh.x,-wh.y);
	offset[1] = vec2( 0.0, -wh.y);
	offset[2] = vec2( wh.x -wh.y);

	offset[3] = vec2(-wh.x,  0.0);
	offset[4] = vec2( 0.0,   0.0);
	offset[5] = vec2( wh.x,  0.0);

	offset[6] = vec2(-wh.x, wh.y);
	offset[7] = vec2( 0.0,  wh.y);
	offset[8] = vec2( wh.x, wh.y);

	kernel[0] = 1.0/16.0;   kernel[1] = 2.0/16.0;   kernel[2] = 1.0/16.0;
	kernel[3] = 2.0/16.0;   kernel[4] = 4.0/16.0;   kernel[5] = 2.0/16.0;
	kernel[6] = 1.0/16.0;   kernel[7] = 2.0/16.0;   kernel[8] = 1.0/16.0;


	for( int i=0; i<9; i++ )
	{
		float tmp = texture(g_DepthTexture, coords + offset[i]).r;
		d += tmp * kernel[i];
	}

	return d;
}


vec3 color(vec2 coords,float blur) //processing the sample
{
	vec3 col = vec3(0.0);

	col.r = texture(g_RenderedTexture,coords + vec2(0.0,1.0)*texel*fringe*blur).r;
	col.g = texture(g_RenderedTexture,coords + vec2(-0.866,-0.5)*texel*fringe*blur).g;
	col.b = texture(g_RenderedTexture,coords + vec2(0.866,-0.5)*texel*fringe*blur).b;

	vec3 lumcoeff = vec3(0.299,0.587,0.114);
	float lum = dot(col.rgb, lumcoeff);
	float thresh = max((lum-threshold)*gain, 0.0);
	return col+mix(vec3(0.0),col,thresh*blur);
}

vec2 rand(vec2 coord) //generating noise/pattern texture for dithering
{
	float noiseX = ((fract(1.0-coord.s*(width/2.0))*0.25)+(fract(coord.t*(height/2.0))*0.75))*2.0-1.0;
	float noiseY = ((fract(1.0-coord.s*(width/2.0))*0.75)+(fract(coord.t*(height/2.0))*0.25))*2.0-1.0;

	if (noise)
	{
		noiseX = clamp(fract(sin(dot(coord ,vec2(12.9898,78.233))) * 43758.5453),0.0,1.0)*2.0-1.0;
		noiseY = clamp(fract(sin(dot(coord ,vec2(12.9898,78.233)*2.0)) * 43758.5453),0.0,1.0)*2.0-1.0;
	}
	return vec2(noiseX,noiseY);
}

vec3 debugFocus(vec3 col, float blur, float depth)
{
	float edge = 0.002*depth; //distance based edge smoothing
	float m = clamp(smoothstep(0.0,edge,blur),0.0,1.0);
	float e = clamp(smoothstep(1.0-edge,1.0,blur),0.0,1.0);

	col = mix(col,vec3(1.0,0.5,0.0),(1.0-m)*0.6);
	col = mix(col,vec3(0.0,0.5,1.0),((1.0-e)-(1.0-m))*0.2);

	return col;
}

float linearize(float depth)
{
	return -zfar * znear / (depth * (zfar - znear) - zfar);
}

float vignette()
{
	float dist = distance(m_f4UVAndScreenPos.xy, vec2(0.5,0.5));
	dist = smoothstep(vignout+(fstop/vignfade), vignin+(fstop/vignfade), dist);
	return clamp(dist,0.0,1.0);
}

void main()
{
	//scene depth calculation

	float depth = linearize(texture(g_DepthTexture,m_f4UVAndScreenPos.xy).x);

	if (depthblur)
	{
		depth = linearize(bdepth(m_f4UVAndScreenPos.xy));
	}

	//focal plane calculation

	float fDepth = focalDepth;

	if (autofocus)
	{
		fDepth = linearize(texture(g_DepthTexture,focus).x);
	}

	//dof blur factor calculation

	float blur = 0.0;

	if (manualdof)
	{
		float a = depth-fDepth; //focal plane
		float b = (a-fdofstart)/fdofdist; //far DoF
		float c = (-a-ndofstart)/ndofdist; //near Dof
		blur = (a>0.0)?b:c;
	}

	else
	{
		float f = focalLength; //focal length in mm
		float d = fDepth*100.0; //focal plane in mm
		float o = depth*100.0; //depth in mm

		float a = (o*f)/(o-f);
		float b = (d*f)/(d-f);
		float c = (d-f)/(d*fstop*CoC);

		blur = abs(a-b)*c;
	}

	blur = clamp(blur,0.0,1.0);

	// calculation of pattern for ditering

	vec2 noise = rand(m_f4UVAndScreenPos.xy)*namount*blur;

	// getting blur x and y step factor

	float w = (1.0/width)*blur*maxblur+noise.x;
	float h = (1.0/height)*blur*maxblur+noise.y;

	// calculation of final color

	vec3 col = vec3(0.0);

	if(blur < 0.05) //some optimization thingy
	{
		col = texture(g_RenderedTexture, m_f4UVAndScreenPos.xy).rgb;
	}

	else
	{
		col = texture(g_RenderedTexture, m_f4UVAndScreenPos.xy).rgb;
		float s = 1.0;
		int ringsamples;

		for (int i = 1; i <= rings; i += 1)
		{
			ringsamples = i * samples;

			for (int j = 0 ; j < ringsamples ; j += 1)
			{
				float step = PI*2.0 / float(ringsamples);
				float pw = (cos(float(j)*step)*float(i));
				float ph = (sin(float(j)*step)*float(i));
				float p = 1.0;
				if (pentagon)
				{
					p = penta(vec2(pw,ph));
				}
				col += color(m_f4UVAndScreenPos.xy + vec2(pw*w,ph*h),blur)*mix(1.0,(float(i))/(float(rings)),bias)*p;
				s += 1.0*mix(1.0,(float(i))/(float(rings)),bias)*p;
			}
		}
		col /= s; //divide by sample count
	}

	if (showFocus)
	{
		col = debugFocus(col, blur, depth);
	}

	if (vignetting)
	{
		col *= vignette();
	}

	Out_f4Color.rgb = col;
	Out_f4Color.a = 1.0;

//    Out_f4Color = vec4(1, 0.8, 0.5, 1);
}