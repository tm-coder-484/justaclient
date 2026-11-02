#version 330
#extension GL_ARB_separate_shader_objects : require
// Dual Kawase downsample (Marius Bjørge, "Bandwidth-Efficient Rendering", SIGGRAPH 2015).

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    // Offset by one source texel (half a destination texel) for a wider kernel per pass.
    vec2 halfTexel = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord - halfTexel).rgb;
    sum += texture(InSampler, texCoord + halfTexel).rgb;
    sum += texture(InSampler, texCoord + vec2(halfTexel.x, -halfTexel.y)).rgb;
    sum += texture(InSampler, texCoord - vec2(halfTexel.x, -halfTexel.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
