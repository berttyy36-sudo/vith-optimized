package vithh.vithOptimized.nativeopt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class NativeAccelerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("VithOptimized-Native");

    private static boolean available = false;
    private static uintFeatures features = new uintFeatures(0);

    // C++ Handles (Fallback)
    private static MethodHandle CULL_ENTITIES_HANDLE;
    private static MethodHandle RAYCAST_BATCH_HANDLE;
    private static MethodHandle TRANSFORM_QUADS_HANDLE;
    private static MethodHandle MESH_SECTION_FACES_HANDLE;
    private static MethodHandle BATCH_AABB_OVERLAP_HANDLE;
    private static MethodHandle GET_FEATURES_HANDLE;

    // Rust Engine Handles (Primary)
    private static boolean rustAvailable = false;
    private static int rustVersion = 0;
    private static MethodHandle RUST_GET_VERSION_HANDLE;
    private static MethodHandle RUST_CULL_ENTITIES_HANDLE;
    private static MethodHandle RUST_RAYCAST_AABB_BATCH_HANDLE;
    private static MethodHandle RUST_TRANSFORM_QUADS_HANDLE;
    private static MethodHandle RUST_TEST_DISTANCE_CULL_SINGLE_HANDLE;
    private static MethodHandle RUST_DISTANCE_SQ_3D_HANDLE;
    private static MethodHandle RUST_EXTRACT_FRUSTUM_PLANES_HANDLE;
    private static MethodHandle RUST_BATCH_FRUSTUM_CULL_AABBS_HANDLE;
    private static MethodHandle RUST_TEST_BOX_FRUSTUM_SINGLE_HANDLE;
    private static MethodHandle RUST_PARTICLE_BATCH_TICK_HANDLE;
    private static MethodHandle RUST_PARTICLE_BATCH_CULL_HANDLE;
    private static MethodHandle RUST_MATRIX4F_PROJECT_POINT_HANDLE;
    private static MethodHandle RUST_BATCH_MATRIX4F_MUL_HANDLE;
    private static MethodHandle RUST_QUATERNION_SLERP_BATCH_HANDLE;
    private static MethodHandle RUST_FAST_INV_SQRT_BATCH_HANDLE;
    private static MethodHandle RUST_BUILD_CHUNK_SECTION_MESH_HANDLE;

    private static MethodHandle RUST_MESH_SECTION_FACES_HANDLE;
    private static MethodHandle RUST_BATCH_AABB_OVERLAP_HANDLE;
    private static MethodHandle RUST_GREEDY_MERGE_SLICE_HANDLE;
    private static MethodHandle RUST_SORT_TRANSLUCENT_DEPTH_HANDLE;
    private static MethodHandle RUST_BATCH_LERP_VECTORS_HANDLE;
    private static MethodHandle RUST_SOLVE_OCCLUSION_GRAPH_HANDLE;
    private static MethodHandle RUST_COMPUTE_AMBIENT_OCCLUSION_3D_HANDLE;
    private static MethodHandle RUST_BATCH_TRANSFORM_MODEL_PARTS_HANDLE;
    private static MethodHandle RUST_MATRIX4F_MUL_HANDLE;
    private static MethodHandle RUST_MATRIX4F_INVERT_HANDLE;
    private static MethodHandle RUST_FRUSTUM_TEST_BOX_HANDLE;
    private static MethodHandle RUST_BATCH_DISTANCE_CULL_HANDLE;
    private static MethodHandle RUST_FAST_LIGHTMAP_HANDLE;
    private static MethodHandle RUST_BATCH_SIN_COS_HANDLE;

    public record MergedQuad(int minU, int minV, int maxU, int maxV, int sliceCoord, int direction) {}
    public record NativeVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, int normal) {}
    public record ChunkVoxelFace(int minU, int minV, int maxU, int maxV, int sliceCoord, int direction, int blockId, float[] ao, int[] light) {}

    public record uintFeatures(int bitmask) {
        public boolean hasAvx2() { return (bitmask & 1) != 0; }
        public boolean hasFma()  { return (bitmask & 2) != 0; }
    }

    static {
        File nativeDir = new File("vith/natives");
        if (!nativeDir.exists()) {
            nativeDir.mkdirs();
        }
        Linker linker = Linker.nativeLinker();

        try {
            loadRustLibrary(linker, nativeDir);
        } catch (Throwable t) {
            LOGGER.warn("Rust Engine initialization failed: {}", t.getMessage());
            rustAvailable = false;
        }

        try {
            loadNativeCppLibrary(linker, nativeDir);
        } catch (Throwable t) {
            LOGGER.warn("Native C++ accelerator could not be initialized: {}", t.getMessage());
            available = false;
        }

        if (rustAvailable) {
            available = true;
        }
    }

    private NativeAccelerator() {}

    private static void loadNativeCppLibrary(Linker linker, File nativeDir) throws Throwable {
        String dllName = "vith_engine.dll";
        File targetDll = new File(nativeDir, dllName);
        File devDll = new File("src/main/resources/natives/" + dllName);

        if (devDll.exists()) {
            Files.copy(devDll.toPath(), targetDll.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in = NativeAccelerator.class.getResourceAsStream("/natives/" + dllName)) {
                if (in != null) {
                    Files.copy(in, targetDll.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        if (!targetDll.exists()) {
            return;
        }

        SymbolLookup lookup = SymbolLookup.libraryLookup(targetDll.toPath(), Arena.global());

        var featSym = lookup.find("vith_get_simd_features");
        if (featSym.isPresent()) {
            GET_FEATURES_HANDLE = linker.downcallHandle(featSym.get(), FunctionDescriptor.of(ValueLayout.JAVA_INT));
            int featVal = (int) GET_FEATURES_HANDLE.invokeExact();
            features = new uintFeatures(featVal);
        }

        var cullSym = lookup.find("vith_cull_entities_avx2");
        cullSym.ifPresent(memorySegment -> CULL_ENTITIES_HANDLE = linker.downcallHandle(
                memorySegment,
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        ));

        var raySym = lookup.find("vith_raycast_aabb_batch_avx2");
        raySym.ifPresent(memorySegment -> RAYCAST_BATCH_HANDLE = linker.downcallHandle(
                memorySegment,
                FunctionDescriptor.of(
                        ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS
                )
        ));

        var meshSym = lookup.find("vith_mesh_section_faces_avx2");
        meshSym.ifPresent(memorySegment -> MESH_SECTION_FACES_HANDLE = linker.downcallHandle(
                memorySegment,
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        ));

        var overlapSym = lookup.find("vith_batch_aabb_overlap_avx2");
        overlapSym.ifPresent(memorySegment -> BATCH_AABB_OVERLAP_HANDLE = linker.downcallHandle(
                memorySegment,
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        ));

        var transQuadSym = lookup.find("vith_transform_quads_avx2");
        transQuadSym.ifPresent(memorySegment -> TRANSFORM_QUADS_HANDLE = linker.downcallHandle(
                memorySegment,
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        ));

        LOGGER.info("Native C++ AVX2 Accelerator linked (AVX2={}, FMA={})", features.hasAvx2(), features.hasFma());
    }

    private static void loadRustLibrary(Linker linker, File nativeDir) throws Throwable {
        String rustDllName = "vith_rust_engine.dll";
        File targetRustDll = new File(nativeDir, rustDllName);
        File devRustDll = new File("src/main/resources/natives/" + rustDllName);

        if (devRustDll.exists()) {
            Files.copy(devRustDll.toPath(), targetRustDll.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in = NativeAccelerator.class.getResourceAsStream("/natives/" + rustDllName)) {
                if (in != null) Files.copy(in, targetRustDll.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (!targetRustDll.exists()) {
            throw new IllegalStateException("Rust native library not found: " + targetRustDll.getAbsolutePath());
        }

        SymbolLookup lookup = SymbolLookup.libraryLookup(targetRustDll.toPath(), Arena.global());

        RUST_GET_VERSION_HANDLE = linker.downcallHandle(lookup.find("vith_rust_get_version").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT));
        rustVersion = (int) RUST_GET_VERSION_HANDLE.invokeExact();

        RUST_CULL_ENTITIES_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_cull_entities_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        );

        RUST_RAYCAST_AABB_BATCH_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_raycast_aabb_batch_simd").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS
                )
        );

        RUST_TRANSFORM_QUADS_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_transform_quads_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_TEST_DISTANCE_CULL_SINGLE_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_test_distance_cull_single").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE
                )
        );

        RUST_DISTANCE_SQ_3D_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_distance_sq_3d").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE
                )
        );

        RUST_EXTRACT_FRUSTUM_PLANES_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_extract_frustum_planes_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
        );

        RUST_BATCH_FRUSTUM_CULL_AABBS_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_batch_frustum_cull_aabbs_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_TEST_BOX_FRUSTUM_SINGLE_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_test_box_frustum_single").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
        );

        RUST_PARTICLE_BATCH_TICK_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_particle_batch_tick_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT
                )
        );

        RUST_PARTICLE_BATCH_CULL_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_particle_batch_cull_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_MATRIX4F_PROJECT_POINT_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_matrix4f_project_point_simd").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
        );

        RUST_BATCH_MATRIX4F_MUL_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_batch_matrix4f_mul_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_QUATERNION_SLERP_BATCH_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_quaternion_slerp_batch_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_FAST_INV_SQRT_BATCH_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_fast_inv_sqrt_batch_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_BUILD_CHUNK_SECTION_MESH_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_build_chunk_section_mesh").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_MESH_SECTION_FACES_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_mesh_section_faces_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        RUST_BATCH_AABB_OVERLAP_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_batch_aabb_overlap_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_GREEDY_MERGE_SLICE_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_greedy_merge_slice").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_SORT_TRANSLUCENT_DEPTH_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_sort_translucent_depth").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS
                )
        );

        RUST_BATCH_LERP_VECTORS_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_batch_lerp_vectors").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT
                )
        );

        RUST_SOLVE_OCCLUSION_GRAPH_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_solve_occlusion_graph").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );

        RUST_COMPUTE_AMBIENT_OCCLUSION_3D_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_compute_ambient_occlusion_3d").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
        );

        RUST_BATCH_TRANSFORM_MODEL_PARTS_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_batch_transform_model_parts").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
        );

        RUST_MATRIX4F_MUL_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_matrix4f_mul").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        RUST_MATRIX4F_INVERT_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_matrix4f_invert").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        RUST_FRUSTUM_TEST_BOX_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_frustum_test_box_simd").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
        );

        RUST_BATCH_DISTANCE_CULL_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_batch_distance_cull").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        );

        RUST_FAST_LIGHTMAP_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_fast_lightmap_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        );

        RUST_BATCH_SIN_COS_HANDLE = linker.downcallHandle(
                lookup.find("vith_rust_batch_sin_cos_simd").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
        );

        rustAvailable = true;
        LOGGER.info("Primary Rust Game & Render Engine loaded successfully (vith_rust_engine v{})", rustVersion);
    }

    public static boolean isAvailable() {
        return rustAvailable || available;
    }

    public static boolean isRustAvailable() {
        return rustAvailable;
    }

    public static int getRustVersion() {
        return rustVersion;
    }

    public static uintFeatures getFeatures() {
        return features;
    }

    // Zero-allocation single distance test in Rust
    public static boolean rustShouldCullDistance(double x, double y, double z, double camX, double camY, double camZ, double maxDistSq) {
        if (rustAvailable && RUST_TEST_DISTANCE_CULL_SINGLE_HANDLE != null) {
            try {
                int res = (int) RUST_TEST_DISTANCE_CULL_SINGLE_HANDLE.invokeExact(x, y, z, camX, camY, camZ, maxDistSq);
                return res != 0;
            } catch (Throwable t) {
                LOGGER.warn("Rust single distance cull failed: {}", t.getMessage());
            }
        }
        double dx = x - camX;
        double dy = y - camY;
        double dz = z - camZ;
        return (dx * dx + dy * dy + dz * dz) > maxDistSq;
    }

    // Zero-allocation distance squared calculation
    public static double rustDistanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        if (rustAvailable && RUST_DISTANCE_SQ_3D_HANDLE != null) {
            try {
                return (double) RUST_DISTANCE_SQ_3D_HANDLE.invokeExact(x1, y1, z1, x2, y2, z2);
            } catch (Throwable ignored) {}
        }
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    // SIMD Frustum Plane Extraction from 4x4 View-Proj Matrix
    public static void rustExtractFrustumPlanes(float[] viewProj, float[] outNx, float[] outNy, float[] outNz, float[] outD) {
        if (viewProj == null || outNx == null || outNy == null || outNz == null || outD == null) return;
        if (rustAvailable && RUST_EXTRACT_FRUSTUM_PLANES_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment vpSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, viewProj);
                MemorySegment nxSeg = arena.allocate(ValueLayout.JAVA_FLOAT, 6);
                MemorySegment nySeg = arena.allocate(ValueLayout.JAVA_FLOAT, 6);
                MemorySegment nzSeg = arena.allocate(ValueLayout.JAVA_FLOAT, 6);
                MemorySegment dSeg  = arena.allocate(ValueLayout.JAVA_FLOAT, 6);

                RUST_EXTRACT_FRUSTUM_PLANES_HANDLE.invokeExact(vpSeg, nxSeg, nySeg, nzSeg, dSeg);

                MemorySegment.copy(nxSeg, ValueLayout.JAVA_FLOAT, 0, outNx, 0, 6);
                MemorySegment.copy(nySeg, ValueLayout.JAVA_FLOAT, 0, outNy, 0, 6);
                MemorySegment.copy(nzSeg, ValueLayout.JAVA_FLOAT, 0, outNz, 0, 6);
                MemorySegment.copy(dSeg,  ValueLayout.JAVA_FLOAT, 0, outD,  0, 6);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust frustum plane extraction failed: {}", t.getMessage());
            }
        }
        // Fallback
        float m00 = viewProj[0], m10 = viewProj[1], m20 = viewProj[2], m30 = viewProj[3];
        float m01 = viewProj[4], m11 = viewProj[5], m21 = viewProj[6], m31 = viewProj[7];
        float m02 = viewProj[8], m12 = viewProj[9], m22 = viewProj[10], m32 = viewProj[11];
        float m03 = viewProj[12], m13 = viewProj[13], m23 = viewProj[14], m33 = viewProj[15];

        float[][] raw = new float[][] {
                {m30 + m00, m31 + m01, m32 + m02, m33 + m03},
                {m30 - m00, m31 - m01, m32 - m02, m33 - m03},
                {m30 + m10, m31 + m11, m32 + m12, m33 + m13},
                {m30 - m10, m31 - m11, m32 - m12, m33 - m13},
                {m30 + m20, m31 + m21, m32 + m22, m33 + m23},
                {m30 - m20, m31 - m21, m32 - m22, m33 - m23}
        };
        for (int p = 0; p < 6; p++) {
            float nx = raw[p][0], ny = raw[p][1], nz = raw[p][2], d = raw[p][3];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float inv = len > 0.0f ? 1.0f / len : 1.0f;
            outNx[p] = nx * inv; outNy[p] = ny * inv; outNz[p] = nz * inv; outD[p] = d * inv;
        }
    }

    // Zero-allocation single box frustum test
    public static boolean rustTestBoxFrustumSingle(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float[] planesNx, float[] planesNy, float[] planesNz, float[] planesD
    ) {
        if (planesNx == null || planesNy == null || planesNz == null || planesD == null) return true;
        if (rustAvailable && RUST_TEST_BOX_FRUSTUM_SINGLE_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pnxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNx);
                MemorySegment pnySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNy);
                MemorySegment pnzSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNz);
                MemorySegment pdSeg  = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesD);

                int res = (int) RUST_TEST_BOX_FRUSTUM_SINGLE_HANDLE.invokeExact(
                        minX, minY, minZ, maxX, maxY, maxZ,
                        pnxSeg, pnySeg, pnzSeg, pdSeg
                );
                return res != 0;
            } catch (Throwable ignored) {}
        }
        float cx = (float) ((minX + maxX) * 0.5);
        float cy = (float) ((minY + maxY) * 0.5);
        float cz = (float) ((minZ + maxZ) * 0.5);
        float ex = (float) ((maxX - minX) * 0.5);
        float ey = (float) ((maxY - minY) * 0.5);
        float ez = (float) ((maxZ - minZ) * 0.5);

        for (int p = 0; p < 6; p++) {
            float dist = cx * planesNx[p] + cy * planesNy[p] + cz * planesNz[p] + planesD[p];
            float radius = ex * Math.abs(planesNx[p]) + ey * Math.abs(planesNy[p]) + ez * Math.abs(planesNz[p]);
            if (dist < -radius) return false;
        }
        return true;
    }

    // Entity Frustum Culling (Rust primary, C++ fallback, Java fallback)
    public static void cullEntitiesSimd(
            float[] centersX, float[] centersY, float[] centersZ,
            float[] extentsX, float[] extentsY, float[] extentsZ,
            float[] planesNx, float[] planesNy, float[] planesNz, float[] planesD,
            int count,
            long[] outVisibilityBits
    ) {
        if (count <= 0 || outVisibilityBits == null) return;

        MethodHandle handle = rustAvailable && RUST_CULL_ENTITIES_HANDLE != null ? RUST_CULL_ENTITIES_HANDLE : CULL_ENTITIES_HANDLE;

        if (handle != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment cxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, centersX);
                MemorySegment cySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, centersY);
                MemorySegment czSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, centersZ);

                MemorySegment exSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, extentsX);
                MemorySegment eySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, extentsY);
                MemorySegment ezSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, extentsZ);

                MemorySegment pnxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNx);
                MemorySegment pnySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNy);
                MemorySegment pnzSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNz);
                MemorySegment pdSeg  = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesD);

                int numWords = (count + 63) / 64;
                MemorySegment outBitsSeg = arena.allocate(ValueLayout.JAVA_LONG, numWords);

                handle.invokeExact(
                        cxSeg, cySeg, czSeg,
                        exSeg, eySeg, ezSeg,
                        pnxSeg, pnySeg, pnzSeg, pdSeg,
                        count,
                        outBitsSeg
                );

                MemorySegment.copy(outBitsSeg, ValueLayout.JAVA_LONG, 0, outVisibilityBits, 0, numWords);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Native culling execution failed: {}", t.getMessage());
            }
        }

        int numWords = (count + 63) / 64;
        for (int w = 0; w < numWords; ++w) {
            outVisibilityBits[w] = 0L;
        }

        for (int i = 0; i < count; ++i) {
            float cx = centersX[i];
            float cy = centersY[i];
            float cz = centersZ[i];
            float ex = extentsX[i];
            float ey = extentsY[i];
            float ez = extentsZ[i];

            boolean inside = true;
            for (int p = 0; p < 6; ++p) {
                float dist = cx * planesNx[p] + cy * planesNy[p] + cz * planesNz[p] + planesD[p];
                float radius = ex * Math.abs(planesNx[p]) + ey * Math.abs(planesNy[p]) + ez * Math.abs(planesNz[p]);
                if (dist < -radius) {
                    inside = false;
                    break;
                }
            }

            if (inside) {
                int wordIdx = i / 64;
                int bitOffset = i % 64;
                outVisibilityBits[wordIdx] |= (1L << bitOffset);
            }
        }
    }

    // Raycast AABB Batch (Rust primary, C++ fallback, Java fallback)
    public static double raycastBatchSimd(
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            double[] minX, double[] minY, double[] minZ,
            double[] maxX, double[] maxY, double[] maxZ,
            int boxCount,
            double maxDist
    ) {
        if (boxCount <= 0) return -1.0;

        MethodHandle handle = rustAvailable && RUST_RAYCAST_AABB_BATCH_HANDLE != null ? RUST_RAYCAST_AABB_BATCH_HANDLE : RAYCAST_BATCH_HANDLE;

        if (handle != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment minXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minX);
                MemorySegment minYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minY);
                MemorySegment minZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minZ);

                MemorySegment maxXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxX);
                MemorySegment maxYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxY);
                MemorySegment maxZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxZ);

                MemorySegment hitIdxSeg = arena.allocate(ValueLayout.JAVA_INT);

                return (double) handle.invokeExact(
                        ox, oy, oz,
                        dx, dy, dz,
                        minXSeg, minYSeg, minZSeg,
                        maxXSeg, maxYSeg, maxZSeg,
                        boxCount,
                        maxDist,
                        hitIdxSeg
                );
            } catch (Throwable t) {
                LOGGER.warn("Native raycast execution failed: {}", t.getMessage());
            }
        }

        double invDx = Math.abs(dx) > 1e-12 ? 1.0 / dx : 1e12;
        double invDy = Math.abs(dy) > 1e-12 ? 1.0 / dy : 1e12;
        double invDz = Math.abs(dz) > 1e-12 ? 1.0 / dz : 1e12;

        double closestHit = maxDist;
        int bestIdx = -1;

        for (int i = 0; i < boxCount; ++i) {
            double t1 = (minX[i] - ox) * invDx;
            double t2 = (maxX[i] - ox) * invDx;
            double t3 = (minY[i] - oy) * invDy;
            double t4 = (maxY[i] - oy) * invDy;
            double t5 = (minZ[i] - oz) * invDz;
            double t6 = (maxZ[i] - oz) * invDz;

            double tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
            double tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

            if (tmax >= 0.0 && tmin <= tmax && tmin < closestHit) {
                closestHit = tmin;
                bestIdx = i;
            }
        }

        return bestIdx != -1 ? closestHit : -1.0;
    }

    // Mesh Section Faces (Rust primary, C++ fallback, Java fallback)
    public static void meshSectionFacesSimd(
            byte[] blockOpacity,
            long[] outFaceBits,
            int[] outFaceCounts
    ) {
        if (blockOpacity == null || outFaceBits == null || outFaceCounts == null) return;
        if (blockOpacity.length < 4096 || outFaceBits.length < 384 || outFaceCounts.length < 6) return;

        MethodHandle handle = rustAvailable && RUST_MESH_SECTION_FACES_HANDLE != null ? RUST_MESH_SECTION_FACES_HANDLE : MESH_SECTION_FACES_HANDLE;

        if (handle != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment opSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, blockOpacity);
                MemorySegment bitsSeg = arena.allocate(ValueLayout.JAVA_LONG, 384);
                MemorySegment countsSeg = arena.allocate(ValueLayout.JAVA_INT, 6);

                handle.invokeExact(opSeg, bitsSeg, countsSeg);

                MemorySegment.copy(bitsSeg, ValueLayout.JAVA_LONG, 0, outFaceBits, 0, 384);
                MemorySegment.copy(countsSeg, ValueLayout.JAVA_INT, 0, outFaceCounts, 0, 6);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Native section meshing failed: {}", t.getMessage());
            }
        }

        for (int d = 0; d < 6; ++d) {
            outFaceCounts[d] = 0;
            for (int w = 0; w < 64; ++w) {
                outFaceBits[d * 64 + w] = 0L;
            }
        }

        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                int baseIdx = (y * 256) + (z * 16);
                for (int x = 0; x < 16; ++x) {
                    int idx = baseIdx + x;
                    if (blockOpacity[idx] == 0) continue;

                    int wordIdx = idx / 64;
                    long bit = 1L << (idx % 64);

                    if (y == 0 || blockOpacity[idx - 256] == 0) {
                        outFaceBits[0 * 64 + wordIdx] |= bit;
                        outFaceCounts[0]++;
                    }
                    if (y == 15 || blockOpacity[idx + 256] == 0) {
                        outFaceBits[1 * 64 + wordIdx] |= bit;
                        outFaceCounts[1]++;
                    }
                    if (z == 0 || blockOpacity[idx - 16] == 0) {
                        outFaceBits[2 * 64 + wordIdx] |= bit;
                        outFaceCounts[2]++;
                    }
                    if (z == 15 || blockOpacity[idx + 16] == 0) {
                        outFaceBits[3 * 64 + wordIdx] |= bit;
                        outFaceCounts[3]++;
                    }
                    if (x == 0 || blockOpacity[idx - 1] == 0) {
                        outFaceBits[4 * 64 + wordIdx] |= bit;
                        outFaceCounts[4]++;
                    }
                    if (x == 15 || blockOpacity[idx + 1] == 0) {
                        outFaceBits[5 * 64 + wordIdx] |= bit;
                        outFaceCounts[5]++;
                    }
                }
            }
        }
    }

    // Batch AABB Overlap (Rust primary, C++ fallback, Java fallback)
    public static void batchAabbOverlapSimd(
            double qMinX, double qMinY, double qMinZ,
            double qMaxX, double qMaxY, double qMaxZ,
            double[] minX, double[] minY, double[] minZ,
            double[] maxX, double[] maxY, double[] maxZ,
            int count,
            long[] outOverlapBits
    ) {
        if (count <= 0 || outOverlapBits == null) return;

        MethodHandle handle = rustAvailable && RUST_BATCH_AABB_OVERLAP_HANDLE != null ? RUST_BATCH_AABB_OVERLAP_HANDLE : BATCH_AABB_OVERLAP_HANDLE;

        if (handle != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment minXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minX);
                MemorySegment minYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minY);
                MemorySegment minZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minZ);
                MemorySegment maxXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxX);
                MemorySegment maxYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxY);
                MemorySegment maxZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxZ);

                int numWords = (count + 63) / 64;
                MemorySegment outBitsSeg = arena.allocate(ValueLayout.JAVA_LONG, numWords);

                handle.invokeExact(
                        qMinX, qMinY, qMinZ,
                        qMaxX, qMaxY, qMaxZ,
                        minXSeg, minYSeg, minZSeg,
                        maxXSeg, maxYSeg, maxZSeg,
                        count,
                        outBitsSeg
                );

                MemorySegment.copy(outBitsSeg, ValueLayout.JAVA_LONG, 0, outOverlapBits, 0, numWords);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Native AABB overlap failed: {}", t.getMessage());
            }
        }

        int numWords = (count + 63) / 64;
        for (int w = 0; w < numWords; ++w) {
            outOverlapBits[w] = 0L;
        }

        for (int i = 0; i < count; ++i) {
            if (qMinX < maxX[i] && qMaxX > minX[i] &&
                    qMinY < maxY[i] && qMaxY > minY[i] &&
                    qMinZ < maxZ[i] && qMaxZ > minZ[i]) {
                int wordIdx = i / 64;
                int bitOffset = i % 64;
                outOverlapBits[wordIdx] |= (1L << bitOffset);
            }
        }
    }

    // Particle Batch Simulation in Rust
    public static void rustParticleBatchTick(
            double[] posX, double[] posY, double[] posZ,
            double[] velX, double[] velY, double[] velZ,
            double gravity, double drag, int count
    ) {
        if (count <= 0 || posX == null || velX == null) return;
        if (rustAvailable && RUST_PARTICLE_BATCH_TICK_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment px = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, posX);
                MemorySegment py = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, posY);
                MemorySegment pz = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, posZ);
                MemorySegment vx = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, velX);
                MemorySegment vy = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, velY);
                MemorySegment vz = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, velZ);

                RUST_PARTICLE_BATCH_TICK_HANDLE.invokeExact(px, py, pz, vx, vy, vz, gravity, drag, count);

                MemorySegment.copy(px, ValueLayout.JAVA_DOUBLE, 0, posX, 0, count);
                MemorySegment.copy(py, ValueLayout.JAVA_DOUBLE, 0, posY, 0, count);
                MemorySegment.copy(pz, ValueLayout.JAVA_DOUBLE, 0, posZ, 0, count);
                MemorySegment.copy(vx, ValueLayout.JAVA_DOUBLE, 0, velX, 0, count);
                MemorySegment.copy(vy, ValueLayout.JAVA_DOUBLE, 0, velY, 0, count);
                MemorySegment.copy(vz, ValueLayout.JAVA_DOUBLE, 0, velZ, 0, count);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust particle batch tick failed: {}", t.getMessage());
            }
        }
        for (int i = 0; i < count; i++) {
            posX[i] += velX[i];
            velX[i] *= drag;
            posY[i] += velY[i];
            velY[i] = (velY[i] - gravity) * drag;
            posZ[i] += velZ[i];
            velZ[i] *= drag;
        }
    }

    // Particle Batch Culling in Rust
    public static void rustParticleBatchCull(
            double[] posX, double[] posY, double[] posZ,
            double camX, double camY, double camZ,
            double maxDistSq, int count, long[] outBits
    ) {
        if (count <= 0 || outBits == null || posX == null) return;
        if (rustAvailable && RUST_PARTICLE_BATCH_CULL_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment px = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, posX);
                MemorySegment py = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, posY);
                MemorySegment pz = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, posZ);
                int numWords = (count + 63) / 64;
                MemorySegment bits = arena.allocate(ValueLayout.JAVA_LONG, numWords);

                RUST_PARTICLE_BATCH_CULL_HANDLE.invokeExact(px, py, pz, camX, camY, camZ, maxDistSq, count, bits);

                MemorySegment.copy(bits, ValueLayout.JAVA_LONG, 0, outBits, 0, numWords);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust particle batch cull failed: {}", t.getMessage());
            }
        }
        int numWords = (count + 63) / 64;
        for (int w = 0; w < numWords; ++w) outBits[w] = 0L;
        for (int i = 0; i < count; ++i) {
            double dx = posX[i] - camX;
            double dy = posY[i] - camY;
            double dz = posZ[i] - camZ;
            if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                outBits[i / 64] |= (1L << (i % 64));
            }
        }
    }

    // World to Screen Projection in Rust
    public static boolean rustMatrix4fProjectPoint(
            float[] m, float x, float y, float z,
            float vpX, float vpY, float vpW, float vpH,
            float[] outWindowPos
    ) {
        if (m == null || outWindowPos == null || outWindowPos.length < 3) return false;
        if (rustAvailable && RUST_MATRIX4F_PROJECT_POINT_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment mSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, m);
                MemorySegment wx = arena.allocate(ValueLayout.JAVA_FLOAT);
                MemorySegment wy = arena.allocate(ValueLayout.JAVA_FLOAT);
                MemorySegment wz = arena.allocate(ValueLayout.JAVA_FLOAT);

                int res = (int) RUST_MATRIX4F_PROJECT_POINT_HANDLE.invokeExact(
                        mSeg, x, y, z, vpX, vpY, vpW, vpH, wx, wy, wz
                );
                if (res != 0) {
                    outWindowPos[0] = wx.get(ValueLayout.JAVA_FLOAT, 0);
                    outWindowPos[1] = wy.get(ValueLayout.JAVA_FLOAT, 0);
                    outWindowPos[2] = wz.get(ValueLayout.JAVA_FLOAT, 0);
                    return true;
                }
                return false;
            } catch (Throwable t) {
                LOGGER.warn("Rust matrix projection failed: {}", t.getMessage());
            }
        }
        return false;
    }

    // Full Chunk Section Mesher in Rust
    public static int rustBuildChunkSectionMesh(
            byte[] paddedOpacity,
            byte[] paddedLight,
            short[] blockStates,
            int maxFaces,
            List<ChunkVoxelFace> outFaces
    ) {
        if (paddedOpacity == null || maxFaces <= 0) return 0;
        if (rustAvailable && RUST_BUILD_CHUNK_SECTION_MESH_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment opSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, paddedOpacity);
                MemorySegment litSeg = paddedLight != null ? arena.allocateFrom(ValueLayout.JAVA_BYTE, paddedLight) : MemorySegment.NULL;
                MemorySegment bsSeg = blockStates != null ? arena.allocateFrom(ValueLayout.JAVA_SHORT, blockStates) : MemorySegment.NULL;

                // ChunkVoxelFace struct layout:
                // min_u: u8, min_v: u8, max_u: u8, max_v: u8, slice_coord: u8, direction: u8, block_id: u16
                // ao: [f32; 4], light: [u32; 4] -> total size = 8 + 16 + 16 = 40 bytes (aligned to 4)
                long faceStructSize = 40L;
                MemorySegment outFacesSeg = arena.allocate(faceStructSize * maxFaces);

                int count = (int) RUST_BUILD_CHUNK_SECTION_MESH_HANDLE.invokeExact(
                        opSeg, litSeg, bsSeg, maxFaces, outFacesSeg
                );

                if (outFaces != null) {
                    for (int i = 0; i < count; i++) {
                        long base = i * faceStructSize;
                        int minU = Byte.toUnsignedInt(outFacesSeg.get(ValueLayout.JAVA_BYTE, base + 0));
                        int minV = Byte.toUnsignedInt(outFacesSeg.get(ValueLayout.JAVA_BYTE, base + 1));
                        int maxU = Byte.toUnsignedInt(outFacesSeg.get(ValueLayout.JAVA_BYTE, base + 2));
                        int maxV = Byte.toUnsignedInt(outFacesSeg.get(ValueLayout.JAVA_BYTE, base + 3));
                        int sCoord = Byte.toUnsignedInt(outFacesSeg.get(ValueLayout.JAVA_BYTE, base + 4));
                        int dir = Byte.toUnsignedInt(outFacesSeg.get(ValueLayout.JAVA_BYTE, base + 5));
                        int bId = Short.toUnsignedInt(outFacesSeg.get(ValueLayout.JAVA_SHORT, base + 6));

                        float[] ao = new float[4];
                        for (int c = 0; c < 4; c++) {
                            ao[c] = outFacesSeg.get(ValueLayout.JAVA_FLOAT, base + 8 + c * 4L);
                        }
                        int[] lit = new int[4];
                        for (int c = 0; c < 4; c++) {
                            lit[c] = outFacesSeg.get(ValueLayout.JAVA_INT, base + 24 + c * 4L);
                        }

                        outFaces.add(new ChunkVoxelFace(minU, minV, maxU, maxV, sCoord, dir, bId, ao, lit));
                    }
                }
                return count;
            } catch (Throwable t) {
                LOGGER.warn("Rust chunk section mesher failed: {}", t.getMessage());
            }
        }
        return 0;
    }

    // Translucent depth sort in Rust
    public static void rustSortTranslucentDepth(
            float[] centersX, float[] centersY, float[] centersZ,
            float camX, float camY, float camZ,
            int count,
            int[] outSortedIndices
    ) {
        if (count <= 0 || outSortedIndices == null) return;

        if (rustAvailable && RUST_SORT_TRANSLUCENT_DEPTH_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment cxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, centersX);
                MemorySegment cySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, centersY);
                MemorySegment czSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, centersZ);
                MemorySegment outIdxSeg = arena.allocate(ValueLayout.JAVA_INT, count);

                RUST_SORT_TRANSLUCENT_DEPTH_HANDLE.invokeExact(
                        cxSeg, cySeg, czSeg,
                        camX, camY, camZ,
                        count,
                        outIdxSeg
                );

                MemorySegment.copy(outIdxSeg, ValueLayout.JAVA_INT, 0, outSortedIndices, 0, count);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust depth sort failed: {}", t.getMessage());
            }
        }

        for (int i = 0; i < count; i++) {
            outSortedIndices[i] = i;
        }
    }

    // SIMD Vector Lerp in Rust
    public static void rustBatchLerpVectors(
            float[] prevX, float[] curX, float[] outX,
            float[] prevY, float[] curY, float[] outY,
            float[] prevZ, float[] curZ, float[] outZ,
            float partialTick,
            int count
    ) {
        if (count <= 0 || prevX == null || curX == null || outX == null) return;

        if (rustAvailable && RUST_BATCH_LERP_VECTORS_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, prevX);
                MemorySegment cxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, curX);
                MemorySegment oxSeg = arena.allocate(ValueLayout.JAVA_FLOAT, count);

                MemorySegment pySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, prevY);
                MemorySegment cySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, curY);
                MemorySegment oySeg = arena.allocate(ValueLayout.JAVA_FLOAT, count);

                MemorySegment pzSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, prevZ);
                MemorySegment czSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, curZ);
                MemorySegment ozSeg = arena.allocate(ValueLayout.JAVA_FLOAT, count);

                RUST_BATCH_LERP_VECTORS_HANDLE.invokeExact(
                        pxSeg, cxSeg, oxSeg,
                        pySeg, cySeg, oySeg,
                        pzSeg, czSeg, ozSeg,
                        partialTick,
                        count
                );

                MemorySegment.copy(oxSeg, ValueLayout.JAVA_FLOAT, 0, outX, 0, count);
                MemorySegment.copy(oySeg, ValueLayout.JAVA_FLOAT, 0, outY, 0, count);
                MemorySegment.copy(ozSeg, ValueLayout.JAVA_FLOAT, 0, outZ, 0, count);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust vector lerp failed: {}", t.getMessage());
            }
        }

        for (int i = 0; i < count; i++) {
            outX[i] = prevX[i] + (curX[i] - prevX[i]) * partialTick;
            outY[i] = prevY[i] + (curY[i] - prevY[i]) * partialTick;
            outZ[i] = prevZ[i] + (curZ[i] - prevZ[i]) * partialTick;
        }
    }

    // 2D Greedy Meshing in Rust
    public static int rustGreedyMergeSlice(
            short[] sliceBits,
            int sliceCoord,
            int direction,
            int maxQuads,
            List<MergedQuad> outQuads
    ) {
        if (sliceBits == null || sliceBits.length < 16 || maxQuads <= 0) return 0;

        if (rustAvailable && RUST_GREEDY_MERGE_SLICE_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment bitsSeg = arena.allocateFrom(ValueLayout.JAVA_SHORT, sliceBits);
                MemorySegment quadsSeg = arena.allocate(6L * maxQuads);

                int count = (int) RUST_GREEDY_MERGE_SLICE_HANDLE.invokeExact(
                        bitsSeg,
                        (byte) sliceCoord,
                        (byte) direction,
                        maxQuads,
                        quadsSeg
                );

                if (outQuads != null) {
                    for (int i = 0; i < count; i++) {
                        long offset = i * 6L;
                        int minU = Byte.toUnsignedInt(quadsSeg.get(ValueLayout.JAVA_BYTE, offset + 0));
                        int minV = Byte.toUnsignedInt(quadsSeg.get(ValueLayout.JAVA_BYTE, offset + 1));
                        int maxU = Byte.toUnsignedInt(quadsSeg.get(ValueLayout.JAVA_BYTE, offset + 2));
                        int maxV = Byte.toUnsignedInt(quadsSeg.get(ValueLayout.JAVA_BYTE, offset + 3));
                        int sCoord = Byte.toUnsignedInt(quadsSeg.get(ValueLayout.JAVA_BYTE, offset + 4));
                        int dir = Byte.toUnsignedInt(quadsSeg.get(ValueLayout.JAVA_BYTE, offset + 5));
                        outQuads.add(new MergedQuad(minU, minV, maxU, maxV, sCoord, dir));
                    }
                }
                return count;
            } catch (Throwable t) {
                LOGGER.warn("Rust greedy merge failed: {}", t.getMessage());
            }
        }
        return 0;
    }

    // Occlusion Graph BFS/DFS Solver in Rust
    public static int rustSolveOcclusionGraph(
            int[] coordsX, int[] coordsY, int[] coordsZ,
            long[] passageMasks,
            int sectionCount,
            int camX, int camY, int camZ,
            float[] planesNx, float[] planesNy, float[] planesNz, float[] planesD,
            int[] outVisibleIndices
    ) {
        if (sectionCount <= 0 || coordsX == null || outVisibleIndices == null) return 0;

        if (rustAvailable && RUST_SOLVE_OCCLUSION_GRAPH_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment cxSeg = arena.allocateFrom(ValueLayout.JAVA_INT, coordsX);
                MemorySegment cySeg = arena.allocateFrom(ValueLayout.JAVA_INT, coordsY);
                MemorySegment czSeg = arena.allocateFrom(ValueLayout.JAVA_INT, coordsZ);
                MemorySegment pmSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, passageMasks);

                MemorySegment pnxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNx);
                MemorySegment pnySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNy);
                MemorySegment pnzSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNz);
                MemorySegment pdSeg  = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesD);

                MemorySegment outIdxSeg = arena.allocate(ValueLayout.JAVA_INT, sectionCount);

                int count = (int) RUST_SOLVE_OCCLUSION_GRAPH_HANDLE.invokeExact(
                        cxSeg, cySeg, czSeg,
                        pmSeg, sectionCount,
                        camX, camY, camZ,
                        pnxSeg, pnySeg, pnzSeg, pdSeg,
                        outIdxSeg
                );

                MemorySegment.copy(outIdxSeg, ValueLayout.JAVA_INT, 0, outVisibleIndices, 0, count);
                return count;
            } catch (Throwable t) {
                LOGGER.warn("Rust occlusion solver failed: {}", t.getMessage());
            }
        }

        for (int i = 0; i < sectionCount; i++) {
            outVisibleIndices[i] = i;
        }
        return sectionCount;
    }

    // Ambient Occlusion in Rust
    public static int rustComputeAmbientOcclusion3D(
            byte[] paddedOpacity,
            byte[] paddedLight,
            byte[] quadX,
            byte[] quadY,
            byte[] quadZ,
            byte[] quadDirection,
            int quadCount,
            float[] outCornerAo,
            int[] outCornerLight
    ) {
        if (quadCount <= 0 || paddedOpacity == null || outCornerAo == null) return 0;

        if (rustAvailable && RUST_COMPUTE_AMBIENT_OCCLUSION_3D_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment opSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, paddedOpacity);
                MemorySegment litSeg = (paddedLight != null) ? arena.allocateFrom(ValueLayout.JAVA_BYTE, paddedLight) : MemorySegment.NULL;

                MemorySegment qxSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, quadX);
                MemorySegment qySeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, quadY);
                MemorySegment qzSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, quadZ);
                MemorySegment qdSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, quadDirection);

                MemorySegment aoSeg = arena.allocate(ValueLayout.JAVA_FLOAT, quadCount * 4L);
                MemorySegment outLitSeg = (outCornerLight != null) ? arena.allocate(ValueLayout.JAVA_INT, quadCount * 4L) : MemorySegment.NULL;

                int count = (int) RUST_COMPUTE_AMBIENT_OCCLUSION_3D_HANDLE.invokeExact(
                        opSeg, litSeg,
                        qxSeg, qySeg, qzSeg, qdSeg,
                        quadCount,
                        aoSeg, outLitSeg
                );

                MemorySegment.copy(aoSeg, ValueLayout.JAVA_FLOAT, 0, outCornerAo, 0, quadCount * 4);
                if (outCornerLight != null && !outLitSeg.equals(MemorySegment.NULL)) {
                    MemorySegment.copy(outLitSeg, ValueLayout.JAVA_INT, 0, outCornerLight, 0, quadCount * 4);
                }
                return count;
            } catch (Throwable t) {
                LOGGER.warn("Rust AO engine failed: {}", t.getMessage());
            }
        }

        java.util.Arrays.fill(outCornerAo, 0, quadCount * 4, 1.0f);
        return quadCount;
    }

    // Batch Transform Model Parts in Rust
    public static int rustBatchTransformModelParts(
            float[] matrices,
            float[] minX, float[] minY, float[] minZ,
            float[] maxX, float[] maxY, float[] maxZ,
            int partCount,
            float[] outVx, float[] outVy, float[] outVz
    ) {
        if (partCount <= 0 || matrices == null || outVx == null) return 0;

        if (rustAvailable && RUST_BATCH_TRANSFORM_MODEL_PARTS_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment mSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, matrices);

                MemorySegment minXSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, minX);
                MemorySegment minYSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, minY);
                MemorySegment minZSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, minZ);

                MemorySegment maxXSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, maxX);
                MemorySegment maxYSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, maxY);
                MemorySegment maxZSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, maxZ);

                int vCount = partCount * 24;
                MemorySegment vxSeg = arena.allocate(ValueLayout.JAVA_FLOAT, vCount);
                MemorySegment vySeg = arena.allocate(ValueLayout.JAVA_FLOAT, vCount);
                MemorySegment vzSeg = arena.allocate(ValueLayout.JAVA_FLOAT, vCount);

                int count = (int) RUST_BATCH_TRANSFORM_MODEL_PARTS_HANDLE.invokeExact(
                        mSeg,
                        minXSeg, minYSeg, minZSeg,
                        maxXSeg, maxYSeg, maxZSeg,
                        partCount,
                        vxSeg, vySeg, vzSeg
                );

                MemorySegment.copy(vxSeg, ValueLayout.JAVA_FLOAT, 0, outVx, 0, vCount);
                MemorySegment.copy(vySeg, ValueLayout.JAVA_FLOAT, 0, outVy, 0, vCount);
                MemorySegment.copy(vzSeg, ValueLayout.JAVA_FLOAT, 0, outVz, 0, vCount);
                return count;
            } catch (Throwable t) {
                LOGGER.warn("Rust model transformer failed: {}", t.getMessage());
            }
        }
        return 0;
    }

    // SSE/AVX 4x4 Matrix Multiplication in Rust
    public static void rustMatrix4fMul(float[] a, float[] b, float[] out) {
        if (a == null || b == null || out == null || a.length < 16 || b.length < 16 || out.length < 16) return;

        if (rustAvailable && RUST_MATRIX4F_MUL_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment aSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, a);
                MemorySegment bSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, b);
                MemorySegment outSeg = arena.allocate(ValueLayout.JAVA_FLOAT, 16);

                RUST_MATRIX4F_MUL_HANDLE.invokeExact(aSeg, bSeg, outSeg);
                MemorySegment.copy(outSeg, ValueLayout.JAVA_FLOAT, 0, out, 0, 16);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust matrix mul failed: {}", t.getMessage());
            }
        }

        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                out[col * 4 + row] = sum;
            }
        }
    }

    // 4x4 Matrix Inversion in Rust
    public static boolean rustMatrix4fInvert(float[] in, float[] out) {
        if (in == null || out == null || in.length < 16 || out.length < 16) return false;

        if (rustAvailable && RUST_MATRIX4F_INVERT_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, in);
                MemorySegment outSeg = arena.allocate(ValueLayout.JAVA_FLOAT, 16);

                int res = (int) RUST_MATRIX4F_INVERT_HANDLE.invokeExact(inSeg, outSeg);
                if (res != 0) {
                    MemorySegment.copy(outSeg, ValueLayout.JAVA_FLOAT, 0, out, 0, 16);
                    return true;
                }
                return false;
            } catch (Throwable t) {
                LOGGER.warn("Rust matrix invert failed: {}", t.getMessage());
            }
        }
        return false;
    }

    // Frustum Box Test in Rust
    public static boolean rustFrustumTestBox(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            float[] planesNx, float[] planesNy, float[] planesNz, float[] planesD
    ) {
        if (planesNx == null || planesNy == null || planesNz == null || planesD == null) return true;

        if (rustAvailable && RUST_FRUSTUM_TEST_BOX_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pnxSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNx);
                MemorySegment pnySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNy);
                MemorySegment pnzSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesNz);
                MemorySegment pdSeg  = arena.allocateFrom(ValueLayout.JAVA_FLOAT, planesD);

                int res = (int) RUST_FRUSTUM_TEST_BOX_HANDLE.invokeExact(
                        minX, minY, minZ,
                        maxX, maxY, maxZ,
                        pnxSeg, pnySeg, pnzSeg, pdSeg
                );
                return res != 0;
            } catch (Throwable t) {
                LOGGER.warn("Rust frustum test failed: {}", t.getMessage());
            }
        }

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float ex = (maxX - minX) * 0.5f;
        float ey = (maxY - minY) * 0.5f;
        float ez = (maxZ - minZ) * 0.5f;

        for (int p = 0; p < 6; p++) {
            float dist = cx * planesNx[p] + cy * planesNy[p] + cz * planesNz[p] + planesD[p];
            float radius = ex * Math.abs(planesNx[p]) + ey * Math.abs(planesNy[p]) + ez * Math.abs(planesNz[p]);
            if (dist < -radius) {
                return false;
            }
        }
        return true;
    }

    // Distance Cull Batch in Rust
    public static void rustBatchDistanceCull(
            double[] cx, double[] cy, double[] cz,
            double camX, double camY, double camZ,
            double maxDistSq,
            int count,
            long[] outBits
    ) {
        if (count <= 0 || outBits == null || cx == null) return;

        if (rustAvailable && RUST_BATCH_DISTANCE_CULL_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment cxSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, cx);
                MemorySegment cySeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, cy);
                MemorySegment czSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, cz);

                int numWords = (count + 63) / 64;
                MemorySegment bitsSeg = arena.allocate(ValueLayout.JAVA_LONG, numWords);

                RUST_BATCH_DISTANCE_CULL_HANDLE.invokeExact(
                        cxSeg, cySeg, czSeg,
                        camX, camY, camZ,
                        maxDistSq,
                        count,
                        bitsSeg
                );

                MemorySegment.copy(bitsSeg, ValueLayout.JAVA_LONG, 0, outBits, 0, numWords);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust batch distance cull failed: {}", t.getMessage());
            }
        }

        int numWords = (count + 63) / 64;
        for (int w = 0; w < numWords; ++w) {
            outBits[w] = 0L;
        }

        for (int i = 0; i < count; ++i) {
            double dx = cx[i] - camX;
            double dy = cy[i] - camY;
            double dz = cz[i] - camZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= maxDistSq) {
                int wordIdx = i / 64;
                int bitOffset = i % 64;
                outBits[wordIdx] |= (1L << bitOffset);
            }
        }
    }

    // Fast Lightmap SIMD in Rust
    public static void rustFastLightmap(
            float ambient,
            float skyFactor,
            float blockFactor,
            float gamma,
            boolean isNightVision,
            int[] outRgba
    ) {
        if (outRgba == null || outRgba.length < 256) return;

        if (rustAvailable && RUST_FAST_LIGHTMAP_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment rgbaSeg = arena.allocate(ValueLayout.JAVA_INT, 256);

                RUST_FAST_LIGHTMAP_HANDLE.invokeExact(
                        ambient,
                        skyFactor,
                        blockFactor,
                        gamma,
                        isNightVision ? 1 : 0,
                        rgbaSeg
                );

                MemorySegment.copy(rgbaSeg, ValueLayout.JAVA_INT, 0, outRgba, 0, 256);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust lightmap failed: {}", t.getMessage());
            }
        }

        for (int sky = 0; sky < 16; sky++) {
            float sVal = (sky / 15.0f) * skyFactor;
            for (int block = 0; block < 16; block++) {
                float bVal = (block / 15.0f) * blockFactor;
                float r = Math.min(1.0f, bVal * 1.2f + sVal * 0.9f + ambient);
                float g = Math.min(1.0f, bVal * 1.0f + sVal * 0.95f + ambient);
                float b = Math.min(1.0f, bVal * 0.8f + sVal * 1.1f + ambient);

                int ir = (int) (r * 255.0f);
                int ig = (int) (g * 255.0f);
                int ib = (int) (b * 255.0f);

                outRgba[sky * 16 + block] = 0xFF000000 | (ib << 16) | (ig << 8) | ir;
            }
        }
    }

    // Batch Sin & Cos in Rust
    public static void rustBatchSinCos(float[] inAngles, int count, float[] outSin, float[] outCos) {
        if (count <= 0 || inAngles == null || outSin == null || outCos == null) return;

        if (rustAvailable && RUST_BATCH_SIN_COS_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, inAngles);
                MemorySegment sinSeg = arena.allocate(ValueLayout.JAVA_FLOAT, count);
                MemorySegment cosSeg = arena.allocate(ValueLayout.JAVA_FLOAT, count);

                RUST_BATCH_SIN_COS_HANDLE.invokeExact(inSeg, count, sinSeg, cosSeg);

                MemorySegment.copy(sinSeg, ValueLayout.JAVA_FLOAT, 0, outSin, 0, count);
                MemorySegment.copy(cosSeg, ValueLayout.JAVA_FLOAT, 0, outCos, 0, count);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust sin cos failed: {}", t.getMessage());
            }
        }

        for (int i = 0; i < count; i++) {
            outSin[i] = (float) Math.sin(inAngles[i]);
            outCos[i] = (float) Math.cos(inAngles[i]);
        }
    }

    // Fast Inverse Sqrt Batch in Rust
    public static void rustFastInvSqrtBatch(float[] inValues, int count, float[] outValues) {
        if (count <= 0 || inValues == null || outValues == null) return;
        if (rustAvailable && RUST_FAST_INV_SQRT_BATCH_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, inValues);
                MemorySegment outSeg = arena.allocate(ValueLayout.JAVA_FLOAT, count);

                RUST_FAST_INV_SQRT_BATCH_HANDLE.invokeExact(inSeg, count, outSeg);
                MemorySegment.copy(outSeg, ValueLayout.JAVA_FLOAT, 0, outValues, 0, count);
                return;
            } catch (Throwable t) {
                LOGGER.warn("Rust inv sqrt failed: {}", t.getMessage());
            }
        }
        for (int i = 0; i < count; i++) {
            outValues[i] = 1.0f / (float) Math.sqrt(inValues[i]);
        }
    }
}
