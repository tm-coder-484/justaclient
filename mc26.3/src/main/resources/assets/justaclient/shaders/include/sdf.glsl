// Signed distance to a rounded box centred on the origin. Negative inside.
float sd_round_box(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// One-pixel anti-aliased coverage from a distance, in screen pixels whatever the pose scale.
float aa_coverage(float d) {
    float w = max(fwidth(d), 1e-4);
    return clamp(0.5 - d / w, 0.0, 1.0);
}
