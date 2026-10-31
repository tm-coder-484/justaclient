#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <justaclient:sdf.glsl>

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in float radius;
flat in float feather;
flat in float lineWidth;

out vec4 fragColor;

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
