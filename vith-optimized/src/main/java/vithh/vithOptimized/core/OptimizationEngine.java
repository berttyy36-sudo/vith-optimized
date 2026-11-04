package vithh.vithOptimized.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.culling.EntityCuller;
import vithh.vithOptimized.math.FastMath;
import vithh.vithOptimized.nativeopt.NativeAccelerator;
import vithh.vithOptimized.render.framegen.UniversalFrameGenerator;
import vithh.vithOptimized.threading.ParallelExecutor;

public final class OptimizationEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("VithOptimized-Engine");
    private static final Minecraft mc = Minecraft.getInstance();

    private static volatile OptimizationEngine INSTANCE;
    private final OptimizationConfig config;
    private int tickCounter = 0;

    private OptimizationEngine() {
        this.config = OptimizationConfig.getInstance();
    }

    public static OptimizationEngine getInstance() {
        if (INSTANCE == null) {
            synchronized (OptimizationEngine.class) {
                if (INSTANCE == null) {
                    INSTANCE = new OptimizationEngine();
                }
            }
        }
        return INSTANCE;
    }

    public void init() {
        applyThreadingConfig();
        applyGpuConfig();
        applyChunkConfig();
        LOGGER.info("OptimizationEngine initialized successfully with all optimizations enabled (Rust Engine Active: {}).", NativeAccelerator.isRustAvailable());
    }

    public OptimizationConfig getConfig() {
        return config;
    }

    public void applyThreadingConfig() {
        if (!config.multiThreading) return;

        int threads = config.threadCount;
        System.setProperty("max.bg.threads", String.valueOf(threads));
        ParallelExecutor.init(threads, config.cpuPriority);

        if (config.cpuPriority) {
            try {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            } catch (SecurityException ignored) {}
        }
    }

    public void applyGpuConfig() {
        if (!config.gpuThroughput) return;

        try {
            OptionInstance<Integer> fpsOption = mc.options.framerateLimit();
            if (fpsOption != null && fpsOption.get() < 260) {
                fpsOption.set(260);
            }

            OptionInstance<Boolean> vsyncOption = mc.options.enableVsync();
            if (vsyncOption != null && vsyncOption.get()) {
                vsyncOption.set(false);
            }
        } catch (Throwable ignored) {}
    }

    public void applyChunkConfig() {
        try {
            if (config.asyncChunkMeshing) {
                OptionInstance<PrioritizeChunkUpdates> chunkOption = mc.options.prioritizeChunkUpdates();
                if (chunkOption != null && chunkOption.get() != PrioritizeChunkUpdates.NONE) {
                    chunkOption.set(PrioritizeChunkUpdates.NONE);
                }
            }
        } catch (Throwable ignored) {}
    }

    public void onTick() {
        tickCounter++;
        if (tickCounter % 40 == 0) {
            if (config.cpuPriority && Thread.currentThread().getPriority() != Thread.MAX_PRIORITY) {
                try {
                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                } catch (SecurityException ignored) {}
            }

            if (config.gpuThroughput) {
                applyGpuConfig();
            }

            if (config.asyncChunkMeshing) {
                applyChunkConfig();
            }
        }
    }

    public boolean shouldCullEntity(Entity entity, double camX, double camY, double camZ) {
        if (!config.parallelEntityCulling) {
            return false;
        }
        return EntityCuller.shouldCull(entity, camX, camY, camZ, config);
    }

    public boolean shouldCullBlockEntity(BlockEntity blockEntity) {
        if (!config.blockEntityCulling || blockEntity == null) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        if (pos == null) return false;

        if (mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return false;
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        if (camPos == null) return false;

        double maxDist = config.blockEntityDistance;
        double maxDistSq = maxDist * maxDist;

        if (config.rustEngine && NativeAccelerator.isRustAvailable()) {
            return NativeAccelerator.rustShouldCullDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, camPos.x, camPos.y, camPos.z, maxDistSq);
        }

        double distSq = FastMath.distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, camPos.x, camPos.y, camPos.z);
        return distSq > maxDistSq;
    }

    public boolean shouldCullParticle(double x, double y, double z, int currentCount) {
        if (!config.particleCulling) {
            return false;
        }

        if (currentCount >= config.maxParticles) {
            return true;
        }

        if (mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return false;
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        if (camPos != null) {
            double maxDistSq = 36.0 * 36.0;
            if (config.rustEngine && NativeAccelerator.isRustAvailable()) {
                return NativeAccelerator.rustShouldCullDistance(x, y, z, camPos.x, camPos.y, camPos.z, maxDistSq);
            }
            double distSq = FastMath.distanceSq(x, y, z, camPos.x, camPos.y, camPos.z);
            if (distSq > maxDistSq) {
                return true;
            }
        }

        return false;
    }

    public void cleanup() {
        EntityCuller.clearCache();
        ParallelExecutor.shutdownInstance();
        UniversalFrameGenerator.cleanupTextures();
    }
}
