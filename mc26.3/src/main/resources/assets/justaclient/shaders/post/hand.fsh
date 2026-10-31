#version 330
#extension GL_ARB_separate_shader_objects : require
// Composites the first-person hand, drawn on its own (premultiplied) target, back over the frame with a glow and an
// optional colour style. See HandFx.

uniform sampler2D HandSampler;
// The hand pass can leave alpha at 1 across the whole target (26.x composites some features full-screen), so coverage
// comes from depth: only pixels the hand actually drew have depth above the reversed-Z clear value of 0.
uniform sampler2D HandDepthSampler;

layout(std140) uniform JacHand {
    vec4 GlowColor;  // rgb, strength
    vec4 Accent2;    // second gradient stop rgb, style amount
    vec4 Params;     // style (0 none, 1 gradient, 2 chroma, 3 outline, 4 invert), glow radius px, time, outline px
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

vec3 hue(float h) {
    vec3 k = vec3(1.0, 2.0 / 3.0, 1.0 / 3.0);
    return clamp(abs(fract(h + k) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

float coverage(vec2 uv) {
    return texture(HandDepthSampler, uv).r > 0.0 ? texture(HandSampler, uv).a : 0.0;
}

// Average coverage of the hand in a ring of the given radius, for a soft outer glow.
float ring(vec2 uv, vec2 texel, float radius) {
    float sum = 0.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235988;
        sum += coverage(uv + vec2(cos(a), sin(a)) * texel * radius);
    }
    return sum / 12.0;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(HandSampler, 0));
    vec4 hand = texture(HandSampler, texCoord);
    hand.a = coverage(texCoord);
    int style = int(Params.x + 0.5);
    float amount = Accent2.a;

    vec3 rgb = hand.a > 0.0 ? hand.rgb / hand.a : vec3(0.0);
    float l = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    if (style == 1) {
        vec3 grad = mix(GlowColor.rgb, Accent2.rgb, clamp(texCoord.x * 1.4 - 0.2 + texCoord.y * 0.3, 0.0, 1.0));
        rgb = mix(rgb, grad * (0.35 + l * 1.1), amount);
    } else if (style == 2) {
        vec3 c = hue(texCoord.x * 0.6 + texCoord.y * 0.3 + Params.z * 0.15);
        rgb = mix(rgb, c * (0.35 + l * 1.1), amount);
    } else if (style == 4) {
        rgb = mix(rgb, 1.0 - rgb, amount);
    }

    float glow = 0.0;
    if (GlowColor.a > 0.0 && Params.y > 0.0) {
        glow = ring(texCoord, texel, Params.y * 0.35) * 0.5 + ring(texCoord, texel, Params.y * 0.7) * 0.32
            + ring(texCoord, texel, Params.y) * 0.18;
        glow = clamp(glow * 2.6, 0.0, 1.0) * GlowColor.a;
    }

    float alpha = hand.a;
    if (style == 3) {
        // Outline: a crisp accent edge around the hand, with the hand itself faded by the style amount.
        float edge = clamp(ring(texCoord, texel, Params.w) * 2.0, 0.0, 1.0) * (1.0 - hand.a);
        alpha = hand.a * (1.0 - amount);
        vec3 premul = rgb * alpha;
        float outA = alpha + edge * (1.0 - alpha);
        premul += GlowColor.rgb * edge * (1.0 - alpha);
        float g = glow * (1.0 - outA);
        fragColor = vec4(premul + GlowColor.rgb * g, outA + g);
        return;
    }

    vec3 premul = rgb * alpha;
    float g = glow * (1.0 - alpha);
    fragColor = vec4(premul + GlowColor.rgb * g, alpha + g);
}
