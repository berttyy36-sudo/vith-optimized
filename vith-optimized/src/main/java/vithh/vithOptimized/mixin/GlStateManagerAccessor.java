package vithh.vithOptimized.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlStateManager.class)
public interface GlStateManagerAccessor {

    @Accessor("writeFbo")
    static void setWriteFbo(int fbo) {
        throw new AssertionError();
    }

    @Accessor("readFbo")
    static void setReadFbo(int fbo) {
        throw new AssertionError();
    }

    @Accessor("writeFbo")
    static int getWriteFbo() {
        throw new AssertionError();
    }

    @Accessor("readFbo")
    static int getReadFbo() {
        throw new AssertionError();
    }
}
