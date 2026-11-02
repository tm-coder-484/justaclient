#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in float LineWidth;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 localPos;
layout(location = 2) flat out vec2 halfSize;
layout(location = 3) flat out float radius;
layout(location = 4) flat out float feather;
layout(location = 5) flat out float lineWidth;

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
