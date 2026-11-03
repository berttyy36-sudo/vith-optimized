package vithh.vithOptimized.mixin;

import net.minecraft.client.renderer.SectionBufferBuilderPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vithh.vithOptimized.config.OptimizationConfig;

@Mixin(SectionBufferBuilderPool.class)
public class SectionBufferBuilderPoolMixin {

    @ModifyVariable(method = "allocate", at = @At("HEAD"), argsOnly = true)
    private static int onAllocate(int count) {
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.multiThreading) {
                return Math.max(count, config.threadCount * 3);
            }
        } catch (Throwable ignored) {}
        return count;
    }
}
