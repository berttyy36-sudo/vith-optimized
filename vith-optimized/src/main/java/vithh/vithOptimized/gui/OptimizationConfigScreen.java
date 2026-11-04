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

    private static final String[] TAB_KEYS = {
            "tab.cpu",
            "tab.culling",
            "tab.gpu",
            "tab.math"
    };

    public OptimizationConfigScreen(Screen parent) {
        super(Component.literal(I18n.get("screen.title", OptimizationConfig.getInstance().language)));
        this.parent = parent;
        this.config = OptimizationConfig.getInstance();
    }

    @Override
    public Component getTitle() {
        return Component.literal(I18n.get("screen.title", config.language));
    }

    @Override
    protected void init() {
        super.init();
        buildSettingsUI();
    }

    private void buildSettingsUI() {
        this.clearWidgets();

        int tabWidth = Math.min(100, (this.width - 40) / TAB_KEYS.length);
        int totalTabsWidth = tabWidth * TAB_KEYS.length;
        int tabStartX = (this.width - totalTabsWidth) / 2;

        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int tabIdx = i;
            String tabTitle = I18n.get(TAB_KEYS[i], config.language);
            String label = (currentTab == tabIdx ? "> " : "") + tabTitle;
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
            addToggleWidget(leftX, startY, colWidth, "opt.cpu.multithreading", () -> config.multiThreading, val -> config.multiThreading = val);
            addSliderWidget(rightX, startY, colWidth, "opt.cpu.threads", config.threadCount, 2, 32, 1, val -> config.threadCount = (int) (double) val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "opt.cpu.pcore", () -> config.cpuPriority, val -> config.cpuPriority = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "opt.cpu.saturation", () -> config.maxThreadSaturation, val -> config.maxThreadSaturation = val);

            addToggleWidget(leftX, startY + rowHeight * 2, colWidth, "opt.cpu.async_meshing", () -> config.asyncChunkMeshing, val -> config.asyncChunkMeshing = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "opt.cpu.gpu_offload", () -> config.gpuChunkOffload, val -> config.gpuChunkOffload = val);
        } else if (currentTab == 1) {
            addToggleWidget(leftX, startY, colWidth, "opt.cull.entity", () -> config.parallelEntityCulling, val -> config.parallelEntityCulling = val);
            addSliderWidget(rightX, startY, colWidth, "opt.cull.entity_dist", config.entityDistance, 16, 128, 4, val -> config.entityDistance = val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "opt.cull.invisible", () -> config.cullInvisible, val -> config.cullInvisible = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "opt.cull.block_entity", () -> config.blockEntityCulling, val -> config.blockEntityCulling = val);

            addSliderWidget(leftX, startY + rowHeight * 2, colWidth, "opt.cull.be_dist", config.blockEntityDistance, 16, 96, 4, val -> config.blockEntityDistance = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "opt.cull.particle", () -> config.particleCulling, val -> config.particleCulling = val);

            addSliderWidget(leftX, startY + rowHeight * 3, colWidth, "opt.cull.max_particles", config.maxParticles, 50, 2000, 50, val -> config.maxParticles = (int) (double) val);
            addToggleWidget(rightX, startY + rowHeight * 3, colWidth, "opt.cull.entity_ticks", () -> config.entityTickCulling, val -> config.entityTickCulling = val);
        } else if (currentTab == 2) {
            addToggleWidget(leftX, startY, colWidth, "opt.gpu.framegen", () -> config.frameGeneration, val -> config.frameGeneration = val);
            addSliderWidget(rightX, startY, colWidth, "opt.gpu.sharpness", config.frameGenSharpness, 0.0, 2.0, 0.1, val -> config.frameGenSharpness = val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "opt.gpu.disocclusion", () -> config.frameGenDisocclusion, val -> config.frameGenDisocclusion = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "opt.gpu.throughput", () -> config.gpuThroughput, val -> config.gpuThroughput = val);

            addToggleWidget(leftX, startY + rowHeight * 2, colWidth, "opt.gpu.single_pass_blur", () -> config.singlePassBlur, val -> config.singlePassBlur = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "opt.gpu.zero_alloc", () -> config.zeroAllocProj, val -> config.zeroAllocProj = val);
        } else if (currentTab == 3) {
            addToggleWidget(leftX, startY, colWidth, "opt.math.fast_math", () -> config.fastMath, val -> config.fastMath = val);
            addToggleWidget(rightX, startY, colWidth, "opt.math.simd", () -> config.nativeSimd, val -> config.nativeSimd = val);

            addToggleWidget(leftX, startY + rowHeight, colWidth, "opt.math.rust", () -> config.rustEngine, val -> config.rustEngine = val);
            addToggleWidget(rightX, startY + rowHeight, colWidth, "opt.math.interpolation", () -> config.entityInterpolationOpt, val -> config.entityInterpolationOpt = val);

            addToggleWidget(leftX, startY + rowHeight * 2, colWidth, "opt.math.fluid", () -> config.fluidPhysicsOpt, val -> config.fluidPhysicsOpt = val);
            addToggleWidget(rightX, startY + rowHeight * 2, colWidth, "opt.math.hysteresis", () -> config.occlusionHysteresis, val -> config.occlusionHysteresis = val);

            addToggleWidget(leftX, startY + rowHeight * 3, colWidth, "opt.math.rust_matrix", () -> config.rustMatrixPipeline, val -> config.rustMatrixPipeline = val);
            addToggleWidget(rightX, startY + rowHeight * 3, colWidth, "opt.math.rust_frustum", () -> config.rustFrustumCulling, val -> config.rustFrustumCulling = val);

            addToggleWidget(leftX, startY + rowHeight * 4, colWidth, "opt.math.rust_lightmap", () -> config.rustFastLightmap, val -> config.rustFastLightmap = val);
            addToggleWidget(rightX, startY + rowHeight * 4, colWidth, "opt.math.gc", () -> config.gcStabilizer, val -> config.gcStabilizer = val);
        }

        int bottomY = this.height - 28;
        int actionBtnWidth = Math.min(130, Math.max(80, (this.width - 32) / 3));
        int totalActionWidth = actionBtnWidth * 3 + 8;
        int actionStartX = (this.width - totalActionWidth) / 2;

        this.addRenderableWidget(Button.builder(Component.literal(I18n.get("button.reset", config.language)), btn -> {
            config.resetToDefaults();
            applyAndSync();
            buildSettingsUI();
        }).bounds(actionStartX, bottomY, actionBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(I18n.getLanguageButtonText(config.language)), btn -> {
            config.language = I18n.getNextLanguage(config.language);
            applyAndSync();
            buildSettingsUI();
        }).bounds(actionStartX + actionBtnWidth + 4, bottomY, actionBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(I18n.get("button.done", config.language)), btn -> {
            applyAndSync();
            this.onClose();
        }).bounds(actionStartX + (actionBtnWidth + 4) * 2, bottomY, actionBtnWidth, 20).build());
    }

    private void addToggleWidget(int x, int y, int width, String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        String name = I18n.get(key, config.language);
        String onText = I18n.get("state.on", config.language);
        String offText = I18n.get("state.off", config.language);
        Button[] btnRef = new Button[1];
        btnRef[0] = Button.builder(Component.literal(name + ": " + (getter.get() ? onText : offText)), btn -> {
            boolean next = !getter.get();
            setter.accept(next);
            String nextState = next ? I18n.get("state.on", config.language) : I18n.get("state.off", config.language);
            btn.setMessage(Component.literal(name + ": " + nextState));
            applyAndSync();
        }).bounds(x, y, width, 20).build();
        this.addRenderableWidget(btnRef[0]);
    }

    private void addSliderWidget(int x, int y, int width, String key, double currentVal, double min, double max, double step, Consumer<Double> setter) {
        String name = I18n.get(key, config.language);
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
