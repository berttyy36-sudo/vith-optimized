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
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.nativeopt.NativeAccelerator;
import vithh.vithOptimized.render.framegen.UniversalFrameGenerator;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    private static final ThreadLocal<float[]> PROJ_BUFFER = ThreadLocal.withInitial(() -> new float[16]);
    private static final ThreadLocal<float[]> VIEW_BUFFER = ThreadLocal.withInitial(() -> new float[16]);
    private static final ThreadLocal<float[]> RES_BUFFER = ThreadLocal.withInitial(() -> new float[16]);
    private static final ThreadLocal<float[]> FRUSTUM_NX = ThreadLocal.withInitial(() -> new float[6]);
    private static final ThreadLocal<float[]> FRUSTUM_NY = ThreadLocal.withInitial(() -> new float[6]);
    private static final ThreadLocal<float[]> FRUSTUM_NZ = ThreadLocal.withInitial(() -> new float[6]);
    private static final ThreadLocal<float[]> FRUSTUM_D  = ThreadLocal.withInitial(() -> new float[6]);

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraRenderState, Matrix4fc projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderEntityOutline, CallbackInfo ci) {
        if (cameraRenderState != null && cameraRenderState.viewRotationMatrix != null && projectionMatrix != null) {
            try {
                OptimizationConfig config = OptimizationConfig.getInstance();
                if (config.rustMatrixPipeline && NativeAccelerator.isRustAvailable()) {
                    float[] projArr = PROJ_BUFFER.get();
                    float[] viewArr = VIEW_BUFFER.get();
                    float[] resArr = RES_BUFFER.get();
                    projectionMatrix.get(projArr);
                    cameraRenderState.viewRotationMatrix.get(viewArr);
                    NativeAccelerator.rustMatrix4fMul(projArr, viewArr, resArr);

                    if (config.rustFrustumCulling) {
                        NativeAccelerator.rustExtractFrustumPlanes(
                                resArr,
                                FRUSTUM_NX.get(),
                                FRUSTUM_NY.get(),
                                FRUSTUM_NZ.get(),
                                FRUSTUM_D.get()
                        );
                    }

                    Matrix4f viewProj = new Matrix4f();
                    viewProj.set(resArr);
                    UniversalFrameGenerator.updateCameraMatrix(viewProj);
                    return;
                }
            } catch (Throwable ignored) {}

            Matrix4f viewProj = new Matrix4f(projectionMatrix).mul(cameraRenderState.viewRotationMatrix);
            UniversalFrameGenerator.updateCameraMatrix(viewProj);
        }
    }
}
