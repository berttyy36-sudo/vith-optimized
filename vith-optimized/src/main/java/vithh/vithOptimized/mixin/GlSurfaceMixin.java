package vithh.vithOptimized.mixin;

import com.mojang.blaze3d.opengl.GlSurface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.render.framegen.UniversalFrameGenerator;

@Mixin(GlSurface.class)
public class GlSurfaceMixin {

    @Shadow
    @Final
    private long windowHandle;

    @Inject(method = "present", at = @At("RETURN"))
    private void onPresent(CallbackInfo ci) {
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.frameGeneration) {
                UniversalFrameGenerator.onFramePresented(this.windowHandle, config);
            }
        } catch (Throwable ignored) {}
    }
}
