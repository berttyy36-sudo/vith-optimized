package vithh.vithOptimized.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vithh.vithOptimized.core.OptimizationEngine;
import vithh.vithOptimized.render.IMinecraftAccessor;

@Mixin(Minecraft.class)
public class MinecraftMixin implements IMinecraftAccessor {

    @Shadow
    private int frames;

    @Override
    public void vith$incrementFrames() {
        this.frames++;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        OptimizationEngine.getInstance().onTick();
    }
}
