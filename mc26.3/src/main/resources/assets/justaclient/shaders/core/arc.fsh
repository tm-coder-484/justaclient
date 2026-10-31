#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <justaclient:sdf.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 localPos;
layout(location = 2) flat in vec2 halfSize;
layout(location = 3) flat in float radius;
layout(location = 4) flat in float feather;
layout(location = 5) flat in float lineWidth;

layout(location = 0) out vec4 fragColor;

// Must match ArcRenderState.ANGLE_FIXED and JacPipelines.FIXED (shape.vsh divides UV2 by FIXED).
const float ANGLE_FIXED = 8000.0;
const float FIXED = 4.0;
const float TAU = 6.28318530718;

vec2 on_ring(float turns, float r) {
    float a = turns * TAU;
    return vec2(sin(a), -cos(a)) * r;
}

void main() {
    float start = radius * FIXED / ANGLE_FIXED;
    float sweep = feather * FIXED / ANGLE_FIXED;
    float mid = halfSize.x - lineWidth * 0.5;
    float half_w = lineWidth * 0.5;
    float d;
    // Angle clockwise from 12 o'clock in turns (GUI y points down).
    float turns = fract(atan(localPos.x, -localPos.y) / TAU - start);
    if (sweep >= 0.999 || turns <= sweep) {
        d = abs(length(localPos) - mid) - half_w;
    } else {
        // Outside the swept range: distance to the round caps.
        float c0 = length(localPos - on_ring(start, mid));
        float c1 = length(localPos - on_ring(start + sweep, mid));
        d = min(c0, c1) - half_w;
    }
    float alpha = vertexColor.a * aa_coverage(d);
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
