package vithh.vithOptimized.gui;

import java.util.HashMap;
import java.util.Map;

public class I18n {

    public static final String EN = "en";
    public static final String RU = "ru";
    public static final String ES = "es";
    public static final String ZH = "zh";

    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();

    static {
        TRANSLATIONS.put(EN, new HashMap<>());
        TRANSLATIONS.put(RU, new HashMap<>());
        TRANSLATIONS.put(ES, new HashMap<>());
        TRANSLATIONS.put(ZH, new HashMap<>());

        put("screen.title", "Vith Optimized Configuration", "Настройки Vith Optimized", "Configuración de Vith Optimized", "Vith Optimized 配置");

        put("tab.cpu", "CPU & Threads", "ЦП и потоки", "CPU e hilos", "CPU与线程");
        put("tab.culling", "Culling", "Отсечение", "Oclusión", "剔除优化");
        put("tab.gpu", "GPU & FrameGen", "ГПУ и FrameGen", "GPU y FrameGen", "GPU与帧生成");
        put("tab.math", "Math & Physics", "Мат. и физика", "Mat. y física", "数学与物理");

        put("opt.cpu.multithreading", "Multi-Threading CPU", "Многопоточный ЦП", "Multihilo de CPU", "CPU 多线程");
        put("opt.cpu.threads", "Threads", "Потоки", "Hilos", "线程数");
        put("opt.cpu.pcore", "P-Core Affinity", "Приоритет P-ядер", "Afinidad de P-Cores", "性能核优先级");
        put("opt.cpu.saturation", "100% Saturation", "100% Насыщение", "100% Saturación", "100% 满载饱和");
        put("opt.cpu.async_meshing", "Async Chunk Meshing", "Асинхр. мешинг", "Mallado asíncrono", "异步区块构建");
        put("opt.cpu.gpu_offload", "GPU Chunk Offload", "Выгрузка в GPU", "Descarga a GPU", "GPU 区块分流");

        put("opt.cull.entity", "Entity Culling", "Отсечение сущностей", "Oclusión de entidades", "实体剔除");
        put("opt.cull.entity_dist", "Entity Dist", "Дист. сущностей", "Dist. de entidades", "实体渲染距离");
        put("opt.cull.invisible", "Cull Invisible Mobs", "Скрыть невидим. мобов", "Ocultar invisibles", "剔除隐形生物");
        put("opt.cull.block_entity", "BlockEntity Culling", "Отсечение блоков", "Oclusión de bloques", "方块实体剔除");
        put("opt.cull.be_dist", "BE Dist", "Дист. блоков", "Dist. de bloques", "方块实体距离");
        put("opt.cull.particle", "Particle Culling", "Отсечение частиц", "Oclusión de partículas", "粒子剔除");
        put("opt.cull.max_particles", "Max Particles", "Макс. частиц", "Máx. partículas", "最大粒子数");
        put("opt.cull.entity_ticks", "Entity Tick Culling", "Отсечение тиков сущн.", "Oclusión ticks entidad", "实体 Tick 优化");

        put("opt.gpu.framegen", "Universal Frame Gen 2x", "Генерация кадров 2x", "Gen. fotogramas 2x", "通用双倍帧生成");
        put("opt.gpu.sharpness", "Motion Sharpness", "Резкость движения", "Nitidez movimiento", "动态锐化度");
        put("opt.gpu.disocclusion", "Disocclusion Shield", "Защита артефактов", "Escudo oclusión", "遮挡重建保护");
        put("opt.gpu.throughput", "Max GPU Throughput", "Макс. пропускная GPU", "Máx. rendimiento GPU", "最大 GPU 吞吐量");
        put("opt.gpu.single_pass_blur", "Single-Pass Blur", "Однопроходный блюр", "Desenfoque rápido", "单通道运动模糊");
        put("opt.gpu.zero_alloc", "Zero-Alloc Proj", "Zero-Alloc проекция", "Proy. sin asignación", "零内存分配投影");

        put("opt.math.fast_math", "FastMath FMA", "Быстрая мат. FMA", "FastMath FMA", "快速数学 FMA");
        put("opt.math.simd", "Native SIMD AVX2", "Нативный SIMD AVX2", "SIMD AVX2 nativo", "原生 SIMD AVX2");
        put("opt.math.rust", "Rust 2.0 Engine", "Движок Rust 2.0", "Motor Rust 2.0", "Rust 2.0 引擎");
        put("opt.math.interpolation", "Interpolation Opt", "Оптимиз. интерполяции", "Optim. interpolación", "插值平滑优化");
        put("opt.math.fluid", "Fluid Physics Opt", "Физика жидкостей", "Física de fluidos", "流体物理优化");
        put("opt.math.hysteresis", "Occlusion Hysteresis", "Гистерезис окклюзии", "Histéresis de oclusión", "遮挡滞后算法");
        put("opt.math.gc", "GC Freeze Shield", "Защита от фризов GC", "Escudo tirones GC", "垃圾回收防卡顿");
        put("opt.math.rust_matrix", "Rust Matrix Pipeline", "Конвейер матриц Rust", "Pipeline matrices Rust", "Rust 矩阵渲染管线");
        put("opt.math.rust_frustum", "Rust Frustum Culling", "Фрустум отсечение Rust", "Oclusión frustum Rust", "Rust 视锥体剔除");
        put("opt.math.rust_lightmap", "Rust Lightmap Engine", "Движок света Rust", "Motor de luz Rust", "Rust 光照贴图引擎");

        put("button.reset", "Reset Defaults", "Сбросить", "Restablecer", "恢复默认");
        put("button.done", "Done", "Готово", "Listo", "完成");

        put("state.on", "ON", "ВКЛ", "SÍ", "开启");
        put("state.off", "OFF", "ВЫКЛ", "NO", "关闭");
    }

    private static void put(String key, String en, String ru, String es, String zh) {
        TRANSLATIONS.get(EN).put(key, en);
        TRANSLATIONS.get(RU).put(key, ru);
        TRANSLATIONS.get(ES).put(key, es);
        TRANSLATIONS.get(ZH).put(key, zh);
    }

    public static String get(String key, String lang) {
        if (lang == null) {
            lang = EN;
        }
        Map<String, String> map = TRANSLATIONS.get(lang.toLowerCase());
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }
        Map<String, String> fallback = TRANSLATIONS.get(EN);
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }
        return key;
    }

    public static String getNextLanguage(String current) {
        if (current == null) {
            return RU;
        }
        String lower = current.toLowerCase();
        if (lower.equals(EN)) {
            return RU;
        }
        if (lower.equals(RU)) {
            return ES;
        }
        if (lower.equals(ES)) {
            return ZH;
        }
        return EN;
    }

    public static String getLanguageButtonText(String lang) {
        if (lang == null) {
            lang = EN;
        }
        String lower = lang.toLowerCase();
        if (lower.equals(RU)) {
            return "Язык: Русский";
        }
        if (lower.equals(ES)) {
            return "Idioma: Español";
        }
        if (lower.equals(ZH)) {
            return "语言: 中文";
        }
        return "Language: English";
    }
}
