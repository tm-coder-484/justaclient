#version 330

// Dual Kawase upsample (Marius Bjørge, "Bandwidth-Efficient Rendering", SIGGRAPH 2015).

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 halfTexel = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord + vec2(-halfTexel.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-halfTexel.x, halfTexel.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, halfTexel.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(halfTexel.x, halfTexel.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(halfTexel.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(halfTexel.x, -halfTexel.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -halfTexel.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-halfTexel.x, -halfTexel.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}
