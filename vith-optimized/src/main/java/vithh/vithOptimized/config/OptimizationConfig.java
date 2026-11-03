package vithh.vithOptimized.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

public class OptimizationConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("VithOptimized-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("vith-optimized.json").toFile();

    private static OptimizationConfig INSTANCE;

    public boolean multiThreading = true;
    public int threadCount = Math.max(2, Math.min(32, Runtime.getRuntime().availableProcessors()));
    public boolean cpuPriority = true;
    public boolean asyncChunkMeshing = true;
    public boolean parallelEntityCulling = true;
    public double entityDistance = 64.0;
    public boolean cullInvisible = true;
    public boolean blockEntityCulling = true;
    public double blockEntityDistance = 48.0;
    public boolean particleCulling = true;
    public int maxParticles = 500;
    public boolean fastMath = true;
    public boolean nativeSimd = true;
    public boolean entityInterpolationOpt = true;
    public boolean fluidPhysicsOpt = true;
    public boolean occlusionHysteresis = true;
    public boolean zeroAllocProj = true;
    public boolean singlePassBlur = true;
    public boolean gpuThroughput = true;
    public boolean gpuChunkOffload = true;
    public boolean rustEngine = true;
    public boolean maxThreadSaturation = true;
    public boolean frameGeneration = true;
    public double frameGenSharpness = 1.0;
    public boolean frameGenDisocclusion = true;
    public boolean gcStabilizer = true;

    public static OptimizationConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static OptimizationConfig load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                OptimizationConfig loaded = GSON.fromJson(reader, OptimizationConfig.class);
                if (loaded != null) {
                    if (loaded.threadCount < 2) {
                        loaded.threadCount = Math.max(2, Runtime.getRuntime().availableProcessors());
                    }
                    return loaded;
                }
            } catch (Throwable t) {
                LOGGER.error("Failed to load vith-optimized config, using defaults: {}", t.getMessage());
            }
        }
        OptimizationConfig config = new OptimizationConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to save vith-optimized config: {}", t.getMessage());
        }
    }

    public void resetToDefaults() {
        multiThreading = true;
        threadCount = Math.max(2, Math.min(32, Runtime.getRuntime().availableProcessors()));
        cpuPriority = true;
        asyncChunkMeshing = true;
        parallelEntityCulling = true;
        entityDistance = 64.0;
        cullInvisible = true;
        blockEntityCulling = true;
        blockEntityDistance = 48.0;
        particleCulling = true;
        maxParticles = 500;
        fastMath = true;
        nativeSimd = true;
        entityInterpolationOpt = true;
        fluidPhysicsOpt = true;
        occlusionHysteresis = true;
        zeroAllocProj = true;
        singlePassBlur = true;
        gpuThroughput = true;
        gpuChunkOffload = true;
        rustEngine = true;
        maxThreadSaturation = true;
        frameGeneration = true;
        frameGenSharpness = 1.0;
        frameGenDisocclusion = true;
        gcStabilizer = true;
        save();
    }
}
