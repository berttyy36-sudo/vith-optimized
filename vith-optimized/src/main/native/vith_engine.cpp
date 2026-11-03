#include <immintrin.h>
#include <cstdint>
#include <cmath>
#include <algorithm>

#if defined(_MSC_VER)
#define VITH_EXPORT __declspec(dllexport)
#else
#define VITH_EXPORT __attribute__((visibility("default")))
#endif

extern "C" {

VITH_EXPORT uint32_t vith_get_simd_features() {
    uint32_t features = 0;
#if defined(__AVX2__) || defined(_M_AMD64)
    features |= 1;
#endif
#if defined(__FMA__) || defined(_M_AMD64)
    features |= 2;
#endif
    return features;
}

VITH_EXPORT void vith_cull_entities_avx2(
    const float* __restrict centers_x,
    const float* __restrict centers_y,
    const float* __restrict centers_z,
    const float* __restrict extents_x,
    const float* __restrict extents_y,
    const float* __restrict extents_z,
    const float* __restrict frustum_nx,
    const float* __restrict frustum_ny,
    const float* __restrict frustum_nz,
    const float* __restrict frustum_d,
    int32_t count,
    uint64_t* __restrict out_visibility_bits
) {
    if (count <= 0 || !out_visibility_bits) return;

    int32_t numWords = (count + 63) / 64;
    for (int32_t w = 0; w < numWords; ++w) {
        out_visibility_bits[w] = 0ULL;
    }

    int32_t i = 0;

    for (; i <= count - 8; i += 8) {

        _mm_prefetch((const char*)&centers_x[i + 16], _MM_HINT_T0);
        _mm_prefetch((const char*)&centers_y[i + 16], _MM_HINT_T0);
        _mm_prefetch((const char*)&centers_z[i + 16], _MM_HINT_T0);
        _mm_prefetch((const char*)&extents_x[i + 16], _MM_HINT_T0);
        _mm_prefetch((const char*)&extents_y[i + 16], _MM_HINT_T0);
        _mm_prefetch((const char*)&extents_z[i + 16], _MM_HINT_T0);

        __m256 cx = _mm256_loadu_ps(&centers_x[i]);
        __m256 cy = _mm256_loadu_ps(&centers_y[i]);
        __m256 cz = _mm256_loadu_ps(&centers_z[i]);

        __m256 ex = _mm256_loadu_ps(&extents_x[i]);
        __m256 ey = _mm256_loadu_ps(&extents_y[i]);
        __m256 ez = _mm256_loadu_ps(&extents_z[i]);

        __m256 outside_any = _mm256_setzero_ps();

        for (int32_t p = 0; p < 6; ++p) {
            __m256 pnx = _mm256_set1_ps(frustum_nx[p]);
            __m256 pny = _mm256_set1_ps(frustum_ny[p]);
            __m256 pnz = _mm256_set1_ps(frustum_nz[p]);
            __m256 pd  = _mm256_set1_ps(frustum_d[p]);

            __m256 dist = _mm256_fmadd_ps(cx, pnx, _mm256_fmadd_ps(cy, pny, _mm256_fmadd_ps(cz, pnz, pd)));

            __m256 abs_pnx = _mm256_andnot_ps(_mm256_set1_ps(-0.0f), pnx);
            __m256 abs_pny = _mm256_andnot_ps(_mm256_set1_ps(-0.0f), pny);
            __m256 abs_pnz = _mm256_andnot_ps(_mm256_set1_ps(-0.0f), pnz);
            __m256 radius = _mm256_fmadd_ps(ex, abs_pnx, _mm256_fmadd_ps(ey, abs_pny, _mm256_mul_ps(ez, abs_pnz)));

            __m256 neg_radius = _mm256_sub_ps(_mm256_setzero_ps(), radius);
            __m256 is_outside = _mm256_cmp_ps(dist, neg_radius, _CMP_LT_OQ);
            outside_any = _mm256_or_ps(outside_any, is_outside);
        }

        int32_t outMask = _mm256_movemask_ps(outside_any);
        uint8_t insideMask = static_cast<uint8_t>(~outMask & 0xFF);

        int32_t wordIdx = i / 64;
        int32_t bitOffset = i % 64;
        out_visibility_bits[wordIdx] |= (static_cast<uint64_t>(insideMask) << bitOffset);
    }

    for (; i < count; ++i) {
        float cx = centers_x[i];
        float cy = centers_y[i];
        float cz = centers_z[i];
        float ex = extents_x[i];
        float ey = extents_y[i];
        float ez = extents_z[i];

        bool inside = true;
        for (int32_t p = 0; p < 6; ++p) {
            float dist = cx * frustum_nx[p] + cy * frustum_ny[p] + cz * frustum_nz[p] + frustum_d[p];
            float radius = ex * std::fabs(frustum_nx[p]) + ey * std::fabs(frustum_ny[p]) + ez * std::fabs(frustum_nz[p]);
            if (dist < -radius) {
                inside = false;
                break;
            }
        }

        if (inside) {
            int32_t wordIdx = i / 64;
            int32_t bitOffset = i % 64;
            out_visibility_bits[wordIdx] |= (1ULL << bitOffset);
        }
    }
}

VITH_EXPORT double vith_raycast_aabb_batch_avx2(
    double ray_ox, double ray_oy, double ray_oz,
    double ray_dx, double ray_dy, double ray_dz,
    const double* __restrict min_x, const double* __restrict min_y, const double* __restrict min_z,
    const double* __restrict max_x, const double* __restrict max_y, const double* __restrict max_z,
    int32_t box_count,
    double max_dist,
    int32_t* __restrict out_hit_index
) {
    if (box_count <= 0) return -1.0;

    double inv_dx = (std::abs(ray_dx) > 1e-12) ? (1.0 / ray_dx) : 1e12;
    double inv_dy = (std::abs(ray_dy) > 1e-12) ? (1.0 / ray_dy) : 1e12;
    double inv_dz = (std::abs(ray_dz) > 1e-12) ? (1.0 / ray_dz) : 1e12;

    double closest_hit = max_dist;
    int32_t best_index = -1;

    for (int32_t i = 0; i < box_count; ++i) {
        double t1 = (min_x[i] - ray_ox) * inv_dx;
        double t2 = (max_x[i] - ray_ox) * inv_dx;
        double t3 = (min_y[i] - ray_oy) * inv_dy;
        double t4 = (max_y[i] - ray_oy) * inv_dy;
        double t5 = (min_z[i] - ray_oz) * inv_dz;
        double t6 = (max_z[i] - ray_oz) * inv_dz;

        double tmin = std::max(std::max(std::min(t1, t2), std::min(t3, t4)), std::min(t5, t6));
        double tmax = std::min(std::min(std::max(t1, t2), std::max(t3, t4)), std::max(t5, t6));

        if (tmax >= 0.0 && tmin <= tmax && tmin < closest_hit) {
            closest_hit = tmin;
            best_index = i;
        }
    }

    if (out_hit_index) {
        *out_hit_index = best_index;
    }
    return (best_index != -1) ? closest_hit : -1.0;
}

struct NativeVertex {
    float x, y, z;
    uint32_t color;
    float u, v;
    uint32_t overlay;
    uint32_t light;
    uint32_t normal;
};

VITH_EXPORT void vith_transform_quads_avx2(
    const float* __restrict in_pos,
    const float* __restrict in_uvs,
    const float* __restrict matrix4x4,
    uint32_t color,
    uint32_t overlay,
    uint32_t light,
    uint32_t packed_normal,
    int32_t quad_count,
    NativeVertex* __restrict out_vertices
) {
    if (quad_count <= 0 || !in_pos || !in_uvs || !matrix4x4 || !out_vertices) return;

    float m00 = matrix4x4[0],  m01 = matrix4x4[4],  m02 = matrix4x4[8],  m03 = matrix4x4[12];
    float m10 = matrix4x4[1],  m11 = matrix4x4[5],  m12 = matrix4x4[9],  m13 = matrix4x4[13];
    float m20 = matrix4x4[2],  m21 = matrix4x4[6],  m22 = matrix4x4[10], m23 = matrix4x4[14];

    for (int32_t q = 0; q < quad_count; ++q) {
        NativeVertex* quad_out = &out_vertices[q * 4];

        for (int32_t v = 0; v < 4; ++v) {
            int32_t pos_idx = (q * 4 + v) * 3;
            int32_t uv_idx  = (q * 4 + v) * 2;

            float vx = in_pos[pos_idx + 0];
            float vy = in_pos[pos_idx + 1];
            float vz = in_pos[pos_idx + 2];

            quad_out[v].x = m00 * vx + m01 * vy + m02 * vz + m03;
            quad_out[v].y = m10 * vx + m11 * vy + m12 * vz + m13;
            quad_out[v].z = m20 * vx + m21 * vy + m22 * vz + m23;

            quad_out[v].color   = color;
            quad_out[v].u       = in_uvs[uv_idx + 0];
            quad_out[v].v       = in_uvs[uv_idx + 1];
            quad_out[v].overlay = overlay;
            quad_out[v].light   = light;
            quad_out[v].normal  = packed_normal;
        }
    }
}

VITH_EXPORT void vith_mesh_section_faces_avx2(
    const uint8_t* __restrict block_opacity,
    uint64_t* __restrict out_face_bits,
    int32_t* __restrict out_face_counts
) {
    if (!block_opacity || !out_face_bits || !out_face_counts) return;

    for (int32_t d = 0; d < 6; ++d) {
        out_face_counts[d] = 0;
        for (int32_t w = 0; w < 64; ++w) {
            out_face_bits[d * 64 + w] = 0ULL;
        }
    }

    for (int32_t y = 0; y < 16; ++y) {
        for (int32_t z = 0; z < 16; ++z) {
            int32_t base_idx = (y * 256) + (z * 16);
            for (int32_t x = 0; x < 16; ++x) {
                int32_t idx = base_idx + x;
                uint8_t current = block_opacity[idx];
                if (current == 0) continue;

                int32_t word_idx = idx / 64;
                uint64_t bit = 1ULL << (idx % 64);

                if (y == 0 || block_opacity[idx - 256] == 0) {
                    out_face_bits[0 * 64 + word_idx] |= bit;
                    out_face_counts[0]++;
                }

                if (y == 15 || block_opacity[idx + 256] == 0) {
                    out_face_bits[1 * 64 + word_idx] |= bit;
                    out_face_counts[1]++;
                }

                if (z == 0 || block_opacity[idx - 16] == 0) {
                    out_face_bits[2 * 64 + word_idx] |= bit;
                    out_face_counts[2]++;
                }

                if (z == 15 || block_opacity[idx + 16] == 0) {
                    out_face_bits[3 * 64 + word_idx] |= bit;
                    out_face_counts[3]++;
                }

                if (x == 0 || block_opacity[idx - 1] == 0) {
                    out_face_bits[4 * 64 + word_idx] |= bit;
                    out_face_counts[4]++;
                }

                if (x == 15 || block_opacity[idx + 1] == 0) {
                    out_face_bits[5 * 64 + word_idx] |= bit;
                    out_face_counts[5]++;
                }
            }
        }
    }
}

VITH_EXPORT void vith_batch_aabb_overlap_avx2(
    double q_minx, double q_miny, double q_minz,
    double q_maxx, double q_maxy, double q_maxz,
    const double* __restrict min_x,
    const double* __restrict min_y,
    const double* __restrict min_z,
    const double* __restrict max_x,
    const double* __restrict max_y,
    const double* __restrict max_z,
    int32_t count,
    uint64_t* __restrict out_overlap_bits
) {
    if (count <= 0 || !out_overlap_bits) return;

    int32_t numWords = (count + 63) / 64;
    for (int32_t w = 0; w < numWords; ++w) {
        out_overlap_bits[w] = 0ULL;
    }

    __m256d q_minx_v = _mm256_set1_pd(q_minx);
    __m256d q_miny_v = _mm256_set1_pd(q_miny);
    __m256d q_minz_v = _mm256_set1_pd(q_minz);
    __m256d q_maxx_v = _mm256_set1_pd(q_maxx);
    __m256d q_maxy_v = _mm256_set1_pd(q_maxy);
    __m256d q_maxz_v = _mm256_set1_pd(q_maxz);

    int32_t i = 0;

    for (; i <= count - 4; i += 4) {

        _mm_prefetch((const char*)&min_x[i + 8], _MM_HINT_T0);
        _mm_prefetch((const char*)&min_y[i + 8], _MM_HINT_T0);
        _mm_prefetch((const char*)&min_z[i + 8], _MM_HINT_T0);
        _mm_prefetch((const char*)&max_x[i + 8], _MM_HINT_T0);
        _mm_prefetch((const char*)&max_y[i + 8], _MM_HINT_T0);
        _mm_prefetch((const char*)&max_z[i + 8], _MM_HINT_T0);

        __m256d t_minx = _mm256_loadu_pd(&min_x[i]);
        __m256d t_miny = _mm256_loadu_pd(&min_y[i]);
        __m256d t_minz = _mm256_loadu_pd(&min_z[i]);
        __m256d t_maxx = _mm256_loadu_pd(&max_x[i]);
        __m256d t_maxy = _mm256_loadu_pd(&max_y[i]);
        __m256d t_maxz = _mm256_loadu_pd(&max_z[i]);

        __m256d ox1 = _mm256_cmp_pd(q_minx_v, t_maxx, _CMP_LT_OQ);
        __m256d ox2 = _mm256_cmp_pd(q_maxx_v, t_minx, _CMP_GT_OQ);
        __m256d oy1 = _mm256_cmp_pd(q_miny_v, t_maxy, _CMP_LT_OQ);
        __m256d oy2 = _mm256_cmp_pd(q_maxy_v, t_miny, _CMP_GT_OQ);
        __m256d oz1 = _mm256_cmp_pd(q_minz_v, t_maxz, _CMP_LT_OQ);
        __m256d oz2 = _mm256_cmp_pd(q_maxz_v, t_minz, _CMP_GT_OQ);

        __m256d ox = _mm256_and_pd(ox1, ox2);
        __m256d oy = _mm256_and_pd(oy1, oy2);
        __m256d oz = _mm256_and_pd(oz1, oz2);
        __m256d overlap = _mm256_and_pd(ox, _mm256_and_pd(oy, oz));

        int32_t mask = _mm256_movemask_pd(overlap);
        if (mask != 0) {
            int32_t word_idx = i / 64;
            int32_t bit_offset = i % 64;
            out_overlap_bits[word_idx] |= ((uint64_t) mask) << bit_offset;
        }
    }

    for (; i < count; ++i) {
        if (q_minx < max_x[i] && q_maxx > min_x[i] &&
            q_miny < max_y[i] && q_maxy > min_y[i] &&
            q_minz < max_z[i] && q_maxz > min_z[i]) {
            int32_t word_idx = i / 64;
            int32_t bit_offset = i % 64;
            out_overlap_bits[word_idx] |= (1ULL << bit_offset);
        }
    }
}

}
