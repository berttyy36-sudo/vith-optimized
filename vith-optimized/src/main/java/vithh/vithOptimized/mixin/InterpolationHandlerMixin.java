package vithh.vithOptimized.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vithh.vithOptimized.config.OptimizationConfig;

@Mixin(InterpolationHandler.class)
public abstract class InterpolationHandlerMixin {

    @Redirect(
        method = "interpolate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z")
    )
    private boolean redirectNoCollision(Level level, Entity entity, AABB box) {
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.entityInterpolationOpt && entity != Minecraft.getInstance().player) {
                return true;
            }
        } catch (Throwable ignored) {}
        return level.noCollision(entity, box);
    }
}
