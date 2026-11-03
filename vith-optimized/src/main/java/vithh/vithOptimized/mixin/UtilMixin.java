package vithh.vithOptimized.mixin;

import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vithh.vithOptimized.config.OptimizationConfig;

@Mixin(Util.class)
public class UtilMixin {

    @Inject(method = "maxAllowedExecutorThreads", at = @At("HEAD"), cancellable = true)
    private static void onMaxAllowedExecutorThreads(CallbackInfoReturnable<Integer> cir) {
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.multiThreading) {
                cir.setReturnValue(config.threadCount);
            }
        } catch (Throwable ignored) {}
    }
}
