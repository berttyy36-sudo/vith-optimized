package vithh.vithOptimized.render;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import vithh.vithOptimized.mixin.GlStateManagerAccessor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class RenderResetHelper {

    private static final List<Field> ENCODER_FIELDS_TO_CLEAR = new ArrayList<>();
    private static boolean reflectionInitialized = false;

    private RenderResetHelper() {}

    private static void initReflection(Object encoder) {
        if (reflectionInitialized || encoder == null) return;
        reflectionInitialized = true;
        try {
            Class<?> clazz = encoder.getClass();
            for (Field f : clazz.getDeclaredFields()) {
                String name = f.getName();
                Class<?> type = f.getType();

                boolean match = name.equals("lastPipeline") || name.equals("lastProgram") || name.equals("lastVertexArray")
                        || RenderPipeline.class.isAssignableFrom(type)
                        || GlProgram.class.isAssignableFrom(type)
                        || type.getName().contains("VertexArray");

                if (match) {
                    f.setAccessible(true);
                    ENCODER_FIELDS_TO_CLEAR.add(f);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void fullReset() {
        try {
            Object encoder = RenderSystem.getDevice().createCommandEncoder();
            if (encoder != null) {
                if (!reflectionInitialized) {
                    initReflection(encoder);
                }
                for (Field f : ENCODER_FIELDS_TO_CLEAR) {
                    try {
                        f.set(encoder, null);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        GlStateManagerAccessor.setWriteFbo(-999);
        GlStateManagerAccessor.setReadFbo(-999);

        for (int i = 0; i < 8; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(0);
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }
}
