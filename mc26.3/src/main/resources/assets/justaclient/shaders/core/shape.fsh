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

void main() {
    float d = sd_round_box(localPos, halfSize, radius);
    float coverage;
    if (feather > 0.0) {
        // Shadow / glow: smooth falloff centred on the edge.
        coverage = 1.0 - smoothstep(-feather * 0.5, feather, d);
        coverage *= coverage;
    } else if (lineWidth > 0.0) {
        float stroke = abs(d + lineWidth * 0.5) - lineWidth * 0.5;
        coverage = aa_coverage(stroke);
    } else {
        coverage = aa_coverage(d);
    }
    float alpha = vertexColor.a * coverage;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
