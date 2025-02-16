#include "PostProcessingCommonPS.frag"

// x:BloomThreshold, y:ExposureScale (useful if eyeadaptation is locked)
uniform vec2 f2BloomThreshold;
uniform sampler2D g_Texture;

#define threshold f2BloomThreshold.x
#define scalar    f2BloomThreshold.y


void main()
{
    vec4 sceneColor = texture(g_Texture, m_f4UVAndScreenPos.xy);
	Out_f4Color = max((sceneColor - threshold)*scalar, vec4(0.0,0.0,0.0,0.0));
}