#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec3 msd = texture(Sampler0, texCoord0).rgb;
    float sd = median(msd.r, msd.g, msd.b);
    // How many screen pixels one unit of the distance range covers at this draw size.
    vec2 unitRange = vec2(PX_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float opacity = clamp(screenPxRange * (sd - 0.5) + 0.5, 0.0, 1.0);
    float alpha = vertexColor.a * opacity;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
