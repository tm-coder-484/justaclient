#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <justaclient:sdf.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in float radius;
flat in float feather;
flat in float lineWidth;

out vec4 fragColor;

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
