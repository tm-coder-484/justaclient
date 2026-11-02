#version 330

// Color grading, vignette and motion blur over the finished world frame (see PostFx).

uniform sampler2D InSampler;
uniform sampler2D HistorySampler;

layout(std140) uniform JacPost {
    vec4 Grade;         // exposure (stops), contrast, saturation, vibrance
    vec4 Balance;       // temperature, tint, vignette strength, vignette size
    vec4 Motion;        // blend with last frame, history valid, grading on, vignette on
    vec4 VignetteColor; // rgb, unused
};

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;

    if (Motion.y > 0.5 && Motion.x > 0.0) {
        color = mix(color, texture(HistorySampler, texCoord).rgb, Motion.x);
    }

    if (Motion.z > 0.5) {
        color *= exp2(Grade.x);
        // Warm/cool and green/magenta shifts, kept roughly luminance neutral.
        color *= vec3(1.0 + Balance.x * 0.10, 1.0 - Balance.y * 0.08, 1.0 - Balance.x * 0.10);
        color = (color - 0.5) * Grade.y + 0.5;
        float l = luma(color);
        color = mix(vec3(l), color, Grade.z);
        // Vibrance lifts muted colours more than already saturated ones.
        float maxC = max(color.r, max(color.g, color.b));
        float minC = min(color.r, min(color.g, color.b));
        float sat = maxC - minC;
        color = mix(vec3(luma(color)), color, 1.0 + Grade.w * (1.0 - sat));
    }

    if (Motion.w > 0.5 && Balance.z > 0.0) {
        vec2 d = texCoord - 0.5;
        float dist = length(d * vec2(1.0, 0.85)) * 1.414;
        float edge = smoothstep(Balance.w, Balance.w + 0.55, dist);
        color = mix(color, VignetteColor.rgb, edge * Balance.z);
    }

    // A little dither so heavy grading does not band in skies and fog.
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    fragColor = vec4(clamp(color, 0.0, 1.0) + noise / 255.0, 1.0);
}
