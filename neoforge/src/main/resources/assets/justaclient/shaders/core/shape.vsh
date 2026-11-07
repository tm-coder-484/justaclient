#version 330

uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform mat4 ProjMat;

in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform vec2 ShapeSize;
uniform vec2 ShapeEdge;
uniform float LineWidth;

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
    halfSize = ShapeSize;
    radius = ShapeEdge.x;
    feather = ShapeEdge.y;
    lineWidth = LineWidth;
}
