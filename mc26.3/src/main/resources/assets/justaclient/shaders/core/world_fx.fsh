#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>

// Soft glowing sprites, rings and ribbons for world effects. UV runs -1..1 from the shape's centre line to its edge:
// sprites use both axes (a disc), strips leave x at 0 so only the distance across the strip matters.

layout(location = 0) in vec2 uv;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    if (uv.y > 2.0) {
        // Bubble: clear middle, coloured rim, a small white highlight up and to the left.
        vec2 b = vec2(uv.x, uv.y - 4.0);
        float r = length(b);
        if (r >= 1.0) {
            discard;
        }
        float rim = smoothstep(0.6, 0.93, r) * (1.0 - smoothstep(0.93, 1.0, r));
        float shine = 1.0 - smoothstep(0.0, 0.22, length(b - vec2(-0.38, 0.38)));
        vec3 rgb = mix(vertexColor.rgb, vec3(1.0), shine);
        float alpha = vertexColor.a * clamp(0.08 + rim * 0.9 + shine * 0.9, 0.0, 1.0);
        fragColor = vec4(rgb, alpha) * ColorModulator;
        return;
    }
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
