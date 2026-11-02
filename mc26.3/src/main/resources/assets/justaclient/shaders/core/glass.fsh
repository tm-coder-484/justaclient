#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <justaclient:sdf.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 localPos;
layout(location = 2) flat in vec2 halfSize;
layout(location = 3) flat in float radius;
layout(location = 4) flat in float feather;
layout(location = 5) flat in float lineWidth;

layout(location = 0) out vec4 fragColor;

void main() {
    float coverage = aa_coverage(sd_round_box(localPos, halfSize, radius));
    // lineWidth carries the panel's overall opacity for glass (see Draw.glass).
    float alpha = coverage * lineWidth;
    if (alpha <= 0.0) {
        discard;
    }
    vec3 behind = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    // Tiny ordered dither hides banding in dark, heavily blurred gradients.
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    vec3 color = mix(behind, vertexColor.rgb, vertexColor.a) + noise / 255.0;
    fragColor = vec4(color, alpha) * ColorModulator;
}
