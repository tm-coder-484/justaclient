#version 330

// Composites the first-person hand, drawn on its own (premultiplied) target, back over the frame with a glow and an
// optional colour style. See HandFx.

uniform sampler2D HandSampler;

layout(std140) uniform JacHand {
    vec4 GlowColor;  // rgb, strength
    vec4 Accent2;    // second gradient stop rgb, style amount
    vec4 Params;     // style (0 none, 1 gradient, 2 chroma, 3 outline, 4 invert, 5 glass, 6 smoke, 7 snow, 8 stripes),
                     // glow radius px, time, outline px
};

in vec2 texCoord;

out vec4 fragColor;

vec3 hue(float h) {
    vec3 k = vec3(1.0, 2.0 / 3.0, 1.0 / 3.0);
    return clamp(abs(fract(h + k) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

// Average coverage of the hand in a ring of the given radius, for a soft outer glow.
float ring(vec2 uv, vec2 texel, float radius) {
    float sum = 0.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235988;
        sum += texture(HandSampler, uv + vec2(cos(a), sin(a)) * texel * radius).a;
    }
    return sum / 12.0;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(HandSampler, 0));
    vec4 hand = texture(HandSampler, texCoord);
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
    } else if (style == 8) {
        // Diagonal accent stripes sliding across the hand.
        vec2 px = texCoord / texel;
        float band = step(0.5, fract((px.x + px.y) / 28.0 - Params.z * 0.6));
        vec3 c = mix(GlowColor.rgb, Accent2.rgb, band);
        rgb = mix(rgb, c * (0.4 + l * 1.0), amount * (0.55 + 0.45 * band));
    } else if (style == 7) {
        // Icy tint with twinkling flakes drifting down.
        vec2 px = texCoord / texel;
        vec2 cell = floor((px + vec2(0.0, Params.z * 40.0)) / 6.0);
        float flake = step(0.93, hash(cell)) * (0.6 + 0.4 * sin(Params.z * 6.0 + hash(cell + 3.1) * 40.0));
        vec3 ice = mix(vec3(0.75, 0.88, 1.0), GlowColor.rgb, 0.25) * (0.45 + l * 0.9);
        rgb = mix(rgb, ice, amount * 0.8) + vec3(flake) * amount;
    } else if (style == 6) {
        // Smoke: slow rising wisps in the accent colour over a darkened hand.
        vec2 p = texCoord * vec2(6.0, 4.0) + vec2(0.0, Params.z * 0.35);
        float n = fbm(p + fbm(p * 1.7 - Params.z * 0.2));
        vec3 smoke = mix(vec3(0.05), GlowColor.rgb, smoothstep(0.35, 0.85, n));
        rgb = mix(rgb, smoke * (0.5 + l), amount);
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

    if (style == 5) {
        // Glass: see-through body, bright rim where the hand's coverage falls off, a soft sheen across it.
        float inner = ring(texCoord, texel, 3.0);
        float rim = clamp((1.0 - inner) * 2.5, 0.0, 1.0) * hand.a;
        float sheen = smoothstep(0.0, 0.08, 0.08 - abs(fract(texCoord.x * 0.8 - texCoord.y * 0.5 + Params.z * 0.08) - 0.5));
        vec3 tint = mix(vec3(1.0), GlowColor.rgb, 0.35);
        rgb = mix(rgb, tint * (0.55 + l * 0.6), amount) + vec3(rim * 0.8 + sheen * 0.35) * amount;
        alpha = hand.a * mix(1.0, 0.35 + rim * 0.65, amount);
    }

    vec3 premul = rgb * alpha;
    float g = glow * (1.0 - alpha);
    fragColor = vec4(premul + GlowColor.rgb * g, alpha + g);
}
