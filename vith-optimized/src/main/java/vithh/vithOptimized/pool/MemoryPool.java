package vithh.vithOptimized.pool;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class MemoryPool {

    private static final int MAX_VEC2 = 256;
    private static final int MAX_VEC3 = 256;
    private static final int MAX_VEC4 = 128;
    private static final int MAX_MAT4 = 64;
    private static final int MAX_QUAT = 64;
    private static final int MAX_POSE = 16;
    private static final int MAX_DEPTH = 32;

    private static final ThreadLocal<PoolState> STATE = ThreadLocal.withInitial(PoolState::new);

    private MemoryPool() {}

    public static Scope scope() {
        PoolState state = STATE.get();
        state.push();
        return state;
    }

    public static Vector2f vec2() { return STATE.get().allocVec2(); }
    public static Vector3f vec3() { return STATE.get().allocVec3(); }
    public static Vector4f vec4() { return STATE.get().allocVec4(); }
    public static Matrix4f mat4() { return STATE.get().allocMat4(); }
    public static Quaternionf quat() { return STATE.get().allocQuat(); }
    public static PoseStack poseStack() { return STATE.get().allocPoseStack(); }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static final class PoolState implements Scope {
        private final Vector2f[] vec2Pool = new Vector2f[MAX_VEC2];
        private final Vector3f[] vec3Pool = new Vector3f[MAX_VEC3];
        private final Vector4f[] vec4Pool = new Vector4f[MAX_VEC4];
        private final Matrix4f[] mat4Pool = new Matrix4f[MAX_MAT4];
        private final Quaternionf[] quatPool = new Quaternionf[MAX_QUAT];
        private final PoseStack[] posePool = new PoseStack[MAX_POSE];

        private int idxVec2 = 0;
        private int idxVec3 = 0;
        private int idxVec4 = 0;
        private int idxMat4 = 0;
        private int idxQuat = 0;
        private int idxPose = 0;

        private final int[] markVec2 = new int[MAX_DEPTH];
        private final int[] markVec3 = new int[MAX_DEPTH];
        private final int[] markVec4 = new int[MAX_DEPTH];
        private final int[] markMat4 = new int[MAX_DEPTH];
        private final int[] markQuat = new int[MAX_DEPTH];
        private final int[] markPose = new int[MAX_DEPTH];
        private int depth = 0;

        public PoolState() {
            for (int i = 0; i < MAX_VEC2; i++) vec2Pool[i] = new Vector2f();
            for (int i = 0; i < MAX_VEC3; i++) vec3Pool[i] = new Vector3f();
            for (int i = 0; i < MAX_VEC4; i++) vec4Pool[i] = new Vector4f();
            for (int i = 0; i < MAX_MAT4; i++) mat4Pool[i] = new Matrix4f();
            for (int i = 0; i < MAX_QUAT; i++) quatPool[i] = new Quaternionf();
            for (int i = 0; i < MAX_POSE; i++) posePool[i] = new PoseStack();
        }

        public void push() {
            if (depth < MAX_DEPTH) {
                markVec2[depth] = idxVec2;
                markVec3[depth] = idxVec3;
                markVec4[depth] = idxVec4;
                markMat4[depth] = idxMat4;
                markQuat[depth] = idxQuat;
                markPose[depth] = idxPose;
                depth++;
            }
        }

        @Override
        public void close() {
            if (depth > 0) {
                depth--;
                idxVec2 = markVec2[depth];
                idxVec3 = markVec3[depth];
                idxVec4 = markVec4[depth];
                idxMat4 = markMat4[depth];
                idxQuat = markQuat[depth];
                idxPose = markPose[depth];
            }
        }

        public Vector2f allocVec2() {
            if (idxVec2 >= MAX_VEC2) idxVec2 = 0;
            return vec2Pool[idxVec2++];
        }

        public Vector3f allocVec3() {
            if (idxVec3 >= MAX_VEC3) idxVec3 = 0;
            return vec3Pool[idxVec3++];
        }

        public Vector4f allocVec4() {
            if (idxVec4 >= MAX_VEC4) idxVec4 = 0;
            return vec4Pool[idxVec4++];
        }

        public Matrix4f allocMat4() {
            if (idxMat4 >= MAX_MAT4) idxMat4 = 0;
            return mat4Pool[idxMat4++];
        }

        public Quaternionf allocQuat() {
            if (idxQuat >= MAX_QUAT) idxQuat = 0;
            return quatPool[idxQuat++];
        }

        public PoseStack allocPoseStack() {
            if (idxPose >= MAX_POSE) idxPose = 0;
            PoseStack ps = posePool[idxPose++];
            while (!ps.isEmpty()) ps.popPose();
            return ps;
        }
    }
}
