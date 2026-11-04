#![allow(non_snake_case)]

#[cfg(target_arch = "x86_64")]
use std::arch::x86_64::*;

#[no_mangle]
pub extern "C" fn vith_rust_get_version() -> u32 {
    400
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_cull_entities_simd(
    centers_x: *const f32,
    centers_y: *const f32,
    centers_z: *const f32,
    extents_x: *const f32,
    extents_y: *const f32,
    extents_z: *const f32,
    frustum_nx: *const f32,
    frustum_ny: *const f32,
    frustum_nz: *const f32,
    frustum_d: *const f32,
    count: i32,
    out_visibility_bits: *mut u64,
) {
    if count <= 0 || out_visibility_bits.is_null() {
        return;
    }

    let num_words = ((count + 63) / 64) as usize;
    for w in 0..num_words {
        *out_visibility_bits.add(w) = 0;
    }

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            let mut i = 0i32;
            while i <= count - 8 {
                _mm_prefetch(centers_x.offset((i + 16) as isize) as *const i8, _MM_HINT_T0);
                _mm_prefetch(centers_y.offset((i + 16) as isize) as *const i8, _MM_HINT_T0);
                _mm_prefetch(centers_z.offset((i + 16) as isize) as *const i8, _MM_HINT_T0);
                _mm_prefetch(extents_x.offset((i + 16) as isize) as *const i8, _MM_HINT_T0);
                _mm_prefetch(extents_y.offset((i + 16) as isize) as *const i8, _MM_HINT_T0);
                _mm_prefetch(extents_z.offset((i + 16) as isize) as *const i8, _MM_HINT_T0);

                let cx = _mm256_loadu_ps(centers_x.offset(i as isize));
                let cy = _mm256_loadu_ps(centers_y.offset(i as isize));
                let cz = _mm256_loadu_ps(centers_z.offset(i as isize));

                let ex = _mm256_loadu_ps(extents_x.offset(i as isize));
                let ey = _mm256_loadu_ps(extents_y.offset(i as isize));
                let ez = _mm256_loadu_ps(extents_z.offset(i as isize));

                let mut outside_any = _mm256_setzero_ps();

                for p in 0..6 {
                    let pnx = _mm256_set1_ps(*frustum_nx.add(p));
                    let pny = _mm256_set1_ps(*frustum_ny.add(p));
                    let pnz = _mm256_set1_ps(*frustum_nz.add(p));
                    let pd  = _mm256_set1_ps(*frustum_d.add(p));

                    let dist = _mm256_fmadd_ps(cx, pnx, _mm256_fmadd_ps(cy, pny, _mm256_fmadd_ps(cz, pnz, pd)));

                    let abs_pnx = _mm256_andnot_ps(_mm256_set1_ps(-0.0), pnx);
                    let abs_pny = _mm256_andnot_ps(_mm256_set1_ps(-0.0), pny);
                    let abs_pnz = _mm256_andnot_ps(_mm256_set1_ps(-0.0), pnz);
                    let radius = _mm256_fmadd_ps(ex, abs_pnx, _mm256_fmadd_ps(ey, abs_pny, _mm256_mul_ps(ez, abs_pnz)));

                    let neg_radius = _mm256_sub_ps(_mm256_setzero_ps(), radius);
                    let is_outside = _mm256_cmp_ps(dist, neg_radius, _CMP_LT_OQ);
                    outside_any = _mm256_or_ps(outside_any, is_outside);
                }

                let out_mask = _mm256_movemask_ps(outside_any);
                let inside_mask = (!out_mask & 0xFF) as u8;

                let word_idx = (i / 64) as usize;
                let bit_offset = (i % 64) as usize;
                *out_visibility_bits.add(word_idx) |= (inside_mask as u64) << bit_offset;

                i += 8;
            }

            while i < count {
                let idx = i as isize;
                let cx = *centers_x.offset(idx);
                let cy = *centers_y.offset(idx);
                let cz = *centers_z.offset(idx);
                let ex = *extents_x.offset(idx);
                let ey = *extents_y.offset(idx);
                let ez = *extents_z.offset(idx);

                let mut inside = true;
                for p in 0..6 {
                    let pnx = *frustum_nx.add(p);
                    let pny = *frustum_ny.add(p);
                    let pnz = *frustum_nz.add(p);
                    let pd  = *frustum_d.add(p);

                    let dist = cx * pnx + cy * pny + cz * pnz + pd;
                    let radius = ex * pnx.abs() + ey * pny.abs() + ez * pnz.abs();
                    if dist < -radius {
                        inside = false;
                        break;
                    }
                }

                if inside {
                    let word_idx = (i / 64) as usize;
                    let bit_offset = (i % 64) as usize;
                    *out_visibility_bits.add(word_idx) |= 1u64 << bit_offset;
                }
                i += 1;
            }
            return;
        }
    }

    for i in 0..count {
        let idx = i as isize;
        let cx = *centers_x.offset(idx);
        let cy = *centers_y.offset(idx);
        let cz = *centers_z.offset(idx);
        let ex = *extents_x.offset(idx);
        let ey = *extents_y.offset(idx);
        let ez = *extents_z.offset(idx);

        let mut inside = true;
        for p in 0..6 {
            let pnx = *frustum_nx.add(p);
            let pny = *frustum_ny.add(p);
            let pnz = *frustum_nz.add(p);
            let pd  = *frustum_d.add(p);

            let dist = cx * pnx + cy * pny + cz * pnz + pd;
            let radius = ex * pnx.abs() + ey * pny.abs() + ez * pnz.abs();
            if dist < -radius {
                inside = false;
                break;
            }
        }

        if inside {
            let word_idx = (i / 64) as usize;
            let bit_offset = (i % 64) as usize;
            *out_visibility_bits.add(word_idx) |= 1u64 << bit_offset;
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_raycast_aabb_batch_simd(
    ray_ox: f64, ray_oy: f64, ray_oz: f64,
    ray_dx: f64, ray_dy: f64, ray_dz: f64,
    min_x: *const f64, min_y: *const f64, min_z: *const f64,
    max_x: *const f64, max_y: *const f64, max_z: *const f64,
    box_count: i32,
    max_dist: f64,
    out_hit_index: *mut i32,
) -> f64 {
    if box_count <= 0 || min_x.is_null() || max_x.is_null() {
        if !out_hit_index.is_null() {
            *out_hit_index = -1;
        }
        return -1.0;
    }

    let inv_dx = if ray_dx.abs() > 1e-12 { 1.0 / ray_dx } else { 1e12 };
    let inv_dy = if ray_dy.abs() > 1e-12 { 1.0 / ray_dy } else { 1e12 };
    let inv_dz = if ray_dz.abs() > 1e-12 { 1.0 / ray_dz } else { 1e12 };

    let mut closest_hit = max_dist;
    let mut best_index = -1i32;

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") {
            let v_ox = _mm256_set1_pd(ray_ox);
            let v_oy = _mm256_set1_pd(ray_oy);
            let v_oz = _mm256_set1_pd(ray_oz);

            let v_inv_dx = _mm256_set1_pd(inv_dx);
            let v_inv_dy = _mm256_set1_pd(inv_dy);
            let v_inv_dz = _mm256_set1_pd(inv_dz);

            let mut i = 0i32;
            while i <= box_count - 4 {
                let idx = i as isize;
                let b_min_x = _mm256_loadu_pd(min_x.offset(idx));
                let b_max_x = _mm256_loadu_pd(max_x.offset(idx));
                let t1x = _mm256_mul_pd(_mm256_sub_pd(b_min_x, v_ox), v_inv_dx);
                let t2x = _mm256_mul_pd(_mm256_sub_pd(b_max_x, v_ox), v_inv_dx);
                let min_tx = _mm256_min_pd(t1x, t2x);
                let max_tx = _mm256_max_pd(t1x, t2x);

                let b_min_y = _mm256_loadu_pd(min_y.offset(idx));
                let b_max_y = _mm256_loadu_pd(max_y.offset(idx));
                let t1y = _mm256_mul_pd(_mm256_sub_pd(b_min_y, v_oy), v_inv_dy);
                let t2y = _mm256_mul_pd(_mm256_sub_pd(b_max_y, v_oy), v_inv_dy);
                let min_ty = _mm256_min_pd(t1y, t2y);
                let max_ty = _mm256_max_pd(t1y, t2y);

                let b_min_z = _mm256_loadu_pd(min_z.offset(idx));
                let b_max_z = _mm256_loadu_pd(max_z.offset(idx));
                let t1z = _mm256_mul_pd(_mm256_sub_pd(b_min_z, v_oz), v_inv_dz);
                let t2z = _mm256_mul_pd(_mm256_sub_pd(b_max_z, v_oz), v_inv_dz);
                let min_tz = _mm256_min_pd(t1z, t2z);
                let max_tz = _mm256_max_pd(t1z, t2z);

                let tmin = _mm256_max_pd(_mm256_max_pd(min_tx, min_ty), min_tz);
                let tmax = _mm256_min_pd(_mm256_min_pd(max_tx, max_ty), max_tz);

                let mut tmins = [0.0f64; 4];
                let mut tmaxs = [0.0f64; 4];
                _mm256_storeu_pd(tmins.as_mut_ptr(), tmin);
                _mm256_storeu_pd(tmaxs.as_mut_ptr(), tmax);

                for k in 0..4 {
                    let cur_tmin = tmins[k];
                    let cur_tmax = tmaxs[k];
                    if cur_tmax >= 0.0 && cur_tmin <= cur_tmax && cur_tmin < closest_hit {
                        closest_hit = cur_tmin;
                        best_index = i + k as i32;
                    }
                }

                i += 4;
            }

            while i < box_count {
                let idx = i as isize;
                let t1 = (*min_x.offset(idx) - ray_ox) * inv_dx;
                let t2 = (*max_x.offset(idx) - ray_ox) * inv_dx;
                let t3 = (*min_y.offset(idx) - ray_oy) * inv_dy;
                let t4 = (*max_y.offset(idx) - ray_oy) * inv_dy;
                let t5 = (*min_z.offset(idx) - ray_oz) * inv_dz;
                let t6 = (*max_z.offset(idx) - ray_oz) * inv_dz;

                let tmin = t1.min(t2).max(t3.min(t4)).max(t5.min(t6));
                let tmax = t1.max(t2).min(t3.max(t4)).min(t5.max(t6));

                if tmax >= 0.0 && tmin <= tmax && tmin < closest_hit {
                    closest_hit = tmin;
                    best_index = i;
                }
                i += 1;
            }

            if !out_hit_index.is_null() {
                *out_hit_index = best_index;
            }
            return if best_index != -1 { closest_hit } else { -1.0 };
        }
    }

    for i in 0..box_count {
        let idx = i as isize;
        let t1 = (*min_x.offset(idx) - ray_ox) * inv_dx;
        let t2 = (*max_x.offset(idx) - ray_ox) * inv_dx;
        let t3 = (*min_y.offset(idx) - ray_oy) * inv_dy;
        let t4 = (*max_y.offset(idx) - ray_oy) * inv_dy;
        let t5 = (*min_z.offset(idx) - ray_oz) * inv_dz;
        let t6 = (*max_z.offset(idx) - ray_oz) * inv_dz;

        let tmin = t1.min(t2).max(t3.min(t4)).max(t5.min(t6));
        let tmax = t1.max(t2).min(t3.max(t4)).min(t5.max(t6));

        if tmax >= 0.0 && tmin <= tmax && tmin < closest_hit {
            closest_hit = tmin;
            best_index = i;
        }
    }

    if !out_hit_index.is_null() {
        *out_hit_index = best_index;
    }
    if best_index != -1 { closest_hit } else { -1.0 }
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct NativeVertex {
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub color: u32,
    pub u: f32,
    pub v: f32,
    pub overlay: u32,
    pub light: u32,
    pub normal: u32,
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_transform_quads_simd(
    in_pos: *const f32,
    in_uvs: *const f32,
    matrix4x4: *const f32,
    color: u32,
    overlay: u32,
    light: u32,
    packed_normal: u32,
    quad_count: i32,
    out_vertices: *mut NativeVertex,
) {
    if quad_count <= 0 || in_pos.is_null() || in_uvs.is_null() || matrix4x4.is_null() || out_vertices.is_null() {
        return;
    }

    let m00 = *matrix4x4.add(0);  let m01 = *matrix4x4.add(4);  let m02 = *matrix4x4.add(8);   let m03 = *matrix4x4.add(12);
    let m10 = *matrix4x4.add(1);  let m11 = *matrix4x4.add(5);  let m12 = *matrix4x4.add(9);   let m13 = *matrix4x4.add(13);
    let m20 = *matrix4x4.add(2);  let m21 = *matrix4x4.add(6);  let m22 = *matrix4x4.add(10);  let m23 = *matrix4x4.add(14);

    for q in 0..quad_count as usize {
        let quad_out = out_vertices.add(q * 4);
        for v in 0..4usize {
            let pos_idx = (q * 4 + v) * 3;
            let uv_idx  = (q * 4 + v) * 2;

            let vx = *in_pos.add(pos_idx + 0);
            let vy = *in_pos.add(pos_idx + 1);
            let vz = *in_pos.add(pos_idx + 2);

            let out_v = quad_out.add(v);
            (*out_v).x = m00 * vx + m01 * vy + m02 * vz + m03;
            (*out_v).y = m10 * vx + m11 * vy + m12 * vz + m13;
            (*out_v).z = m20 * vx + m21 * vy + m22 * vz + m23;
            (*out_v).color = color;
            (*out_v).u = *in_uvs.add(uv_idx + 0);
            (*out_v).v = *in_uvs.add(uv_idx + 1);
            (*out_v).overlay = overlay;
            (*out_v).light = light;
            (*out_v).normal = packed_normal;
        }
    }
}

#[no_mangle]
pub extern "C" fn vith_rust_test_distance_cull_single(
    x: f64, y: f64, z: f64,
    cam_x: f64, cam_y: f64, cam_z: f64,
    max_dist_sq: f64,
) -> i32 {
    let dx = x - cam_x;
    let dy = y - cam_y;
    let dz = z - cam_z;
    let dist_sq = dx * dx + dy * dy + dz * dz;
    if dist_sq > max_dist_sq {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "C" fn vith_rust_distance_sq_3d(
    x1: f64, y1: f64, z1: f64,
    x2: f64, y2: f64, z2: f64,
) -> f64 {
    let dx = x1 - x2;
    let dy = y1 - y2;
    let dz = z1 - z2;
    dx * dx + dy * dy + dz * dz
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_extract_frustum_planes_simd(
    view_proj: *const f32,
    out_nx: *mut f32,
    out_ny: *mut f32,
    out_nz: *mut f32,
    out_d: *mut f32,
) {
    if view_proj.is_null() || out_nx.is_null() || out_ny.is_null() || out_nz.is_null() || out_d.is_null() {
        return;
    }

    let m00 = *view_proj.add(0);  let m10 = *view_proj.add(1);  let m20 = *view_proj.add(2);  let m30 = *view_proj.add(3);
    let m01 = *view_proj.add(4);  let m11 = *view_proj.add(5);  let m21 = *view_proj.add(6);  let m31 = *view_proj.add(7);
    let m02 = *view_proj.add(8);  let m12 = *view_proj.add(9);  let m22 = *view_proj.add(10); let m32 = *view_proj.add(11);
    let m03 = *view_proj.add(12); let m13 = *view_proj.add(13); let m23 = *view_proj.add(14); let m33 = *view_proj.add(15);

    let planes = [
        (m30 + m00, m31 + m01, m32 + m02, m33 + m03), // Left
        (m30 - m00, m31 - m01, m32 - m02, m33 - m03), // Right
        (m30 + m10, m31 + m11, m32 + m12, m33 + m13), // Bottom
        (m30 - m10, m31 - m11, m32 - m12, m33 - m13), // Top
        (m30 + m20, m31 + m21, m32 + m22, m33 + m23), // Near
        (m30 - m20, m31 - m21, m32 - m22, m33 - m23), // Far
    ];

    for p in 0..6 {
        let (nx, ny, nz, d) = planes[p];
        let len_sq = nx * nx + ny * ny + nz * nz;
        let inv_len = if len_sq > 0.0f32 { 1.0f32 / len_sq.sqrt() } else { 1.0f32 };

        *out_nx.add(p) = nx * inv_len;
        *out_ny.add(p) = ny * inv_len;
        *out_nz.add(p) = nz * inv_len;
        *out_d.add(p)  = d  * inv_len;
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_batch_frustum_cull_aabbs_simd(
    min_x: *const f64, min_y: *const f64, min_z: *const f64,
    max_x: *const f64, max_y: *const f64, max_z: *const f64,
    frustum_nx: *const f32,
    frustum_ny: *const f32,
    frustum_nz: *const f32,
    frustum_d: *const f32,
    count: i32,
    out_visibility_bits: *mut u64,
) {
    if count <= 0 || out_visibility_bits.is_null() || min_x.is_null() || frustum_nx.is_null() {
        return;
    }

    let num_words = ((count + 63) / 64) as usize;
    for w in 0..num_words {
        *out_visibility_bits.add(w) = 0;
    }

    for i in 0..count {
        let idx = i as isize;
        let cx = ((*min_x.offset(idx) + *max_x.offset(idx)) * 0.5) as f32;
        let cy = ((*min_y.offset(idx) + *max_y.offset(idx)) * 0.5) as f32;
        let cz = ((*min_z.offset(idx) + *max_z.offset(idx)) * 0.5) as f32;
        let ex = ((*max_x.offset(idx) - *min_x.offset(idx)) * 0.5) as f32;
        let ey = ((*max_y.offset(idx) - *min_y.offset(idx)) * 0.5) as f32;
        let ez = ((*max_z.offset(idx) - *min_z.offset(idx)) * 0.5) as f32;

        let mut inside = true;
        for p in 0..6 {
            let pnx = *frustum_nx.add(p);
            let pny = *frustum_ny.add(p);
            let pnz = *frustum_nz.add(p);
            let pd  = *frustum_d.add(p);

            let dist = cx * pnx + cy * pny + cz * pnz + pd;
            let radius = ex * pnx.abs() + ey * pny.abs() + ez * pnz.abs();
            if dist < -radius {
                inside = false;
                break;
            }
        }

        if inside {
            let word_idx = (i / 64) as usize;
            let bit_offset = (i % 64) as usize;
            *out_visibility_bits.add(word_idx) |= 1u64 << bit_offset;
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_test_box_frustum_single(
    min_x: f64, min_y: f64, min_z: f64,
    max_x: f64, max_y: f64, max_z: f64,
    frustum_nx: *const f32,
    frustum_ny: *const f32,
    frustum_nz: *const f32,
    frustum_d: *const f32,
) -> i32 {
    if frustum_nx.is_null() || frustum_ny.is_null() || frustum_nz.is_null() || frustum_d.is_null() {
        return 1;
    }
    let cx = ((min_x + max_x) * 0.5) as f32;
    let cy = ((min_y + max_y) * 0.5) as f32;
    let cz = ((min_z + max_z) * 0.5) as f32;
    let ex = ((max_x - min_x) * 0.5) as f32;
    let ey = ((max_y - min_y) * 0.5) as f32;
    let ez = ((max_z - min_z) * 0.5) as f32;

    for p in 0..6 {
        let pnx = *frustum_nx.add(p);
        let pny = *frustum_ny.add(p);
        let pnz = *frustum_nz.add(p);
        let pd  = *frustum_d.add(p);

        let dist = cx * pnx + cy * pny + cz * pnz + pd;
        let radius = ex * pnx.abs() + ey * pny.abs() + ez * pnz.abs();
        if dist < -radius {
            return 0;
        }
    }
    1
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_particle_batch_tick_simd(
    pos_x: *mut f64, pos_y: *mut f64, pos_z: *mut f64,
    vel_x: *mut f64, vel_y: *mut f64, vel_z: *mut f64,
    gravity: f64,
    drag: f64,
    count: i32,
) {
    if count <= 0 || pos_x.is_null() || vel_x.is_null() {
        return;
    }
    let n = count as usize;
    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            let v_drag = _mm256_set1_pd(drag);
            let v_grav = _mm256_set1_pd(gravity);
            let mut i = 0usize;
            while i + 4 <= n {
                let px = _mm256_loadu_pd(pos_x.add(i));
                let vx = _mm256_loadu_pd(vel_x.add(i));
                _mm256_storeu_pd(pos_x.add(i), _mm256_add_pd(px, vx));
                _mm256_storeu_pd(vel_x.add(i), _mm256_mul_pd(vx, v_drag));

                let py = _mm256_loadu_pd(pos_y.add(i));
                let vy = _mm256_loadu_pd(vel_y.add(i));
                _mm256_storeu_pd(pos_y.add(i), _mm256_add_pd(py, vy));
                let vy_new = _mm256_sub_pd(vy, v_grav);
                _mm256_storeu_pd(vel_y.add(i), _mm256_mul_pd(vy_new, v_drag));

                let pz = _mm256_loadu_pd(pos_z.add(i));
                let vz = _mm256_loadu_pd(vel_z.add(i));
                _mm256_storeu_pd(pos_z.add(i), _mm256_add_pd(pz, vz));
                _mm256_storeu_pd(vel_z.add(i), _mm256_mul_pd(vz, v_drag));

                i += 4;
            }
            while i < n {
                *pos_x.add(i) += *vel_x.add(i);
                *vel_x.add(i) *= drag;
                *pos_y.add(i) += *vel_y.add(i);
                *vel_y.add(i) = (*vel_y.add(i) - gravity) * drag;
                *pos_z.add(i) += *vel_z.add(i);
                *vel_z.add(i) *= drag;
                i += 1;
            }
            return;
        }
    }
    for i in 0..n {
        *pos_x.add(i) += *vel_x.add(i);
        *vel_x.add(i) *= drag;
        *pos_y.add(i) += *vel_y.add(i);
        *vel_y.add(i) = (*vel_y.add(i) - gravity) * drag;
        *pos_z.add(i) += *vel_z.add(i);
        *vel_z.add(i) *= drag;
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_particle_batch_cull_simd(
    pos_x: *const f64, pos_y: *const f64, pos_z: *const f64,
    cam_x: f64, cam_y: f64, cam_z: f64,
    max_dist_sq: f64,
    count: i32,
    out_cull_bits: *mut u64,
) {
    if count <= 0 || out_cull_bits.is_null() || pos_x.is_null() {
        return;
    }

    let num_words = ((count + 63) / 64) as usize;
    for w in 0..num_words {
        *out_cull_bits.add(w) = 0;
    }

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            let v_cam_x = _mm256_set1_pd(cam_x);
            let v_cam_y = _mm256_set1_pd(cam_y);
            let v_cam_z = _mm256_set1_pd(cam_z);
            let v_max_sq = _mm256_set1_pd(max_dist_sq);

            let mut i = 0i32;
            while i <= count - 4 {
                let px = _mm256_loadu_pd(pos_x.offset(i as isize));
                let py = _mm256_loadu_pd(pos_y.offset(i as isize));
                let pz = _mm256_loadu_pd(pos_z.offset(i as isize));

                let dx = _mm256_sub_pd(px, v_cam_x);
                let dy = _mm256_sub_pd(py, v_cam_y);
                let dz = _mm256_sub_pd(pz, v_cam_z);

                let dist_sq = _mm256_fmadd_pd(dx, dx, _mm256_fmadd_pd(dy, dy, _mm256_mul_pd(dz, dz)));
                let cmp = _mm256_cmp_pd(dist_sq, v_max_sq, _CMP_LE_OQ);
                let mask = _mm256_movemask_pd(cmp) as u64;

                let word_idx = (i / 64) as usize;
                let bit_offset = (i % 64) as usize;
                *out_cull_bits.add(word_idx) |= mask << bit_offset;

                i += 4;
            }

            while i < count {
                let idx = i as isize;
                let dx = *pos_x.offset(idx) - cam_x;
                let dy = *pos_y.offset(idx) - cam_y;
                let dz = *pos_z.offset(idx) - cam_z;
                let dist_sq = dx * dx + dy * dy + dz * dz;
                if dist_sq <= max_dist_sq {
                    let word_idx = (i / 64) as usize;
                    let bit_offset = (i % 64) as usize;
                    *out_cull_bits.add(word_idx) |= 1u64 << bit_offset;
                }
                i += 1;
            }
            return;
        }
    }

    for i in 0..count {
        let idx = i as isize;
        let dx = *pos_x.offset(idx) - cam_x;
        let dy = *pos_y.offset(idx) - cam_y;
        let dz = *pos_z.offset(idx) - cam_z;
        let dist_sq = dx * dx + dy * dy + dz * dz;
        if dist_sq <= max_dist_sq {
            let word_idx = (i / 64) as usize;
            let bit_offset = (i % 64) as usize;
            *out_cull_bits.add(word_idx) |= 1u64 << bit_offset;
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_matrix4f_project_point_simd(
    m: *const f32,
    x: f32, y: f32, z: f32,
    vp_x: f32, vp_y: f32, vp_w: f32, vp_h: f32,
    out_win_x: *mut f32, out_win_y: *mut f32, out_win_z: *mut f32,
) -> i32 {
    if m.is_null() || out_win_x.is_null() || out_win_y.is_null() || out_win_z.is_null() {
        return 0;
    }
    let m00 = *m.add(0);  let m10 = *m.add(1);  let m20 = *m.add(2);  let m30 = *m.add(3);
    let m01 = *m.add(4);  let m11 = *m.add(5);  let m21 = *m.add(6);  let m31 = *m.add(7);
    let m02 = *m.add(8);  let m12 = *m.add(9);  let m22 = *m.add(10); let m32 = *m.add(11);
    let m03 = *m.add(12); let m13 = *m.add(13); let m23 = *m.add(14); let m33 = *m.add(15);

    let clip_x = m00 * x + m01 * y + m02 * z + m03;
    let clip_y = m10 * x + m11 * y + m12 * z + m13;
    let clip_z = m20 * x + m21 * y + m22 * z + m23;
    let clip_w = m30 * x + m31 * y + m32 * z + m33;

    if clip_w <= 1e-6f32 {
        return 0;
    }

    let inv_w = 1.0f32 / clip_w;
    let ndc_x = clip_x * inv_w;
    let ndc_y = clip_y * inv_w;
    let ndc_z = clip_z * inv_w;

    *out_win_x = vp_x + (ndc_x + 1.0f32) * 0.5f32 * vp_w;
    *out_win_y = vp_y + (1.0f32 - ndc_y) * 0.5f32 * vp_h;
    *out_win_z = (ndc_z + 1.0f32) * 0.5f32;

    1
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_batch_matrix4f_mul_simd(
    parent_matrix: *const f32,
    local_matrices: *const f32,
    count: i32,
    out_matrices: *mut f32,
) {
    if count <= 0 || parent_matrix.is_null() || local_matrices.is_null() || out_matrices.is_null() {
        return;
    }
    for i in 0..count as usize {
        let loc_ptr = local_matrices.add(i * 16);
        let out_ptr = out_matrices.add(i * 16);
        vith_rust_matrix4f_mul(parent_matrix, loc_ptr, out_ptr);
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_quaternion_slerp_batch_simd(
    q1: *const f32,
    q2: *const f32,
    t: f32,
    count: i32,
    out_q: *mut f32,
) {
    if count <= 0 || q1.is_null() || q2.is_null() || out_q.is_null() {
        return;
    }
    for i in 0..count as usize {
        let base = i * 4;
        let x1 = *q1.add(base);     let y1 = *q1.add(base + 1);
        let z1 = *q1.add(base + 2); let w1 = *q1.add(base + 3);

        let mut x2 = *q2.add(base);     let mut y2 = *q2.add(base + 1);
        let mut z2 = *q2.add(base + 2); let mut w2 = *q2.add(base + 3);

        let mut dot = x1 * x2 + y1 * y2 + z1 * z2 + w1 * w2;
        if dot < 0.0f32 {
            dot = -dot;
            x2 = -x2; y2 = -y2; z2 = -z2; w2 = -w2;
        }

        let scale0;
        let scale1;
        if dot > 0.9995f32 {
            scale0 = 1.0f32 - t;
            scale1 = t;
        } else {
            let theta = dot.clamp(-1.0f32, 1.0f32).acos();
            let sin_theta = theta.sin();
            scale0 = ((1.0f32 - t) * theta).sin() / sin_theta;
            scale1 = (t * theta).sin() / sin_theta;
        }

        let mut rx = scale0 * x1 + scale1 * x2;
        let mut ry = scale0 * y1 + scale1 * y2;
        let mut rz = scale0 * z1 + scale1 * z2;
        let mut rw = scale0 * w1 + scale1 * w2;

        let len_sq = rx * rx + ry * ry + rz * rz + rw * rw;
        if len_sq > 0.0f32 {
            let inv_len = 1.0f32 / len_sq.sqrt();
            rx *= inv_len; ry *= inv_len; rz *= inv_len; rw *= inv_len;
        }

        *out_q.add(base) = rx;
        *out_q.add(base + 1) = ry;
        *out_q.add(base + 2) = rz;
        *out_q.add(base + 3) = rw;
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_fast_inv_sqrt_batch_simd(
    in_values: *const f32,
    count: i32,
    out_values: *mut f32,
) {
    if count <= 0 || in_values.is_null() || out_values.is_null() {
        return;
    }
    let n = count as usize;
    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") {
            let mut i = 0usize;
            while i + 8 <= n {
                let v = _mm256_loadu_ps(in_values.add(i));
                let r = _mm256_rsqrt_ps(v);
                _mm256_storeu_ps(out_values.add(i), r);
                i += 8;
            }
            while i < n {
                let val = *in_values.add(i);
                *out_values.add(i) = 1.0f32 / val.sqrt();
                i += 1;
            }
            return;
        }
    }
    for i in 0..n {
        let val = *in_values.add(i);
        *out_values.add(i) = 1.0f32 / val.sqrt();
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct ChunkVoxelFace {
    pub min_u: u8,
    pub min_v: u8,
    pub max_u: u8,
    pub max_v: u8,
    pub slice_coord: u8,
    pub direction: u8,
    pub block_id: u16,
    pub ao: [f32; 4],
    pub light: [u32; 4],
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_build_chunk_section_mesh(
    padded_opacity: *const u8,
    padded_light: *const u8,
    block_states: *const u16,
    max_faces: i32,
    out_faces: *mut ChunkVoxelFace,
) -> i32 {
    if padded_opacity.is_null() || out_faces.is_null() || max_faces <= 0 {
        return 0;
    }

    let get_padded_idx = |x: usize, y: usize, z: usize| -> usize {
        y * 324 + z * 18 + x
    };

    let mut face_count = 0i32;

    let dir_offsets: [(isize, isize, isize); 6] = [
        (0, -1, 0),
        (0, 1, 0),
        (0, 0, -1),
        (0, 0, 1),
        (-1, 0, 0),
        (1, 0, 0),
    ];

    let ao_table = [1.0f32, 0.80f32, 0.60f32, 0.40f32];

    for y in 1..=16usize {
        for z in 1..=16usize {
            for x in 1..=16usize {
                if face_count >= max_faces {
                    return face_count;
                }

                let cur_idx = get_padded_idx(x, y, z);
                let cur_opacity = *padded_opacity.add(cur_idx);
                if cur_opacity == 0 {
                    continue;
                }

                let b_state = if !block_states.is_null() {
                    let unpadded_idx = (y - 1) * 256 + (z - 1) * 16 + (x - 1);
                    *block_states.add(unpadded_idx)
                } else {
                    1u16
                };

                for d in 0..6usize {
                    if face_count >= max_faces {
                        return face_count;
                    }
                    let (dx, dy, dz) = dir_offsets[d];
                    let nx = (x as isize + dx) as usize;
                    let ny = (y as isize + dy) as usize;
                    let nz = (z as isize + dz) as usize;

                    let neighbor_op = *padded_opacity.add(get_padded_idx(nx, ny, nz));
                    if neighbor_op == 0 {
                        let out_ptr = out_faces.add(face_count as usize);
                        (*out_ptr).min_u = (x - 1) as u8;
                        (*out_ptr).min_v = (z - 1) as u8;
                        (*out_ptr).max_u = x as u8;
                        (*out_ptr).max_v = z as u8;
                        (*out_ptr).slice_coord = (y - 1) as u8;
                        (*out_ptr).direction = d as u8;
                        (*out_ptr).block_id = b_state;

                        for c in 0..4 {
                            (*out_ptr).ao[c] = ao_table[0];
                            if !padded_light.is_null() {
                                let lit = *padded_light.add(get_padded_idx(nx, ny, nz)) as u32;
                                let block_lit = lit & 0x0F;
                                let sky_lit = (lit >> 4) & 0x0F;
                                (*out_ptr).light[c] = (sky_lit << 20) | (block_lit << 4);
                            } else {
                                (*out_ptr).light[c] = 0x00F000F0;
                            }
                        }

                        face_count += 1;
                    }
                }
            }
        }
    }

    face_count
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_mesh_section_faces_simd(
    block_opacity: *const u8,
    out_face_bits: *mut u64,
    out_face_counts: *mut i32,
) {
    if block_opacity.is_null() || out_face_bits.is_null() || out_face_counts.is_null() {
        return;
    }

    for d in 0..6 {
        *out_face_counts.add(d) = 0;
        for w in 0..64 {
            *out_face_bits.add(d * 64 + w) = 0;
        }
    }

    for y in 0..16 {
        for z in 0..16 {
            let base_idx = (y * 256) + (z * 16);
            for x in 0..16 {
                let idx = base_idx + x;
                let current = *block_opacity.add(idx);
                if current == 0 {
                    continue;
                }

                let word_idx = idx / 64;
                let bit = 1u64 << (idx % 64);

                if y == 0 || *block_opacity.add(idx - 256) == 0 {
                    *out_face_bits.add(0 * 64 + word_idx) |= bit;
                    *out_face_counts.add(0) += 1;
                }

                if y == 15 || *block_opacity.add(idx + 256) == 0 {
                    *out_face_bits.add(1 * 64 + word_idx) |= bit;
                    *out_face_counts.add(1) += 1;
                }

                if z == 0 || *block_opacity.add(idx - 16) == 0 {
                    *out_face_bits.add(2 * 64 + word_idx) |= bit;
                    *out_face_counts.add(2) += 1;
                }

                if z == 15 || *block_opacity.add(idx + 16) == 0 {
                    *out_face_bits.add(3 * 64 + word_idx) |= bit;
                    *out_face_counts.add(3) += 1;
                }

                if x == 0 || *block_opacity.add(idx - 1) == 0 {
                    *out_face_bits.add(4 * 64 + word_idx) |= bit;
                    *out_face_counts.add(4) += 1;
                }

                if x == 15 || *block_opacity.add(idx + 1) == 0 {
                    *out_face_bits.add(5 * 64 + word_idx) |= bit;
                    *out_face_counts.add(5) += 1;
                }
            }
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_batch_aabb_overlap_simd(
    q_minx: f64, q_miny: f64, q_minz: f64,
    q_maxx: f64, q_maxy: f64, q_maxz: f64,
    min_x: *const f64,
    min_y: *const f64,
    min_z: *const f64,
    max_x: *const f64,
    max_y: *const f64,
    max_z: *const f64,
    count: i32,
    out_overlap_bits: *mut u64,
) {
    if count <= 0 || out_overlap_bits.is_null() {
        return;
    }

    let num_words = ((count + 63) / 64) as usize;
    for w in 0..num_words {
        *out_overlap_bits.add(w) = 0;
    }

    for i in 0..count {
        let idx = i as isize;
        let t_minx = *min_x.offset(idx);
        let t_miny = *min_y.offset(idx);
        let t_minz = *min_z.offset(idx);
        let t_maxx = *max_x.offset(idx);
        let t_maxy = *max_y.offset(idx);
        let t_maxz = *max_z.offset(idx);

        if q_minx < t_maxx && q_maxx > t_minx
            && q_miny < t_maxy && q_maxy > t_miny
            && q_minz < t_maxz && q_maxz > t_minz
        {
            let word_idx = (i / 64) as usize;
            let bit_offset = (i % 64) as usize;
            *out_overlap_bits.add(word_idx) |= 1u64 << bit_offset;
        }
    }
}

#[repr(C)]
pub struct MergedQuad {
    pub min_u: u8,
    pub min_v: u8,
    pub max_u: u8,
    pub max_v: u8,
    pub slice_coord: u8,
    pub direction: u8,
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_greedy_merge_slice(
    slice_bits: *const u16,
    slice_coord: u8,
    direction: u8,
    max_quads: i32,
    out_quads: *mut MergedQuad,
) -> i32 {
    if slice_bits.is_null() || out_quads.is_null() || max_quads <= 0 {
        return 0;
    }

    let mut grid = [0u16; 16];
    for v in 0..16 {
        grid[v] = *slice_bits.add(v);
    }

    let mut quad_count = 0i32;

    for v in 0..16 {
        while grid[v] != 0 && quad_count < max_quads {
            let u = grid[v].trailing_zeros() as usize;
            if u >= 16 {
                break;
            }

            let mut width = 1usize;
            while (u + width) < 16 && ((grid[v] >> (u + width)) & 1) == 1 {
                width += 1;
            }

            let span_mask = (((1u32 << width) - 1) as u16) << u;
            let mut height = 1usize;
            while (v + height) < 16 && (grid[v + height] & span_mask) == span_mask {
                height += 1;
            }

            for h in 0..height {
                grid[v + h] &= !span_mask;
            }

            let quad_ptr = out_quads.offset(quad_count as isize);
            (*quad_ptr).min_u = u as u8;
            (*quad_ptr).min_v = v as u8;
            (*quad_ptr).max_u = (u + width) as u8;
            (*quad_ptr).max_v = (v + height) as u8;
            (*quad_ptr).slice_coord = slice_coord;
            (*quad_ptr).direction = direction;

            quad_count += 1;
        }
    }

    quad_count
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_sort_translucent_depth(
    centers_x: *const f32,
    centers_y: *const f32,
    centers_z: *const f32,
    cam_x: f32,
    cam_y: f32,
    cam_z: f32,
    count: i32,
    out_sorted_indices: *mut i32,
) {
    if count <= 0 || out_sorted_indices.is_null() {
        return;
    }

    let n = count as usize;
    let mut dist_index_pairs = Vec::with_capacity(n);

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            let v_cam_x = _mm256_set1_ps(cam_x);
            let v_cam_y = _mm256_set1_ps(cam_y);
            let v_cam_z = _mm256_set1_ps(cam_z);

            let mut i = 0usize;
            while i + 8 <= n {
                let px = _mm256_loadu_ps(centers_x.add(i));
                let py = _mm256_loadu_ps(centers_y.add(i));
                let pz = _mm256_loadu_ps(centers_z.add(i));

                let dx = _mm256_sub_ps(px, v_cam_x);
                let dy = _mm256_sub_ps(py, v_cam_y);
                let dz = _mm256_sub_ps(pz, v_cam_z);

                let d2 = _mm256_fmadd_ps(dx, dx, _mm256_fmadd_ps(dy, dy, _mm256_mul_ps(dz, dz)));

                let mut dists = [0.0f32; 8];
                _mm256_storeu_ps(dists.as_mut_ptr(), d2);

                for k in 0..8 {
                    dist_index_pairs.push((dists[k], (i + k) as i32));
                }
                i += 8;
            }

            while i < n {
                let dx = *centers_x.add(i) - cam_x;
                let dy = *centers_y.add(i) - cam_y;
                let dz = *centers_z.add(i) - cam_z;
                let d2 = dx * dx + dy * dy + dz * dz;
                dist_index_pairs.push((d2, i as i32));
                i += 1;
            }

            dist_index_pairs.sort_unstable_by(|a, b| b.0.partial_cmp(&a.0).unwrap_or(std::cmp::Ordering::Equal));

            for j in 0..n {
                *out_sorted_indices.add(j) = dist_index_pairs[j].1;
            }
            return;
        }
    }

    for i in 0..n {
        let dx = *centers_x.add(i) - cam_x;
        let dy = *centers_y.add(i) - cam_y;
        let dz = *centers_z.add(i) - cam_z;
        let d2 = dx * dx + dy * dy + dz * dz;
        dist_index_pairs.push((d2, i as i32));
    }

    dist_index_pairs.sort_unstable_by(|a, b| b.0.partial_cmp(&a.0).unwrap_or(std::cmp::Ordering::Equal));
    for j in 0..n {
        *out_sorted_indices.add(j) = dist_index_pairs[j].1;
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_batch_lerp_vectors(
    prev_x: *const f32, cur_x: *const f32, out_x: *mut f32,
    prev_y: *const f32, cur_y: *const f32, out_y: *mut f32,
    prev_z: *const f32, cur_z: *const f32, out_z: *mut f32,
    partial_tick: f32,
    count: i32,
) {
    if count <= 0 || prev_x.is_null() || cur_x.is_null() || out_x.is_null() {
        return;
    }

    let n = count as usize;

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            let v_t = _mm256_set1_ps(partial_tick);
            let mut i = 0usize;

            while i + 8 <= n {
                let px = _mm256_loadu_ps(prev_x.add(i));
                let cx = _mm256_loadu_ps(cur_x.add(i));
                let rx = _mm256_fmadd_ps(_mm256_sub_ps(cx, px), v_t, px);
                _mm256_storeu_ps(out_x.add(i), rx);

                let py = _mm256_loadu_ps(prev_y.add(i));
                let cy = _mm256_loadu_ps(cur_y.add(i));
                let ry = _mm256_fmadd_ps(_mm256_sub_ps(cy, py), v_t, py);
                _mm256_storeu_ps(out_y.add(i), ry);

                let pz = _mm256_loadu_ps(prev_z.add(i));
                let cz = _mm256_loadu_ps(cur_z.add(i));
                let rz = _mm256_fmadd_ps(_mm256_sub_ps(cz, pz), v_t, pz);
                _mm256_storeu_ps(out_z.add(i), rz);

                i += 8;
            }

            while i < n {
                *out_x.add(i) = *prev_x.add(i) + (*cur_x.add(i) - *prev_x.add(i)) * partial_tick;
                *out_y.add(i) = *prev_y.add(i) + (*cur_y.add(i) - *prev_y.add(i)) * partial_tick;
                *out_z.add(i) = *prev_z.add(i) + (*cur_z.add(i) - *prev_z.add(i)) * partial_tick;
                i += 1;
            }
            return;
        }
    }

    for i in 0..n {
        *out_x.add(i) = *prev_x.add(i) + (*cur_x.add(i) - *prev_x.add(i)) * partial_tick;
        *out_y.add(i) = *prev_y.add(i) + (*cur_y.add(i) - *prev_y.add(i)) * partial_tick;
        *out_z.add(i) = *prev_z.add(i) + (*cur_z.add(i) - *prev_z.add(i)) * partial_tick;
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_solve_occlusion_graph(
    section_coords_x: *const i32,
    section_coords_y: *const i32,
    section_coords_z: *const i32,
    section_passage_masks: *const u64,
    section_count: i32,
    cam_section_x: i32,
    cam_section_y: i32,
    cam_section_z: i32,
    frustum_nx: *const f32,
    frustum_ny: *const f32,
    frustum_nz: *const f32,
    frustum_d: *const f32,
    out_visible_indices: *mut i32,
) -> i32 {
    if section_count <= 0 || section_coords_x.is_null() || out_visible_indices.is_null() {
        return 0;
    }

    let n = section_count as usize;
    let mut visited = vec![false; n];
    let mut visible_count = 0i32;
    let mut queue = Vec::with_capacity(1024);

    let mut start_idx = -1i32;
    let mut min_dist_sq = i64::MAX;

    for i in 0..n {
        let sx = *section_coords_x.add(i);
        let sy = *section_coords_y.add(i);
        let sz = *section_coords_z.add(i);

        let dx = (sx - cam_section_x) as i64;
        let dy = (sy - cam_section_y) as i64;
        let dz = (sz - cam_section_z) as i64;
        let d2 = dx * dx + dy * dy + dz * dz;

        if d2 < min_dist_sq {
            min_dist_sq = d2;
            start_idx = i as i32;
        }
    }

    if start_idx >= 0 {
        let s = start_idx as usize;
        visited[s] = true;
        *out_visible_indices.add(visible_count as usize) = start_idx;
        visible_count += 1;
        queue.push((start_idx, 6u8));
    }

    let dir_dx = [0, 0, 0, 0, -1, 1];
    let dir_dy = [-1, 1, 0, 0, 0, 0];
    let dir_dz = [0, 0, -1, 1, 0, 0];

    let mut head = 0usize;
    while head < queue.len() {
        let (curr_idx, from_face) = queue[head];
        head += 1;

        let curr_u = curr_idx as usize;
        let cx = *section_coords_x.add(curr_u);
        let cy = *section_coords_y.add(curr_u);
        let cz = *section_coords_z.add(curr_u);
        let passage_mask = *section_passage_masks.add(curr_u);

        for to_face in 0..6u8 {
            if from_face < 6 {
                let opposite = from_face ^ 1;
                if to_face == opposite {
                    continue;
                }
                let bit_pos = (from_face * 6 + to_face) as usize;
                if ((passage_mask >> bit_pos) & 1) == 0 {
                    continue;
                }
            }

            let nx = cx + dir_dx[to_face as usize];
            let ny = cy + dir_dy[to_face as usize];
            let nz = cz + dir_dz[to_face as usize];

            for cand in 0..n {
                if visited[cand] {
                    continue;
                }

                if *section_coords_x.add(cand) == nx
                    && *section_coords_y.add(cand) == ny
                    && *section_coords_z.add(cand) == nz
                {
                    let center_x = (nx * 16 + 8) as f32;
                    let center_y = (ny * 16 + 8) as f32;
                    let center_z = (nz * 16 + 8) as f32;
                    let extent = 8.0f32;

                    let mut inside = true;
                    for p in 0..6 {
                        let pnx = *frustum_nx.add(p);
                        let pny = *frustum_ny.add(p);
                        let pnz = *frustum_nz.add(p);
                        let pd  = *frustum_d.add(p);

                        let dist = center_x * pnx + center_y * pny + center_z * pnz + pd;
                        let radius = extent * (pnx.abs() + pny.abs() + pnz.abs());
                        if dist < -radius {
                            inside = false;
                            break;
                        }
                    }

                    if inside {
                        visited[cand] = true;
                        *out_visible_indices.add(visible_count as usize) = cand as i32;
                        visible_count += 1;
                        queue.push((cand as i32, to_face ^ 1));
                    }
                    break;
                }
            }
        }
    }

    visible_count
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_compute_ambient_occlusion_3d(
    padded_opacity: *const u8,
    padded_light: *const u8,
    quad_x: *const u8,
    quad_y: *const u8,
    quad_z: *const u8,
    quad_direction: *const u8,
    quad_count: i32,
    out_corner_ao: *mut f32,
    out_corner_light: *mut u32,
) -> i32 {
    if quad_count <= 0 || padded_opacity.is_null() || out_corner_ao.is_null() {
        return 0;
    }

    let stride_y = 18 * 18;
    let stride_z = 18;
    let stride_x = 1;

    let get_index = |x: i32, y: i32, z: i32| -> usize {
        (y * stride_y + z * stride_z + x * stride_x) as usize
    };

    let is_opaque = |x: i32, y: i32, z: i32| -> u8 {
        *padded_opacity.add(get_index(x, y, z))
    };

    let get_light = |x: i32, y: i32, z: i32| -> u8 {
        if padded_light.is_null() {
            0
        } else {
            *padded_light.add(get_index(x, y, z))
        }
    };

    let ao_table = [1.0f32, 0.80f32, 0.60f32, 0.40f32];

    for q in 0..quad_count as usize {
        let x = *quad_x.add(q) as i32;
        let y = *quad_y.add(q) as i32;
        let z = *quad_z.add(q) as i32;
        let dir = *quad_direction.add(q);

        let (nx, ny, nz) = match dir {
            0 => (x, y - 1, z),
            1 => (x, y + 1, z),
            2 => (x, y, z - 1),
            3 => (x, y, z + 1),
            4 => (x - 1, y, z),
            _ => (x + 1, y, z),
        };

        let (u_dx, u_dy, u_dz, v_dx, v_dy, v_dz) = match dir {
            0 | 1 => (1, 0, 0, 0, 0, 1),
            2 | 3 => (1, 0, 0, 0, 1, 0),
            _     => (0, 0, 1, 0, 1, 0),
        };

        let corner_offsets = [
            (-1, -1),
            ( 1, -1),
            ( 1,  1),
            (-1,  1),
        ];

        for c in 0..4 {
            let (cu, cv) = corner_offsets[c];

            let s1_x = nx + cu * u_dx;
            let s1_y = ny + cu * u_dy;
            let s1_z = nz + cu * u_dz;

            let s2_x = nx + cv * v_dx;
            let s2_y = ny + cv * v_dy;
            let s2_z = nz + cv * v_dz;

            let cor_x = nx + cu * u_dx + cv * v_dx;
            let cor_y = ny + cu * u_dy + cv * v_dy;
            let cor_z = nz + cu * u_dz + cv * v_dz;

            let s1 = is_opaque(s1_x, s1_y, s1_z);
            let s2 = is_opaque(s2_x, s2_y, s2_z);
            let cor = is_opaque(cor_x, cor_y, cor_z);

            let factor = if s1 == 1 && s2 == 1 {
                3usize
            } else {
                ((s1 + s2 + cor) as usize).min(3)
            };

            let out_idx = q * 4 + c;
            *out_corner_ao.add(out_idx) = ao_table[factor];

            if !out_corner_light.is_null() {
                let l0 = get_light(nx, ny, nz) as u32;
                let l1 = get_light(s1_x, s1_y, s1_z) as u32;
                let l2 = get_light(s2_x, s2_y, s2_z) as u32;
                let l3 = get_light(cor_x, cor_y, cor_z) as u32;

                let block_sum = (l0 & 0x0F) + (l1 & 0x0F) + (l2 & 0x0F) + (l3 & 0x0F);
                let sky_sum   = ((l0 >> 4) & 0x0F) + ((l1 >> 4) & 0x0F) + ((l2 >> 4) & 0x0F) + ((l3 >> 4) & 0x0F);

                let avg_block = (block_sum >> 2).min(15);
                let avg_sky   = (sky_sum >> 2).min(15);

                *out_corner_light.add(out_idx) = (avg_sky << 20) | (avg_block << 4);
            }
        }
    }

    quad_count
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_batch_transform_model_parts(
    matrices: *const f32,
    cube_min_x: *const f32, cube_min_y: *const f32, cube_min_z: *const f32,
    cube_max_x: *const f32, cube_max_y: *const f32, cube_max_z: *const f32,
    part_count: i32,
    out_vertices_x: *mut f32,
    out_vertices_y: *mut f32,
    out_vertices_z: *mut f32,
) -> i32 {
    if part_count <= 0 || matrices.is_null() || out_vertices_x.is_null() {
        return 0;
    }

    for p in 0..part_count as usize {
        let m_ptr = matrices.add(p * 16);
        let m00 = *m_ptr.add(0);  let m01 = *m_ptr.add(4);  let m02 = *m_ptr.add(8);   let m03 = *m_ptr.add(12);
        let m10 = *m_ptr.add(1);  let m11 = *m_ptr.add(5);  let m12 = *m_ptr.add(9);   let m13 = *m_ptr.add(13);
        let m20 = *m_ptr.add(2);  let m21 = *m_ptr.add(6);  let m22 = *m_ptr.add(10);  let m23 = *m_ptr.add(14);

        let min_x = *cube_min_x.add(p);
        let min_y = *cube_min_y.add(p);
        let min_z = *cube_min_z.add(p);
        let max_x = *cube_max_x.add(p);
        let max_y = *cube_max_y.add(p);
        let max_z = *cube_max_z.add(p);

        let corners_x = [min_x, max_x, max_x, min_x, min_x, max_x, max_x, min_x];
        let corners_y = [min_y, min_y, max_y, max_y, min_y, min_y, max_y, max_y];
        let corners_z = [min_z, min_z, min_z, min_z, max_z, max_z, max_z, max_z];

        let face_indices: [usize; 24] = [
            0, 1, 5, 4,
            2, 3, 7, 6,
            0, 3, 2, 1,
            5, 6, 7, 4,
            4, 7, 3, 0,
            1, 2, 6, 5
        ];

        let base_out = p * 24;
        for v in 0..24 {
            let c = face_indices[v];
            let lx = corners_x[c];
            let ly = corners_y[c];
            let lz = corners_z[c];

            let wx = m00 * lx + m01 * ly + m02 * lz + m03;
            let wy = m10 * lx + m11 * ly + m12 * lz + m13;
            let wz = m20 * lx + m21 * ly + m22 * lz + m23;

            *out_vertices_x.add(base_out + v) = wx;
            *out_vertices_y.add(base_out + v) = wy;
            *out_vertices_z.add(base_out + v) = wz;
        }
    }

    part_count * 24
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_matrix4f_mul(
    m_a: *const f32,
    m_b: *const f32,
    out_m: *mut f32,
) {
    if m_a.is_null() || m_b.is_null() || out_m.is_null() {
        return;
    }

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("sse") {
            let a_col0 = _mm_loadu_ps(m_a.add(0));
            let a_col1 = _mm_loadu_ps(m_a.add(4));
            let a_col2 = _mm_loadu_ps(m_a.add(8));
            let a_col3 = _mm_loadu_ps(m_a.add(12));

            for c in 0..4 {
                let b_ptr = m_b.add(c * 4);
                let b0 = _mm_set1_ps(*b_ptr.add(0));
                let b1 = _mm_set1_ps(*b_ptr.add(1));
                let b2 = _mm_set1_ps(*b_ptr.add(2));
                let b3 = _mm_set1_ps(*b_ptr.add(3));

                let sum0 = _mm_add_ps(_mm_mul_ps(a_col0, b0), _mm_mul_ps(a_col1, b1));
                let sum1 = _mm_add_ps(_mm_mul_ps(a_col2, b2), _mm_mul_ps(a_col3, b3));
                let res = _mm_add_ps(sum0, sum1);

                _mm_storeu_ps(out_m.add(c * 4), res);
            }
            return;
        }
    }

    for col in 0..4 {
        for row in 0..4 {
            let mut sum = 0.0f32;
            for k in 0..4 {
                sum += *m_a.add(k * 4 + row) * *m_b.add(col * 4 + k);
            }
            *out_m.add(col * 4 + row) = sum;
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_matrix4f_invert(
    m: *const f32,
    out_inv: *mut f32,
) -> i32 {
    if m.is_null() || out_inv.is_null() {
        return 0;
    }

    let a00 = *m.add(0);  let a10 = *m.add(1);  let a20 = *m.add(2);  let a30 = *m.add(3);
    let a01 = *m.add(4);  let a11 = *m.add(5);  let a21 = *m.add(6);  let a31 = *m.add(7);
    let a02 = *m.add(8);  let a12 = *m.add(9);  let a22 = *m.add(10); let a32 = *m.add(11);
    let a03 = *m.add(12); let a13 = *m.add(13); let a23 = *m.add(14); let a33 = *m.add(15);

    let b00 = a00 * a11 - a01 * a10;
    let b01 = a00 * a12 - a02 * a10;
    let b02 = a00 * a13 - a03 * a10;
    let b03 = a01 * a12 - a02 * a11;
    let b04 = a01 * a13 - a03 * a11;
    let b05 = a02 * a13 - a03 * a12;
    let b06 = a20 * a31 - a21 * a30;
    let b07 = a20 * a32 - a22 * a30;
    let b08 = a20 * a33 - a23 * a30;
    let b09 = a21 * a32 - a22 * a31;
    let b10 = a21 * a33 - a23 * a31;
    let b11 = a22 * a33 - a23 * a32;

    let det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
    if det.abs() <= 1e-15 {
        return 0;
    }

    let inv_det = 1.0f32 / det;

    *out_inv.add(0)  = (a11 * b11 - a12 * b10 + a13 * b09) * inv_det;
    *out_inv.add(1)  = (-a10 * b11 + a12 * b08 - a13 * b07) * inv_det;
    *out_inv.add(2)  = (a10 * b10 - a11 * b08 + a13 * b06) * inv_det;
    *out_inv.add(3)  = (-a10 * b09 + a11 * b07 - a12 * b06) * inv_det;

    *out_inv.add(4)  = (-a01 * b11 + a02 * b10 - a03 * b09) * inv_det;
    *out_inv.add(5)  = (a00 * b11 - a02 * b08 + a03 * b07) * inv_det;
    *out_inv.add(6)  = (-a00 * b10 + a01 * b08 - a03 * b06) * inv_det;
    *out_inv.add(7)  = (a00 * b09 - a01 * b07 + a02 * b06) * inv_det;

    *out_inv.add(8)  = (a31 * b05 - a32 * b04 + a33 * b03) * inv_det;
    *out_inv.add(9)  = (-a30 * b05 + a32 * b02 - a33 * b01) * inv_det;
    *out_inv.add(10) = (a30 * b04 - a31 * b02 + a33 * b00) * inv_det;
    *out_inv.add(11) = (-a30 * b03 + a31 * b01 - a32 * b00) * inv_det;

    *out_inv.add(12) = (-a21 * b05 + a22 * b04 - a23 * b03) * inv_det;
    *out_inv.add(13) = (a20 * b05 - a22 * b02 + a23 * b01) * inv_det;
    *out_inv.add(14) = (-a20 * b04 + a21 * b02 - a23 * b00) * inv_det;
    *out_inv.add(15) = (a20 * b03 - a21 * b01 + a22 * b00) * inv_det;

    1
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_frustum_test_box_simd(
    min_x: f32, min_y: f32, min_z: f32,
    max_x: f32, max_y: f32, max_z: f32,
    frustum_nx: *const f32,
    frustum_ny: *const f32,
    frustum_nz: *const f32,
    frustum_d: *const f32,
) -> i32 {
    if frustum_nx.is_null() || frustum_ny.is_null() || frustum_nz.is_null() || frustum_d.is_null() {
        return 1;
    }

    let cx = (min_x + max_x) * 0.5f32;
    let cy = (min_y + max_y) * 0.5f32;
    let cz = (min_z + max_z) * 0.5f32;

    let ex = (max_x - min_x) * 0.5f32;
    let ey = (max_y - min_y) * 0.5f32;
    let ez = (max_z - min_z) * 0.5f32;

    for p in 0..6 {
        let pnx = *frustum_nx.add(p);
        let pny = *frustum_ny.add(p);
        let pnz = *frustum_nz.add(p);
        let pd  = *frustum_d.add(p);

        let dist = cx * pnx + cy * pny + cz * pnz + pd;
        let radius = ex * pnx.abs() + ey * pny.abs() + ez * pnz.abs();

        if dist < -radius {
            return 0;
        }
    }

    1
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_batch_distance_cull(
    centers_x: *const f64,
    centers_y: *const f64,
    centers_z: *const f64,
    cam_x: f64,
    cam_y: f64,
    cam_z: f64,
    max_dist_sq: f64,
    count: i32,
    out_bits: *mut u64,
) {
    if count <= 0 || out_bits.is_null() || centers_x.is_null() {
        return;
    }

    let num_words = ((count + 63) / 64) as usize;
    for w in 0..num_words {
        *out_bits.add(w) = 0;
    }

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            let v_cam_x = _mm256_set1_pd(cam_x);
            let v_cam_y = _mm256_set1_pd(cam_y);
            let v_cam_z = _mm256_set1_pd(cam_z);
            let v_max_sq = _mm256_set1_pd(max_dist_sq);

            let mut i = 0i32;
            while i <= count - 4 {
                let px = _mm256_loadu_pd(centers_x.offset(i as isize));
                let py = _mm256_loadu_pd(centers_y.offset(i as isize));
                let pz = _mm256_loadu_pd(centers_z.offset(i as isize));

                let dx = _mm256_sub_pd(px, v_cam_x);
                let dy = _mm256_sub_pd(py, v_cam_y);
                let dz = _mm256_sub_pd(pz, v_cam_z);

                let dist_sq = _mm256_fmadd_pd(dx, dx, _mm256_fmadd_pd(dy, dy, _mm256_mul_pd(dz, dz)));
                let cmp = _mm256_cmp_pd(dist_sq, v_max_sq, _CMP_LE_OQ);
                let mask = _mm256_movemask_pd(cmp) as u64;

                let word_idx = (i / 64) as usize;
                let bit_offset = (i % 64) as usize;
                *out_bits.add(word_idx) |= mask << bit_offset;

                i += 4;
            }

            while i < count {
                let idx = i as isize;
                let dx = *centers_x.offset(idx) - cam_x;
                let dy = *centers_y.offset(idx) - cam_y;
                let dz = *centers_z.offset(idx) - cam_z;
                let dist_sq = dx * dx + dy * dy + dz * dz;
                if dist_sq <= max_dist_sq {
                    let word_idx = (i / 64) as usize;
                    let bit_offset = (i % 64) as usize;
                    *out_bits.add(word_idx) |= 1u64 << bit_offset;
                }
                i += 1;
            }
            return;
        }
    }

    for i in 0..count {
        let idx = i as isize;
        let dx = *centers_x.offset(idx) - cam_x;
        let dy = *centers_y.offset(idx) - cam_y;
        let dz = *centers_z.offset(idx) - cam_z;
        let dist_sq = dx * dx + dy * dy + dz * dz;
        if dist_sq <= max_dist_sq {
            let word_idx = (i / 64) as usize;
            let bit_offset = (i % 64) as usize;
            *out_bits.add(word_idx) |= 1u64 << bit_offset;
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_fast_lightmap_simd(
    ambient: f32,
    sky_factor: f32,
    block_factor: f32,
    gamma: f32,
    is_night_vision: i32,
    out_rgba: *mut u32,
) {
    if out_rgba.is_null() {
        return;
    }

    let gamma_curve = |val: f32| -> f32 {
        let inv = 1.0f32 - val;
        let inv4 = inv * inv * inv * inv;
        let factor = 1.0f32 - inv4;
        factor * gamma + val * (1.0f32 - gamma)
    };

    for sky in 0..16 {
        let s_val = (sky as f32) / 15.0f32 * sky_factor;
        for block in 0..16 {
            let b_val = (block as f32) / 15.0f32 * block_factor;

            let mut r = b_val * 1.2f32 + s_val * 0.9f32 + ambient;
            let mut g = b_val * 1.0f32 + s_val * 0.95f32 + ambient;
            let mut b = b_val * 0.8f32 + s_val * 1.1f32 + ambient;

            if is_night_vision != 0 {
                let max_c = r.max(g.max(b));
                if max_c > 0.0f32 {
                    let boost = 1.0f32 / max_c;
                    r = (r * boost).min(1.0f32);
                    g = (g * boost).min(1.0f32);
                    b = (b * boost).min(1.0f32);
                }
            }

            r = gamma_curve(r.clamp(0.0f32, 1.0f32));
            g = gamma_curve(g.clamp(0.0f32, 1.0f32));
            b = gamma_curve(b.clamp(0.0f32, 1.0f32));

            let ir = (r * 255.0f32) as u32;
            let ig = (g * 255.0f32) as u32;
            let ib = (b * 255.0f32) as u32;

            let packed = 0xFF000000u32 | (ib << 16) | (ig << 8) | ir;
            let idx = sky * 16 + block;
            *out_rgba.add(idx) = packed;
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn vith_rust_batch_sin_cos_simd(
    in_angles: *const f32,
    count: i32,
    out_sin: *mut f32,
    out_cos: *mut f32,
) {
    if count <= 0 || in_angles.is_null() || out_sin.is_null() || out_cos.is_null() {
        return;
    }

    let n = count as usize;
    for i in 0..n {
        let a = *in_angles.add(i);
        *out_sin.add(i) = a.sin();
        *out_cos.add(i) = a.cos();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version() {
        assert_eq!(vith_rust_get_version(), 400);
    }

    #[test]
    fn test_distance_cull() {
        assert_eq!(vith_rust_test_distance_cull_single(0.0, 0.0, 0.0, 0.0, 10.0, 0.0, 64.0), 1);
        assert_eq!(vith_rust_test_distance_cull_single(0.0, 0.0, 0.0, 0.0, 5.0, 0.0, 64.0), 0);
    }

    #[test]
    fn test_raycast() {
        let min_x = [10.0f64];
        let min_y = [-1.0f64];
        let min_z = [-1.0f64];
        let max_x = [12.0f64];
        let max_y = [1.0f64];
        let max_z = [1.0f64];
        let mut hit_idx = -1i32;

        unsafe {
            let hit = vith_rust_raycast_aabb_batch_simd(
                0.0, 0.0, 0.0,
                1.0, 0.0, 0.0,
                min_x.as_ptr(), min_y.as_ptr(), min_z.as_ptr(),
                max_x.as_ptr(), max_y.as_ptr(), max_z.as_ptr(),
                1, 100.0,
                &mut hit_idx as *mut i32,
            );
            assert_eq!(hit_idx, 0);
            assert!((hit - 10.0).abs() < 1e-5);
        }
    }

    #[test]
    fn test_matrix_invert() {
        let identity = [
            1.0f32, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        ];
        let mut out = [0.0f32; 16];
        unsafe {
            let res = vith_rust_matrix4f_invert(identity.as_ptr(), out.as_mut_ptr());
            assert_eq!(res, 1);
            assert_eq!(out, identity);
        }
    }

    #[test]
    fn test_frustum_planes() {
        let identity = [
            1.0f32, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        ];
        let mut nx = [0.0f32; 6];
        let mut ny = [0.0f32; 6];
        let mut nz = [0.0f32; 6];
        let mut d  = [0.0f32; 6];
        unsafe {
            vith_rust_extract_frustum_planes_simd(identity.as_ptr(), nx.as_mut_ptr(), ny.as_mut_ptr(), nz.as_mut_ptr(), d.as_mut_ptr());
            let inside = vith_rust_test_box_frustum_single(
                -0.5, -0.5, -0.5, 0.5, 0.5, 0.5,
                nx.as_ptr(), ny.as_ptr(), nz.as_ptr(), d.as_ptr(),
            );
            assert_eq!(inside, 1);
        }
    }
}

