package vithh.vithOptimized.math;

public final class FastMath {

    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float HALF_PI = (float) (Math.PI * 0.5);
    private static final float INV_TWO_PI = 1.0f / TWO_PI;

    private static final float S1 = -0.166666571f;
    private static final float S2 =  0.008333075f;
    private static final float S3 = -0.000198107f;

    private FastMath() {}

    public static float sin(float rad) {
        float x = rad - TWO_PI * (float) Math.floor((rad + PI) * INV_TWO_PI);

        if (x > HALF_PI) {
            x = PI - x;
        } else if (x < -HALF_PI) {
            x = -PI - x;
        }

        float x2 = x * x;
        return x + x * x2 * (S1 + x2 * (S2 + x2 * S3));
    }

    public static float cos(float rad) {
        return sin(rad + HALF_PI);
    }

    public static float fastInvSqrt(float x) {
        return 1.0f / (float) Math.sqrt(x);
    }

    public static double distanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distanceSq2D(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    public static void batchSinCos(float[] inAngles, int count, float[] outSin, float[] outCos) {
        vithh.vithOptimized.nativeopt.NativeAccelerator.rustBatchSinCos(inAngles, count, outSin, outCos);
    }

    public static void batchFastInvSqrt(float[] inValues, int count, float[] outValues) {
        vithh.vithOptimized.nativeopt.NativeAccelerator.rustFastInvSqrtBatch(inValues, count, outValues);
    }
}
