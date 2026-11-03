package vithh.vithOptimized.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vithh.vithOptimized.core.OptimizationEngine;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void onTryExtractRenderState(
            E blockEntity, float tickDelta, ModelFeatureRenderer.CrumblingOverlay overlay, boolean bl,
            CallbackInfoReturnable<S> cir
    ) {
        try {
            OptimizationEngine engine = OptimizationEngine.getInstance();
            if (engine.shouldCullBlockEntity(blockEntity)) {
                cir.setReturnValue(null);
            }
        } catch (Throwable ignored) {}
    }
}
