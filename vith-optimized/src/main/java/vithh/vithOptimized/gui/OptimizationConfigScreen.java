package vithh.vithOptimized.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import vithh.vithOptimized.config.OptimizationConfig;
import vithh.vithOptimized.core.OptimizationEngine;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class OptimizationConfigScreen extends Screen {

    private final Screen parent;
    private final OptimizationConfig config;
    private int currentTab = 0;

    private static final String[] TAB_NAMES = {
            "CPU & Threads",
            "Culling",
            "GPU & FrameGen",
            "Math & Physics"
    };

    public OptimizationConfigScreen(Screen parent) {
        super(Component.literal("Vith Optimized Configuration"));
        this.parent = parent;
        this.config = OptimizationConfig.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        buildSettingsUI();
    }

    private void buildSettingsUI() {
        this.clearWidgets();

        int tabWidth = Math.min(100, (this.width - 40) / TAB_NAMES.length);
        int totalTabsWidth = tabWidth * TAB_NAMES.length;
        int tabStartX = (this.width - totalTabsWidth) / 2;

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tabIdx = i;
            String label = (currentTab == tabIdx ? "> " : "") + TAB_NAMES[i];
            this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                currentTab = tabIdx;
                buildSettingsUI();
            }).bounds(tabStartX + i * tabWidth, 20, tabWidth - 2, 20).build());
        }

        int colWidth = Math.min(170, (this.width - 60) / 2);
        int leftX = (this.width / 2) - colWidth - 6;
        int rightX = (this.width / 2) + 6;
        int startY = 48;
        int rowHeight = 24;

        if (currentTab == 0) {
            addToggleWidget(leftX, startY, colWidth, "Multi-Threading CPU", () -> config.multiThreading, val -> config.multiThreading = val);
            addSliderWidget(rightX, startY, colWidth, "Threads", config.threadCount, 2, 32, 1, val -> config.threadCount = (int) (double) val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "P-Core Affinity", () -> config.cpuPriority, val -> config.cpuPriority = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "100% Saturation", () -> config.maxThreadSaturation, val -> config.maxThreadSaturation = val);

            addToggleWidget(leftX, startY + rowHeight * 2, colWidth, "Async Chunk Meshing", () -> config.asyncChunkMeshing, val -> config.asyncChunkMeshing = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "GPU Chunk Offload", () -> config.gpuChunkOffload, val -> config.gpuChunkOffload = val);
        } else if (currentTab == 1) {
            addToggleWidget(leftX, startY, colWidth, "Entity Culling", () -> config.parallelEntityCulling, val -> config.parallelEntityCulling = val);
            addSliderWidget(rightX, startY, colWidth, "Entity Dist", config.entityDistance, 16, 128, 4, val -> config.entityDistance = val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "Cull Invisible Mobs", () -> config.cullInvisible, val -> config.cullInvisible = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "BlockEntity Culling", () -> config.blockEntityCulling, val -> config.blockEntityCulling = val);

            addSliderWidget(leftX, startY + rowHeight * 2, colWidth, "BE Dist", config.blockEntityDistance, 16, 96, 4, val -> config.blockEntityDistance = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "Particle Culling", () -> config.particleCulling, val -> config.particleCulling = val);

            addSliderWidget(leftX, startY + rowHeight * 3, colWidth * 2 + 12, "Max Particles", config.maxParticles, 50, 2000, 50, val -> config.maxParticles = (int) (double) val);
        } else if (currentTab == 2) {
            addToggleWidget(leftX, startY, colWidth, "Universal Frame Gen 2x", () -> config.frameGeneration, val -> config.frameGeneration = val);
            addSliderWidget(rightX, startY, colWidth, "Motion Sharpness", config.frameGenSharpness, 0.0, 2.0, 0.1, val -> config.frameGenSharpness = val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "Disocclusion Shield", () -> config.frameGenDisocclusion, val -> config.frameGenDisocclusion = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "Max GPU Throughput", () -> config.gpuThroughput, val -> config.gpuThroughput = val);

            addToggleWidget(leftX, startY + rowHeight * 2, colWidth, "Single-Pass Blur", () -> config.singlePassBlur, val -> config.singlePassBlur = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "Zero-Alloc Proj", () -> config.zeroAllocProj, val -> config.zeroAllocProj = val);
        } else if (currentTab == 3) {
            addToggleWidget(leftX, startY, colWidth, "FastMath FMA", () -> config.fastMath, val -> config.fastMath = val);
            addToggleWidget(rightX, startY, colWidth, "Native SIMD AVX2", () -> config.nativeSimd, val -> config.nativeSimd = val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "Rust 2.0 Engine", () -> config.rustEngine, val -> config.rustEngine = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "Interpolation Opt", () -> config.entityInterpolationOpt, val -> config.entityInterpolationOpt = val);

            addToggleWidget(leftX, startY + rowHeight * 2, colWidth, "Fluid Physics Opt", () -> config.fluidPhysicsOpt, val -> config.fluidPhysicsOpt = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "Occlusion Hysteresis", () -> config.occlusionHysteresis, val -> config.occlusionHysteresis = val);

            addToggleWidget(leftX, startY + rowHeight * 3, colWidth * 2 + 12, "GC Freeze Shield", () -> config.gcStabilizer, val -> config.gcStabilizer = val);
        }

        int bottomY = this.height - 28;
        int actionBtnWidth = Math.min(140, (this.width - 40) / 2);

        this.addRenderableWidget(Button.builder(Component.literal("Reset Defaults"), btn -> {
            config.resetToDefaults();
            applyAndSync();
            buildSettingsUI();
        }).bounds((this.width / 2) - actionBtnWidth - 6, bottomY, actionBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            applyAndSync();
            this.onClose();
        }).bounds((this.width / 2) + 6, bottomY, actionBtnWidth, 20).build());
    }

    private void addToggleWidget(int x, int y, int width, String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        Button[] btnRef = new Button[1];
        btnRef[0] = Button.builder(Component.literal(name + ": " + (getter.get() ? "ON" : "OFF")), btn -> {
            boolean next = !getter.get();
            setter.accept(next);
            btn.setMessage(Component.literal(name + ": " + (next ? "ON" : "OFF")));
            applyAndSync();
        }).bounds(x, y, width, 20).build();
        this.addRenderableWidget(btnRef[0]);
    }

    private void addSliderWidget(int x, int y, int width, String name, double currentVal, double min, double max, double step, Consumer<Double> setter) {
        this.addRenderableWidget(new AbstractSliderButton(x, y, width, 20, Component.empty(), (currentVal - min) / (max - min)) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double val = min + this.value * (max - min);
                if (step >= 1.0) {
                    val = Math.round(val / step) * step;
                    this.setMessage(Component.literal(name + ": " + (int) val));
                } else {
                    double rounded = Math.round(val * 10.0) / 10.0;
                    this.setMessage(Component.literal(name + ": " + rounded));
                }
            }

            @Override
            protected void applyValue() {
                double val = min + this.value * (max - min);
                if (step >= 1.0) {
                    val = Math.round(val / step) * step;
                } else {
                    val = Math.round(val * 10.0) / 10.0;
                }
                setter.accept(val);
                applyAndSync();
            }
        });
    }

    private void applyAndSync() {
        config.save();
        OptimizationEngine engine = OptimizationEngine.getInstance();
        engine.applyThreadingConfig();
        engine.applyGpuConfig();
        engine.applyChunkConfig();
    }

    @Override
    public void onClose() {
        applyAndSync();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
