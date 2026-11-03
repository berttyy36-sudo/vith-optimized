package vithh.vithOptimized.math;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import vithh.vithOptimized.nativeopt.NativeAccelerator;

public final class FastRaycast {

    private FastRaycast() {}

    public static double intersectRayAABB(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double origX, double origY, double origZ,
            double invDirX, double invDirY, double invDirZ,
            double maxDist
    ) {
        double t1 = (minX - origX) * invDirX;
        double t2 = (maxX - origX) * invDirX;
        double tMin = Math.min(t1, t2);
        double tMax = Math.max(t1, t2);

        double t3 = (minY - origY) * invDirY;
        double t4 = (maxY - origY) * invDirY;
        tMin = Math.max(tMin, Math.min(t3, t4));
        tMax = Math.min(tMax, Math.max(t3, t4));

        double t5 = (minZ - origZ) * invDirZ;
        double t6 = (maxZ - origZ) * invDirZ;
        tMin = Math.max(tMin, Math.min(t5, t6));
        tMax = Math.min(tMax, Math.max(t5, t6));

        if (tMax >= Math.max(0.0, tMin) && tMin <= maxDist) {
            return tMin < 0.0 ? 0.0 : tMin;
        }

        return -1.0;
    }

    public static boolean isHit(Entity entity, double eyeX, double eyeY, double eyeZ,
                                double dirX, double dirY, double dirZ,
                                double reach, double hitboxExpand) {
        if (entity == null) return false;

        AABB box = entity.getBoundingBox();
        double minX = box.minX - hitboxExpand;
        double minY = box.minY - hitboxExpand;
        double minZ = box.minZ - hitboxExpand;
        double maxX = box.maxX + hitboxExpand;
        double maxY = box.maxY + hitboxExpand;
        double maxZ = box.maxZ + hitboxExpand;

        double invX = dirX != 0.0 ? 1.0 / dirX : (dirX >= 0.0 ? 1e12 : -1e12);
        double invY = dirY != 0.0 ? 1.0 / dirY : (dirY >= 0.0 ? 1e12 : -1e12);
        double invZ = dirZ != 0.0 ? 1.0 / dirZ : (dirZ >= 0.0 ? 1e12 : -1e12);

        double hitDist = intersectRayAABB(minX, minY, minZ, maxX, maxY, maxZ,
                eyeX, eyeY, eyeZ, invX, invY, invZ, reach);

        return hitDist >= 0.0 && hitDist <= reach;
    }

    public static double raycastBatch(
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            double[] minX, double[] minY, double[] minZ,
            double[] maxX, double[] maxY, double[] maxZ,
            int boxCount,
            double maxDist
    ) {
        return NativeAccelerator.raycastBatchSimd(
                ox, oy, oz, dx, dy, dz,
                minX, minY, minZ, maxX, maxY, maxZ,
                boxCount, maxDist
        );
    }
}
