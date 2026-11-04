package vithh.vithOptimized.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.math.FastMath;
import vithh.vithOptimized.nativeopt.NativeAccelerator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCuller {

    private static final Map<Integer, Boolean> CULL_CACHE = new ConcurrentHashMap<>();

    private EntityCuller() {}

    public static void clearCache() {
        CULL_CACHE.clear();
    }

    public static boolean shouldCull(Entity entity, double camX, double camY, double camZ, OptimizationConfig config) {
        if (entity == null || config == null || !config.parallelEntityCulling) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null && (entity == mc.player || entity.is(mc.player))) {
            return false;
        }

        if (mc.player != null && (mc.player.hasIndirectPassenger(entity) || entity.hasIndirectPassenger(mc.player))) {
            return false;
        }

        double maxDist = config.entityDistance;
        double maxDistSq = maxDist * maxDist;

        if (config.rustEngine && NativeAccelerator.isRustAvailable()) {
            if (NativeAccelerator.rustShouldCullDistance(entity.getX(), entity.getY(), entity.getZ(), camX, camY, camZ, maxDistSq)) {
                return true;
            }
        } else {
            double distSq = FastMath.distanceSq(entity.getX(), entity.getY(), entity.getZ(), camX, camY, camZ);
            if (distSq > maxDistSq) {
                return true;
            }
        }

        if (config.cullInvisible) {
            if (entity.isInvisible()) {
                if (!entity.isCurrentlyGlowing() && !entity.hasGlowingTag()) {
                    return true;
                }
            }

            if (entity instanceof ArmorStand stand) {
                if (stand.isInvisible() || stand.isMarker()) {
                    return true;
                }
            }

            if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                double distSq = (config.rustEngine && NativeAccelerator.isRustAvailable())
                        ? NativeAccelerator.rustDistanceSq(entity.getX(), entity.getY(), entity.getZ(), camX, camY, camZ)
                        : FastMath.distanceSq(entity.getX(), entity.getY(), entity.getZ(), camX, camY, camZ);
                if (distSq > (24.0 * 24.0)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void cullBatchNative(
            float[] cx, float[] cy, float[] cz,
            float[] ex, float[] ey, float[] ez,
            float[] pnx, float[] pny, float[] pnz, float[] pd,
            int count,
            long[] outBits
    ) {
        NativeAccelerator.cullEntitiesSimd(cx, cy, cz, ex, ey, ez, pnx, pny, pnz, pd, count, outBits);
    }
}
