#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>

// Soft glowing sprites, rings and ribbons for world effects. UV runs -1..1 from the shape's centre line to its edge:
// sprites use both axes (a disc), strips leave x at 0 so only the distance across the strip matters.

layout(location = 0) in vec2 uv;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float d = length(uv);
    if (d >= 1.0) {
        discard;
    }
    float halo = 1.0 - d;
    halo *= halo;
    float core = 1.0 - smoothstep(0.0, 0.38, d);
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), core * 0.5);
    float alpha = vertexColor.a * clamp(halo * 1.8 + core * 0.8, 0.0, 1.0);
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
