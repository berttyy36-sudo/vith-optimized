package vithh.vithOptimized.render.framegen;

import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.nativeopt.NativeAccelerator;
import vithh.vithOptimized.render.IMinecraftAccessor;
import vithh.vithOptimized.render.RenderResetHelper;
import vithh.vithOptimized.render.shader.QuadHelper;
import vithh.vithOptimized.render.shader.Shader;

import java.nio.ByteBuffer;

public final class UniversalFrameGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("VithOptimized-FrameGen");
    private static final Minecraft mc = Minecraft.getInstance();

    private static int prevTex = -1;
    private static int flowTex = -1;
    private static int flowFbo = -1;
    private static int copyReadFbo = -1;
    private static int copyDrawFbo = -1;

    private static int allocatedWidth = 0;
    private static int allocatedHeight = 0;

    private static Shader opticalFlowShader;
    private static Shader frameGenShader;
    private static boolean shadersLoaded = false;

    private static final Matrix4f prevViewProj = new Matrix4f();
    private static final Matrix4f currViewProj = new Matrix4f();
    private static final Matrix4f currInvViewProj = new Matrix4f();
    private static boolean hasPrevMatrix = false;
    private static boolean hasValidPrevFrame = false;
    private static boolean statsLogged = false;

    private static final ThreadLocal<float[]> MATRIX_IN_BUFFER = ThreadLocal.withInitial(() -> new float[16]);
    private static final ThreadLocal<float[]> MATRIX_OUT_BUFFER = ThreadLocal.withInitial(() -> new float[16]);

    private UniversalFrameGenerator() {}

    public static void updateCameraMatrix(Matrix4f viewProj) {
        if (viewProj == null) return;
        if (!hasPrevMatrix) {
            prevViewProj.set(viewProj);
            hasPrevMatrix = true;
        }
        currViewProj.set(viewProj);
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.rustMatrixPipeline && NativeAccelerator.isRustAvailable()) {
                float[] inM = MATRIX_IN_BUFFER.get();
                float[] outM = MATRIX_OUT_BUFFER.get();
                viewProj.get(inM);
                if (NativeAccelerator.rustMatrix4fInvert(inM, outM)) {
                    currInvViewProj.set(outM);
                    return;
                }
            }
            currInvViewProj.set(viewProj).invert();
        } catch (Throwable ignored) {}
    }

    private static void initShaders() {
        if (shadersLoaded) return;
        try {
            QuadHelper.init();
            opticalFlowShader = new Shader(FrameGenShaders.VERTEX_SCREEN_QUAD, FrameGenShaders.OPTICAL_FLOW_FRAG);
            frameGenShader = new Shader(FrameGenShaders.VERTEX_SCREEN_QUAD, FrameGenShaders.FRAME_GEN_SYNTHESIS_FRAG);
            shadersLoaded = true;
        } catch (Throwable t) {
            LOGGER.error("Failed to compile UniversalFrameGenerator shaders: ", t);
        }
    }

    private static void checkResolution(int width, int height) {
        if (width == allocatedWidth && height == allocatedHeight && prevTex != -1) return;

        cleanupTextures();

        allocatedWidth = width;
        allocatedHeight = height;

        prevTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        flowTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, flowTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        flowFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, flowFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, flowTex, 0);

        copyReadFbo = GL30.glGenFramebuffers();
        copyDrawFbo = GL30.glGenFramebuffers();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        hasValidPrevFrame = false;
    }

    private static void copyTexture(int srcTex, int dstTex, int width, int height) {
        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, copyReadFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, srcTex, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyDrawFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, dstTex, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);

        if (prevScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
    }

    public static void onFramePresented(long windowHandle, OptimizationConfig config) {
        try {
            if (mc.gameRenderer == null || mc.gameRenderer.mainRenderTarget() == null) return;
            if (!(mc.gameRenderer.mainRenderTarget().getColorTexture() instanceof GlTexture sceneColor)) return;

            int currTex = sceneColor.glId();
            if (currTex <= 0) return;

            int depthTex = 0;
            if (mc.gameRenderer.mainRenderTarget().getDepthTexture() instanceof GlTexture sceneDepth) {
                depthTex = sceneDepth.glId();
            }

            int width = mc.getWindow().getWidth();
            int height = mc.getWindow().getHeight();
            if (width <= 0 || height <= 0) return;

            initShaders();
            if (!shadersLoaded) return;

            checkResolution(width, height);

            if (!hasValidPrevFrame) {
                copyTexture(currTex, prevTex, width, height);
                prevViewProj.set(currViewProj);
                hasValidPrevFrame = true;
                return;
            }

            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            int[] prevVp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevVp);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, flowFbo);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);

            opticalFlowShader.bind();

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, currTex);
            opticalFlowShader.setUniform1i("u_currTex", 0);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            opticalFlowShader.setUniform1i("u_prevTex", 1);

            if (depthTex > 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE2);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
                opticalFlowShader.setUniform1i("u_depthTex", 2);
                opticalFlowShader.setUniform1i("u_hasDepth", 1);
            } else {
                opticalFlowShader.setUniform1i("u_hasDepth", 0);
            }

            opticalFlowShader.setUniformMat4("u_invViewProjCurr", currInvViewProj);
            opticalFlowShader.setUniformMat4("u_viewProjPrev", prevViewProj);
            opticalFlowShader.setUniform2f("u_texelSize", 1.0f / (float) width, 1.0f / (float) height);

            QuadHelper.renderQuad();
            opticalFlowShader.unbind();

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glViewport(0, 0, width, height);

            frameGenShader.bind();

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, currTex);
            frameGenShader.setUniform1i("u_currTex", 0);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            frameGenShader.setUniform1i("u_prevTex", 1);

            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, flowTex);
            frameGenShader.setUniform1i("u_flowTex", 2);

            frameGenShader.setUniform2f("u_texelSize", 1.0f / (float) width, 1.0f / (float) height);
            frameGenShader.setUniform1f("u_blendFactor", 0.5f);
            frameGenShader.setUniform1f("u_sharpness", (float) config.frameGenSharpness);
            frameGenShader.setUniform1i("u_shieldDisocclusion", config.frameGenDisocclusion ? 1 : 0);

            QuadHelper.renderQuad();
            frameGenShader.unbind();

            GLFW.glfwSwapBuffers(windowHandle);

            copyTexture(currTex, prevTex, width, height);
            prevViewProj.set(currViewProj);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL30.glBindVertexArray(prevVao);
            if (prevProgram != 0) GL20.glUseProgram(prevProgram);

            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(prevDepthMask);
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND);
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE);
            if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);

            RenderResetHelper.fullReset();

            if (mc instanceof IMinecraftAccessor accessor) {
                accessor.vith$incrementFrames();
            }

            if (!statsLogged) {
                statsLogged = true;
                LOGGER.info("Universal Frame Generation (Optical Flow 2x) active on window ({}x{})", width, height);
            }
        } catch (Throwable t) {
            LOGGER.error("Error in UniversalFrameGenerator: ", t);
        }
    }

    public static void cleanupTextures() {
        if (prevTex != -1) {
            GL11.glDeleteTextures(prevTex);
            prevTex = -1;
        }
        if (flowTex != -1) {
            GL11.glDeleteTextures(flowTex);
            flowTex = -1;
        }
        if (flowFbo != -1) {
            GL30.glDeleteFramebuffers(flowFbo);
            flowFbo = -1;
        }
        if (copyReadFbo != -1) {
            GL30.glDeleteFramebuffers(copyReadFbo);
            copyReadFbo = -1;
        }
        if (copyDrawFbo != -1) {
            GL30.glDeleteFramebuffers(copyDrawFbo);
            copyDrawFbo = -1;
        }
        allocatedWidth = 0;
        allocatedHeight = 0;
        hasValidPrevFrame = false;
    }
}
