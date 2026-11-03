package vithh.vithOptimized.render.framegen;

public final class FrameGenShaders {

    private FrameGenShaders() {}

    public static final String VERTEX_SCREEN_QUAD = """
        #version 150
        in vec2 in_pos;
        in vec2 in_uv;
        out vec2 v_uv;

        void main() {
            v_uv = in_uv;
            gl_Position = vec4(in_pos, 0.0, 1.0);
        }
        """;

    public static final String OPTICAL_FLOW_FRAG = """
        #version 150
        in vec2 v_uv;
        out vec4 fragColor;

        uniform sampler2D u_currTex;
        uniform sampler2D u_prevTex;
        uniform sampler2D u_depthTex;

        uniform mat4 u_invViewProjCurr;
        uniform mat4 u_viewProjPrev;
        uniform vec2 u_texelSize;
        uniform int u_hasDepth;

        float luminance(vec3 c) {
            return dot(c, vec3(0.299, 0.587, 0.114));
        }

        void main() {
            vec2 flow = vec2(0.0);

            if (u_hasDepth == 1) {
                float depth = texture(u_depthTex, v_uv).r;
                if (depth > 0.0001 && depth < 0.9999) {
                    vec4 clipPos = vec4(v_uv * 2.0 - 1.0, depth, 1.0);
                    vec4 camRelPos = u_invViewProjCurr * clipPos;

                    if (abs(camRelPos.w) > 1e-6) {
                        camRelPos /= camRelPos.w;
                        vec4 prevClip = u_viewProjPrev * camRelPos;

                        if (abs(prevClip.w) > 1e-6) {
                            vec2 prevUv = (prevClip.xy / prevClip.w) * 0.5 + 0.5;
                            vec2 geoFlow = v_uv - prevUv;
                            if (length(geoFlow) < 0.20) {
                                flow = geoFlow;
                            }
                        }
                    }
                }
            }

            vec2 offsetUv = clamp(v_uv - flow, 0.001, 0.999);
            float lumC = luminance(texture(u_currTex, v_uv).rgb);
            float lumP = luminance(texture(u_prevTex, offsetUv).rgb);

            vec2 step = u_texelSize * 2.0;
            float lumR = luminance(texture(u_currTex, clamp(v_uv + vec2(step.x, 0.0), 0.0, 1.0)).rgb);
            float lumL = luminance(texture(u_currTex, clamp(v_uv - vec2(step.x, 0.0), 0.0, 1.0)).rgb);
            float lumT = luminance(texture(u_currTex, clamp(v_uv + vec2(0.0, step.y), 0.0, 1.0)).rgb);
            float lumB = luminance(texture(u_currTex, clamp(v_uv - vec2(0.0, step.y), 0.0, 1.0)).rgb);

            vec2 grad = vec2(lumR - lumL, lumT - lumB);
            float gradMagSq = dot(grad, grad) + 0.0002;
            float temporalDiff = lumC - lumP;

            vec2 residual = - (temporalDiff * grad) / gradMagSq * (u_texelSize * 1.8);
            residual = clamp(residual, vec2(-0.03), vec2(0.03));
            flow += residual;

            if (isnan(flow.x) || isnan(flow.y)) {
                flow = vec2(0.0);
            }

            fragColor = vec4(flow.x, flow.y, 0.0, 1.0);
        }
        """;

    public static final String FRAME_GEN_SYNTHESIS_FRAG = """
        #version 150
        in vec2 v_uv;
        out vec4 fragColor;

        uniform sampler2D u_currTex;
        uniform sampler2D u_prevTex;
        uniform sampler2D u_flowTex;

        uniform vec2 u_texelSize;
        uniform float u_blendFactor;
        uniform float u_sharpness;
        uniform int u_shieldDisocclusion;

        float luminance(vec3 c) {
            return dot(c, vec3(0.299, 0.587, 0.114));
        }

        void main() {
            vec2 flow = texture(u_flowTex, v_uv).xy;
            if (isnan(flow.x) || isnan(flow.y)) {
                flow = vec2(0.0);
            }

            float mag = length(flow);
            if (mag > 0.12) {
                flow = (flow / mag) * 0.12;
            }

            vec2 uvCurr = clamp(v_uv - (1.0 - u_blendFactor) * flow, 0.001, 0.999);
            vec2 uvPrev = clamp(v_uv + u_blendFactor * flow, 0.001, 0.999);

            vec4 colCurr = texture(u_currTex, uvCurr);
            vec4 colPrev = texture(u_prevTex, uvPrev);

            float error = dot(abs(colCurr.rgb - colPrev.rgb), vec3(0.299, 0.587, 0.114));
            float confidence = 1.0;
            if (u_shieldDisocclusion == 1) {
                confidence = clamp(1.0 - smoothstep(0.12, 0.32, error), 0.0, 1.0);
            }

            vec3 blended = mix(colCurr.rgb, mix(colPrev.rgb, colCurr.rgb, u_blendFactor), confidence);

            if (u_sharpness > 0.02) {
                vec3 nN = texture(u_currTex, uvCurr + vec2(0.0, u_texelSize.y)).rgb;
                vec3 nS = texture(u_currTex, uvCurr - vec2(0.0, u_texelSize.y)).rgb;
                vec3 nE = texture(u_currTex, uvCurr + vec2(u_texelSize.x, 0.0)).rgb;
                vec3 nW = texture(u_currTex, uvCurr - vec2(u_texelSize.x, 0.0)).rgb;

                vec3 minCol = min(min(min(min(colCurr.rgb, nN), nS), nE), nW);
                vec3 maxCol = max(max(max(max(colCurr.rgb, nN), nS), nE), nW);

                vec3 laplacian = blended - (nN + nS + nE + nW) * 0.25;
                vec3 sharpened = blended + laplacian * (0.32 * u_sharpness);
                blended = clamp(sharpened, minCol, maxCol);
            }

            fragColor = vec4(blended, 1.0);
        }
        """;
}
