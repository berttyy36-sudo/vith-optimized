#![allow(non_snake_case)]

#[cfg(target_arch = "x86_64")]
use std::arch::x86_64::*;

#[no_mangle]
pub extern "C" fn vith_rust_get_version() -> u32 {
    300
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
    out_corner_light: *mut u32
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
