package vithh.vithOptimized.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.core.OptimizationEngine;

import java.util.Queue;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Shadow
    private Queue<Particle> particlesToAdd;

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void onAddParticle(Particle particle, CallbackInfo ci) {
        if (particle == null) return;
        try {
            OptimizationEngine engine = OptimizationEngine.getInstance();
            OptimizationConfig config = engine.getConfig();
            if (config.particleCulling) {
                Vec3 center = particle.getBoundingBox().getCenter();
                int currentQueueSize = particlesToAdd != null ? particlesToAdd.size() : 0;
                if (engine.shouldCullParticle(center.x, center.y, center.z, currentQueueSize)) {
                    ci.cancel();
                }
            }
        } catch (Throwable ignored) {}
    }
}
