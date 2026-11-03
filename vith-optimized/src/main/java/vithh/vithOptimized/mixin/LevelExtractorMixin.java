package vithh.vithOptimized.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vithh.vithOptimized.core.OptimizationEngine;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    @Shadow @Final private LevelRenderer levelRenderer;

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void onIsEntityVisible(Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> cir) {
        try {
            OptimizationEngine engine = OptimizationEngine.getInstance();
            if (engine.shouldCullEntity(entity, cameraX, cameraY, cameraZ)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
