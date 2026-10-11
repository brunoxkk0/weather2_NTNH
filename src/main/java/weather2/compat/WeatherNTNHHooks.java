package weather2.compat;

import weather2.climate.ClimateEngine;
import weather2.climate.ClimateProfile;
import weather2.integration.DSurroundBridge;
import weather2.weathersystem.storm.StormObject;

/**
 * NTNH Architectural Facade (Unified Seam).
 * Provides clean static entry points for all ASM bytecode patches and game engine hooks.
 * Internally delegates climate and Dynamic Surroundings hooks.
 */
public class WeatherNTNHHooks {

    // ----------------------------------------------------
    // Climate & Planetary Hooks
    // ----------------------------------------------------
    public static int resolveDimensionId(String token) {
        return ClimateEngine.resolveDimensionId(token);
    }

    public static void ensureProfilesLoaded() {
        ClimateEngine.ensureProfilesLoaded();
    }

    public static ClimateProfile getProfile(int dim) {
        return ClimateEngine.getProfile(dim);
    }

    public static ClimateProfile getProfile(StormObject storm) {
        return ClimateEngine.getProfile(storm);
    }

    public static boolean isEve(StormObject storm) {
        return ClimateEngine.isEve(storm);
    }

    public static boolean isTekto(StormObject storm) {
        return ClimateEngine.isTekto(storm);
    }

    public static void onInitRealStorm(StormObject storm) {
        ClimateEngine.onInitRealStorm(storm);
    }

    public static int getAdjustedLightningOdds(int baseOdds, StormObject storm) {
        return ClimateEngine.getAdjustedLightningOdds(baseOdds, storm);
    }

    public static int getDeadlyTimeBetween(int defaultTicks, StormObject storm) {
        return ClimateEngine.getDeadlyTimeBetween(defaultTicks, storm);
    }

    public static int getLandStormOdds(int defaultOdds, StormObject storm) {
        return ClimateEngine.getLandStormOdds(defaultOdds, storm);
    }

    public static int getOceanStormOdds(int defaultOdds, StormObject storm) {
        return ClimateEngine.getOceanStormOdds(defaultOdds, storm);
    }

    public static boolean shouldSpawnFunnel(StormObject storm) {
        return ClimateEngine.shouldSpawnFunnel(storm);
    }

    // ----------------------------------------------------
    // Audio, Command & Dynamic Surroundings Hooks
    // ----------------------------------------------------
    public static void setWorldRainStrength(Object world, float strength) {
        DSurroundBridge.setWorldRainStrength(world, strength);
    }

    public static void updateDSurroundIntensity(float strength) {
        DSurroundBridge.updateDSurroundIntensity(strength);
    }

    public static void onPrecipitationTick(float curPrecipStr) {
        DSurroundBridge.onPrecipitationTick(curPrecipStr);
    }

    public static void resetPrecipitation() {
        DSurroundBridge.resetPrecipitation();
    }

    public static float getAdjustedThunderVolume(float baseVol) {
        return DSurroundBridge.getAdjustedThunderVolume(baseVol);
    }

    public static float getAdjustedWindVolume(float baseVol) {
        return DSurroundBridge.getAdjustedWindVolume(baseVol);
    }

    public static String[] normalizeCommandArgs(String[] args) {
        return DSurroundBridge.normalizeCommandArgs(args);
    }
}
