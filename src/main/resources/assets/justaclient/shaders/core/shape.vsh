#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in float LineWidth;

out vec4 vertexColor;
out vec2 localPos;
flat out vec2 halfSize;
flat out float radius;
flat out float feather;
flat out float lineWidth;

// Must match JacPipelines.FIXED.
const float FIXED = 4.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    localPos = UV0;
    halfSize = vec2(UV1) / FIXED;
    radius = float(UV2.x) / FIXED;
    feather = float(UV2.y) / FIXED;
    lineWidth = LineWidth;
}
