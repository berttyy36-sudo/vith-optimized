package vithh.vithOptimized.nativeopt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class NativeAccelerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("VithOptimized-Native");

    private static boolean available = false;
    private static uintFeatures features = new uintFeatures(0);

    private static MethodHandle CULL_ENTITIES_HANDLE;
    private static MethodHandle RAYCAST_BATCH_HANDLE;
    private static MethodHandle TRANSFORM_QUADS_HANDLE;
    private static MethodHandle MESH_SECTION_FACES_HANDLE;
    private static MethodHandle BATCH_AABB_OVERLAP_HANDLE;
    private static MethodHandle GET_FEATURES_HANDLE;

    private static boolean rustAvailable = false;
    private static int rustVersion = 0;
    private static MethodHandle RUST_GET_VERSION_HANDLE;
    private static MethodHandle RUST_CULL_ENTITIES_HANDLE;
    private static MethodHandle RUST_MESH_SECTION_FACES_HANDLE;
    private static MethodHandle RUST_BATCH_AABB_OVERLAP_HANDLE;
    private static MethodHandle RUST_GREEDY_MERGE_SLICE_HANDLE;
    private static MethodHandle RUST_SORT_TRANSLUCENT_DEPTH_HANDLE;
    private static MethodHandle RUST_BATCH_LERP_VECTORS_HANDLE;
    private static MethodHandle RUST_SOLVE_OCCLUSION_GRAPH_HANDLE;
    private static MethodHandle RUST_COMPUTE_AMBIENT_OCCLUSION_3D_HANDLE;
    private static MethodHandle RUST_BATCH_TRANSFORM_MODEL_PARTS_HANDLE;

    public record MergedQuad(int minU, int minV, int maxU, int maxV, int sliceCoord, int direction) {}

    public record uintFeatures(int bitmask) {
        public boolean hasAvx2() { return (bitmask & 1) != 0; }
        public boolean hasFma()  { return (bitmask & 2) != 0; }
    }

    static {
        try {
            loadNativeLibrary();
        } catch (Throwable t) {
            LOGGER.warn("Native accelerator could not be initialized: {}", t.getMessage());
            available = false;
        }
    }

    private NativeAccelerator() {}

    private static void loadNativeLibrary() throws Throwable {
        String dllName = "vith_engine.dll";
        File nativeDir = new File("vith/natives");
        if (!nativeDir.exists()) {
            nativeDir.mkdirs();
        }
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
            throw new IllegalStateException("Native library file not found: " + targetDll.getAbsolutePath());
        }

        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.libraryLookup(targetDll.toPath(), Arena.global());

        var featSym = lookup.find("vith_get_simd_features").orElseThrow();
        GET_FEATURES_HANDLE = linker.downcallHandle(featSym, FunctionDescriptor.of(ValueLayout.JAVA_INT));
        int featVal = (int) GET_FEATURES_HANDLE.invokeExact();
        features = new uintFeatures(featVal);

        var cullSym = lookup.find("vith_cull_entities_avx2").orElseThrow();
        CULL_ENTITIES_HANDLE = linker.downcallHandle(
                cullSym,
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        );

        var raySym = lookup.find("vith_raycast_aabb_batch_avx2").orElseThrow();
        RAYCAST_BATCH_HANDLE = linker.downcallHandle(
                raySym,
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

        var meshSym = lookup.find("vith_mesh_section_faces_avx2").orElseThrow();
        MESH_SECTION_FACES_HANDLE = linker.downcallHandle(
                meshSym,
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );

        var overlapSym = lookup.find("vith_batch_aabb_overlap_avx2").orElseThrow();
        BATCH_AABB_OVERLAP_HANDLE = linker.downcallHandle(
                overlapSym,
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        );

        available = true;
        LOGGER.info("Native C++ AVX2 Accelerator initialized successfully (AVX2={}, FMA={})", features.hasAvx2(), features.hasFma());

        try {
            loadRustLibrary(linker, nativeDir);
        } catch (Throwable t) {
            LOGGER.warn("Rust Engine initialization failed: {}", t.getMessage());
            rustAvailable = false;
        }
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

        rustAvailable = true;
        LOGGER.info("Rust Engine initialized successfully (vith_rust_engine v{})", rustVersion);
    }

    public static boolean isAvailable() {
        return available;
    }

    public static uintFeatures getFeatures() {
        return features;
    }

    public static void cullEntitiesSimd(
            float[] centersX, float[] centersY, float[] centersZ,
            float[] extentsX, float[] extentsY, float[] extentsZ,
            float[] planesNx, float[] planesNy, float[] planesNz, float[] planesD,
            int count,
            long[] outVisibilityBits
    ) {
        if (count <= 0 || outVisibilityBits == null) return;

        if (available) {
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

                CULL_ENTITIES_HANDLE.invokeExact(
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

    public static double raycastBatchSimd(
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            double[] minX, double[] minY, double[] minZ,
            double[] maxX, double[] maxY, double[] maxZ,
            int boxCount,
            double maxDist
    ) {
        if (boxCount <= 0) return -1.0;

        if (available) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment minXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minX);
                MemorySegment minYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minY);
                MemorySegment minZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minZ);

                MemorySegment maxXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxX);
                MemorySegment maxYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxY);
                MemorySegment maxZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxZ);

                MemorySegment hitIdxSeg = arena.allocate(ValueLayout.JAVA_INT);

                return (double) RAYCAST_BATCH_HANDLE.invokeExact(
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

    public static void meshSectionFacesSimd(
            byte[] blockOpacity,
            long[] outFaceBits,
            int[] outFaceCounts
    ) {
        if (blockOpacity == null || outFaceBits == null || outFaceCounts == null) return;
        if (blockOpacity.length < 4096 || outFaceBits.length < 384 || outFaceCounts.length < 6) return;

        if (available && MESH_SECTION_FACES_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment opSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, blockOpacity);
                MemorySegment bitsSeg = arena.allocate(ValueLayout.JAVA_LONG, 384);
                MemorySegment countsSeg = arena.allocate(ValueLayout.JAVA_INT, 6);

                MESH_SECTION_FACES_HANDLE.invokeExact(opSeg, bitsSeg, countsSeg);

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

    public static void batchAabbOverlapSimd(
            double qMinX, double qMinY, double qMinZ,
            double qMaxX, double qMaxY, double qMaxZ,
            double[] minX, double[] minY, double[] minZ,
            double[] maxX, double[] maxY, double[] maxZ,
            int count,
            long[] outOverlapBits
    ) {
        if (count <= 0 || outOverlapBits == null) return;

        if (available && BATCH_AABB_OVERLAP_HANDLE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment minXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minX);
                MemorySegment minYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minY);
                MemorySegment minZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, minZ);
                MemorySegment maxXSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxX);
                MemorySegment maxYSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxY);
                MemorySegment maxZSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, maxZ);

                int numWords = (count + 63) / 64;
                MemorySegment outBitsSeg = arena.allocate(ValueLayout.JAVA_LONG, numWords);

                BATCH_AABB_OVERLAP_HANDLE.invokeExact(
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

    public static boolean isRustAvailable() {
        return rustAvailable;
    }

    public static int getRustVersion() {
        return rustVersion;
    }

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

    public static int rustGreedyMergeSlice(
            short[] sliceBits,
            int sliceCoord,
            int direction,
            int maxQuads,
            java.util.List<MergedQuad> outQuads
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
}
