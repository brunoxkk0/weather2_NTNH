package weather2.harness;

import weather2.climate.ClimateProfileRegistry;
import weather2.integration.IntegrationManager;

/**
 * Headless test environment controller for Weather2.
 * Allows simulating vanilla, compatible-mod, or degraded states in tests.
 */
public final class TestEnvironment {

    static {
        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}
    }

    private TestEnvironment() {}

    /**
     * Resets all internal caches, registries, and pipelines to clean defaults.
     */
    public static void resetAll() {
        MockBlockFactory.initVanillaBlocks();
        IntegrationManager.resetCache();
        ClimateProfileRegistry.reset();

    }

    /**
     * Simulates a standalone environment where Dynamic Surroundings is absent.
     */
    public static void setupVanilla() {
        resetAll();
        IntegrationManager.setDSurroundLoaded(false);
    }

    /**
     * Simulates an environment with Dynamic Surroundings.
     */
    public static void setupNTNH() {
        resetAll();
        IntegrationManager.setDSurroundLoaded(true);
    }
}
