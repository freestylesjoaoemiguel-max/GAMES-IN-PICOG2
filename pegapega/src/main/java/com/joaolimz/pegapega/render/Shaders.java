package com.joaolimz.pegapega.render;

/** GLSL ES 3.00 sources. Everything is per-vertex lit and fogged to keep fragment cost minimal. */
final class Shaders {
    private Shaders() { }

    /** Dynamic objects: per-vertex directional + ambient lighting, linear fog. */
    static final String LIT_VS =
            "#version 300 es\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec3 aNormal;\n" +
            "layout(location=2) in vec4 aColor;\n" +
            "uniform mat4 uVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uLightDir;\n" +
            "uniform vec3 uLightColor;\n" +
            "uniform vec3 uAmbient;\n" +
            "uniform vec4 uTint;\n" +
            "uniform vec3 uEye;\n" +
            "uniform vec2 uFog;\n" +
            "out vec4 vColor;\n" +
            "out float vFog;\n" +
            "void main(){\n" +
            "  vec4 wp = uModel * vec4(aPos,1.0);\n" +
            "  vec3 n = normalize(mat3(uModel) * aNormal);\n" +
            "  float d = max(dot(n, uLightDir), 0.0);\n" +
            "  float hemi = 0.8 + 0.2 * n.y;\n" +
            "  vec3 light = uAmbient * hemi + uLightColor * d;\n" +
            "  vColor = vec4(aColor.rgb * light, aColor.a) * uTint;\n" +
            "  vFog = clamp((distance(wp.xyz, uEye) - uFog.x) / max(uFog.y - uFog.x, 0.001), 0.0, 1.0);\n" +
            "  gl_Position = uVP * wp;\n" +
            "}\n";

    /** Static level geometry: lighting already baked in vertex colors. Optional detail texture. */
    static final String BAKED_VS =
            "#version 300 es\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec3 aNormal;\n" +
            "layout(location=2) in vec4 aColor;\n" +
            "uniform mat4 uVP;\n" +
            "uniform vec3 uEye;\n" +
            "uniform vec2 uFog;\n" +
            "out vec4 vColor;\n" +
            "out float vFog;\n" +
            "out vec2 vUV;\n" +
            "void main(){\n" +
            "  vColor = aColor;\n" +
            "  vec3 an = abs(aNormal);\n" +
            "  vUV = (an.y > 0.5 ? aPos.xz : (an.x > an.z ? aPos.zy : aPos.xy)) * 0.5;\n" +
            "  vFog = clamp((distance(aPos, uEye) - uFog.x) / max(uFog.y - uFog.x, 0.001), 0.0, 1.0);\n" +
            "  gl_Position = uVP * vec4(aPos, 1.0);\n" +
            "}\n";

    static final String BAKED_FS =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec4 vColor;\n" +
            "in float vFog;\n" +
            "in vec2 vUV;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform sampler2D uDetail;\n" +
            "uniform float uDetailAmount;\n" +
            "out vec4 oColor;\n" +
            "void main(){\n" +
            "  vec3 c = vColor.rgb;\n" +
            "  if (uDetailAmount > 0.0) c *= mix(1.0, texture(uDetail, vUV).r * 2.0, uDetailAmount);\n" +
            "  oColor = vec4(mix(c, uFogColor, vFog), vColor.a);\n" +
            "}\n";

    static final String COLOR_FS =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec4 vColor;\n" +
            "in float vFog;\n" +
            "uniform vec3 uFogColor;\n" +
            "out vec4 oColor;\n" +
            "void main(){ oColor = vec4(mix(vColor.rgb, uFogColor, vFog), vColor.a); }\n";

    /** Unlit (effects, UI quads, sky). */
    static final String UNLIT_VS =
            "#version 300 es\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=2) in vec4 aColor;\n" +
            "uniform mat4 uVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec4 uTint;\n" +
            "uniform vec3 uEye;\n" +
            "uniform vec2 uFog;\n" +
            "out vec4 vColor;\n" +
            "out float vFog;\n" +
            "void main(){\n" +
            "  vec4 wp = uModel * vec4(aPos,1.0);\n" +
            "  vColor = aColor * uTint;\n" +
            "  vFog = clamp((distance(wp.xyz, uEye) - uFog.x) / max(uFog.y - uFog.x, 0.001), 0.0, 1.0);\n" +
            "  gl_Position = uVP * wp;\n" +
            "}\n";

    /** Text: world-space quads generated on the CPU. */
    static final String TEXT_VS =
            "#version 300 es\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec2 aUV;\n" +
            "layout(location=2) in vec4 aColor;\n" +
            "uniform mat4 uVP;\n" +
            "out vec2 vUV;\n" +
            "out vec4 vColor;\n" +
            "void main(){ vUV = aUV; vColor = aColor; gl_Position = uVP * vec4(aPos,1.0); }\n";

    static final String TEXT_FS =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec2 vUV;\n" +
            "in vec4 vColor;\n" +
            "uniform sampler2D uFont;\n" +
            "out vec4 oColor;\n" +
            "void main(){\n" +
            "  float a = texture(uFont, vUV).a;\n" +
            "  if (a < 0.05) discard;\n" +
            "  oColor = vec4(vColor.rgb, vColor.a * a);\n" +
            "}\n";

    /** Lens pre-distortion + comfort vignette + damage flash + fade, in one cheap pass. */
    static final String DISTORT_VS =
            "#version 300 es\n" +
            "layout(location=0) in vec2 aPos;\n" +
            "layout(location=1) in vec2 aUV;\n" +
            "layout(location=2) in float aRadius;\n" +
            "uniform vec2 uFlip;\n" +
            "out vec2 vUV;\n" +
            "out float vR;\n" +
            "void main(){ vUV = aUV; vR = aRadius; gl_Position = vec4(aPos * uFlip, 0.0, 1.0); }\n";

    static final String DISTORT_FS =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec2 vUV;\n" +
            "in float vR;\n" +
            "uniform sampler2D uEyeTex;\n" +
            "uniform vec4 uUVRect;\n" +
            "uniform float uVignette;\n" +
            "uniform float uDamage;\n" +
            "uniform float uFade;\n" +
            "out vec4 oColor;\n" +
            "void main(){\n" +
            "  if (vUV.x < 0.0 || vUV.x > 1.0 || vUV.y < 0.0 || vUV.y > 1.0) { oColor = vec4(0.0,0.0,0.0,1.0); return; }\n" +
            "  vec3 c = texture(uEyeTex, uUVRect.xy + vUV * uUVRect.zw).rgb;\n" +
            "  float edge = smoothstep(0.35, 0.95, vR);\n" +
            "  c = mix(c, vec3(0.55, 0.0, 0.0), uDamage * edge);\n" +
            "  float vig = 1.0 - smoothstep(1.0 - uVignette * 0.75, 1.15 - uVignette * 0.55, vR) * min(uVignette * 1.4, 1.0);\n" +
            "  c *= vig * (1.0 - uFade);\n" +
            "  float lensEdge = 1.0 - smoothstep(0.97, 1.0, vR);\n" +
            "  oColor = vec4(c * lensEdge, 1.0);\n" +
            "}\n";
}
