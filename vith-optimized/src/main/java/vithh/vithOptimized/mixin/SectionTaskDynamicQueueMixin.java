package vithh.vithOptimized.mixin;

import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vithh.vithOptimized.config.OptimizationConfig;

@Mixin(SectionTaskDynamicQueue.class)
public class SectionTaskDynamicQueueMixin {

    @Shadow
    private int recompileQuota;

    @Inject(method = "poll", at = @At("HEAD"))
    private void onPoll(Vec3 cameraPosition, CallbackInfoReturnable<?> cir) {
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.multiThreading) {
                if (this.recompileQuota < 16) {
                    this.recompileQuota = Math.max(32, config.threadCount * 4);
                }
            }
        } catch (Throwable ignored) {}
    }
}
