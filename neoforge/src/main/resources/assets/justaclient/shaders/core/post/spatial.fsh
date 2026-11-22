#version 330

// Spatial menus: lays the menu, drawn flat into GuiSampler, over the frame on a panel in front of the camera. Each
// pixel's view ray is traced onto the panel (flat, or bent around a vertical axis towards the viewer). The same maths
// is in common/spatial/SpatialPanel.java, which maps the mouse back.

uniform sampler2D GuiSampler;


    uniform mat4 InvProj;
    uniform vec4 Center; // camera space, w: bend radius (0 flat)
    uniform vec4 Right;  // w: half width
    uniform vec4 Up;     // w: half height
    uniform vec4 Normal; // towards the viewer, w: opacity


in vec2 texCoord;

out vec4 fragColor;

// Panel (u, v) in -1..1 for the view ray d, or a value outside that range when it misses.
vec2 trace(vec3 d) {
    if (Center.w > 0.0) {
        float r = Center.w;
        vec3 axis = Center.xyz + Normal.xyz * r;
        float oa = -dot(axis, Right.xyz);
        float ob = -dot(axis, Normal.xyz);
        float oy = -dot(axis, Up.xyz);
        float da = dot(d, Right.xyz);
        float db = dot(d, Normal.xyz);
        float dy = dot(d, Up.xyz);
        float qa = da * da + db * db;
        float qb = 2.0 * (oa * da + ob * db);
        float qc = oa * oa + ob * ob - r * r;
        float disc = qb * qb - 4.0 * qa * qc;
        if (qa < 1e-9 || disc < 0.0) {
            return vec2(9.0);
        }
        float s = sqrt(disc);
        for (int i = 0; i < 2; i++) {
            // Veil rewrites shader ASTs; avoid a ternary inside arithmetic.
            float root = -s;
            if (i != 0) root = s;
            float t = (-qb + root) / (2.0 * qa);
            if (t <= 0.0) {
                continue;
            }
            float a = oa + t * da;
            float b = ob + t * db;
            vec2 uv = vec2(r * atan(a, -b) / Right.w, (oy + t * dy) / Up.w);
            if (abs(uv.x) <= 1.0 && abs(uv.y) <= 1.0) {
                return uv;
            }
        }
        return vec2(9.0);
    }
    float denom = dot(d, Normal.xyz);
    if (abs(denom) < 1e-6) {
        return vec2(9.0);
    }
    float t = dot(Center.xyz, Normal.xyz) / denom;
    if (t <= 0.0) {
        return vec2(9.0);
    }
    vec3 local = d * t - Center.xyz;
    return vec2(dot(local, Right.xyz) / Right.w, dot(local, Up.xyz) / Up.w);
}

void main() {
    vec4 p = InvProj * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    vec2 uv = trace(normalize(p.xyz / p.w));
    vec2 tc = uv * 0.5 + 0.5;
    // Four taps across this pixel's footprint keep text smooth where the panel is smaller than the window.
    vec2 dx = clamp(dFdx(tc), -0.01, 0.01) * 0.25;
    vec2 dy = clamp(dFdy(tc), -0.01, 0.01) * 0.25;
    if (abs(uv.x) > 1.0 || abs(uv.y) > 1.0) {
        discard;
    }
    vec4 c = (texture(GuiSampler, tc + dx + dy) + texture(GuiSampler, tc - dx + dy) + texture(GuiSampler, tc + dx - dy)
        + texture(GuiSampler, tc - dx - dy)) * 0.25;
    fragColor = c * Normal.w;
}
