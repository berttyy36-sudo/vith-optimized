package vithh.vithOptimized;

import net.fabricmc.api.ClientModInitializer;
import vithh.vithOptimized.core.OptimizationEngine;

public class VithOptimizedClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OptimizationEngine.getInstance().init();
    }
}
