package vithh.vithOptimized;

import net.fabricmc.api.ModInitializer;
import vithh.vithOptimized.config.OptimizationConfig;

public class VithOptimized implements ModInitializer {

    @Override
    public void onInitialize() {
        OptimizationConfig.getInstance();
    }
}
