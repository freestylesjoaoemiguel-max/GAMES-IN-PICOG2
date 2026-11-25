shader_type canvas_item;
// Approximate visual for a generic VR Box; not a lens profile/calibration.
uniform float curvature : hint_range(0.0, 0.3) = 0.12;
void fragment() {
 vec2 p = UV * 2.0 - vec2(1.0);
 float ax = abs(p.x);
 float ay = abs(p.y);
 // Tapered, rounded edges and a black field between/around both eyes.
 float edge = max(ax - (0.94 - 0.14 * ay * ay), ay - (0.93 - 0.11 * ax * ax));
 float window = 1.0 - smoothstep(-0.009, 0.009, edge);
 float radius2 = dot(p, p);
 vec2 source_uv = (p * (1.0 + curvature * radius2) * 0.85) * 0.5 + 0.5;
 float inside = step(0.0, source_uv.x) * step(source_uv.x, 1.0) * step(0.0, source_uv.y) * step(source_uv.y, 1.0);
 vec4 eye = texture(TEXTURE, clamp(source_uv, vec2(0.0), vec2(1.0)));
 COLOR = vec4(eye.rgb * window * inside, 1.0);
}
