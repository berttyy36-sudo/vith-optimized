package vithh.vithOptimized.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.culling.EntityCuller;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        if (entity == null) return;
        try {
            OptimizationConfig config = OptimizationConfig.getInstance();
            if (config.entityTickCulling) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && entity != mc.player && !entity.hasIndirectPassenger(mc.player)) {
                    if (entity.tickCount % 2 != 0) {
                        double camX = mc.player.getX();
                        double camY = mc.player.getY();
                        double camZ = mc.player.getZ();
                        if (EntityCuller.shouldCull(entity, camX, camY, camZ, config)) {
                            ci.cancel();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
