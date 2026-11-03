package vithh.vithOptimized.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vithh.vithOptimized.render.framegen.UniversalFrameGenerator;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraRenderState, Matrix4fc projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderEntityOutline, CallbackInfo ci) {
        if (cameraRenderState != null && cameraRenderState.viewRotationMatrix != null && projectionMatrix != null) {
            Matrix4f viewProj = new Matrix4f(projectionMatrix).mul(cameraRenderState.viewRotationMatrix);
            UniversalFrameGenerator.updateCameraMatrix(viewProj);
        }
    }
}
