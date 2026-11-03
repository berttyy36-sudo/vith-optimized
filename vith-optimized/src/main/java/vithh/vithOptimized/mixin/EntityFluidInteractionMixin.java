package vithh.vithOptimized.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vithh.vithOptimized.config.OptimizationConfig;

@Mixin(EntityFluidInteraction.class)
public class EntityFluidInteractionMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdateFluidInteraction(Entity entity, boolean isPushedByFluid, CallbackInfo ci) {
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.fluidPhysicsOpt) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && entity != mc.player) {
                    ci.cancel();
                }
            }
        } catch (Throwable ignored) {}
    }
}
