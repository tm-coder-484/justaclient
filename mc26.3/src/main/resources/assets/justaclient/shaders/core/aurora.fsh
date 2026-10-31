#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>

// Aurora curtain. UV.x is the position along the band (already scrolled over time on the CPU), UV.y runs 0 at the
// bottom edge to 1 at the top. Vertical rays come from a few sines at unrelated frequencies.

layout(location = 0) in vec2 uv;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float rays = 0.55 + 0.45 * sin(uv.x * 9.0) * sin(uv.x * 3.7 + 1.3);
    float fine = 0.7 + 0.3 * sin(uv.x * 41.0 + sin(uv.x * 5.0) * 2.0);
    float bottom = smoothstep(0.0, 0.08, uv.y);
    float top = pow(1.0 - clamp(uv.y, 0.0, 1.0), 1.4);
    float alpha = min(1.0, vertexColor.a * rays * fine * bottom * top * 1.6);
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
