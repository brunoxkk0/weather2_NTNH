package weather2.config;

import java.io.File;

import modconfig.ConfigComment;
import modconfig.IConfigCategory;
import weather2.climate.ClimateEngine;
import weather2.util.WeatherUtilConfig;

public class ConfigMisc implements IConfigCategory {

    public static boolean Misc_proxyRenderOverrideEnabled = true;
    public static boolean Misc_windOn = true;
    public static int Misc_simBoxRadiusCutoff = 1024;
    public static int Misc_simBoxRadiusSpawn = 1024;
    public static boolean Misc_ForceVanillaCloudsOff = true;
    public static int Misc_AutoDataSaveIntervalInTicks = '\u8ca0';
    public static boolean consoleDebug = false;
    @ConfigComment({
        "If true, lets server side do vanilla weather rules, weather2 will only make storms when server side says \'rain\' is on" })
    public static boolean overcastMode = false;
    @ConfigComment({
        "Used if overcastMode is off, 1 = lock weather on, 0 = lock weather off, -1 = dont lock anything, let server do whatever" })
    public static int lockServerWeatherMode = 0;
    public static boolean preventServerThunderstorms = true;
    @ConfigComment({ "How often in ticks the clients should check the server\'s weather status" })
    public static int tickerRateSyncWeatherCheckVanilla = 400;
    @ConfigComment({
        "How often in ticks to sync low-wind storm data between server and clients. Might want to increase this for dedicated servers." })
    public static int tickerRateSyncWeatherLowWind = 40;
    @ConfigComment({
        "How often in ticks to sync high-wind storm data between server and clients. Might want to increase this for dedicated servers." })
    public static int tickerRateSyncWeatherHighWind = 2;
    @ConfigComment({ "How often in ticks to sync volcano data between server and clients." })
    public static int tickerRateSyncVolcanos = 40;
    @ConfigComment({ "How often in ticks to sync wind data and do other IMC stuff between server and clients." })
    public static int tickerRateSyncWindAndIMC = 60;
    @ConfigComment({
        "How often in ticks to run the storm spawn/remove checks. CHANGING THIS WILL CHANGE STORM FREQUENCY BY THE SAME FACTOR e.g. doubling this will make storms twice as rare (but will also take twice as long to \'remove\' storms that are \'dead\')." })
    public static int tickerRateSyncStormSpawnOrRemoveChecks = 20;
    @ConfigComment({
        "Weather profile for each configured dimension: dimToken, maxStage, alwaysProgresses, deadlyCooldown, lightningMultiplier, landSpawnOdds, oceanSpawnOdds, legacyGrabBlocks" })
    public static String Dimension_Weather_Profiles = ClimateEngine.DEFAULT_WEATHER_PROFILES;
    @ConfigComment({ "Maximum duration of a natural tornado funnel in ticks" })
    public static int Tornado_DefaultDurationTicks = 2400;
    @ConfigComment({ "Make tornados initial heading aimed towards closest player" })
    public static boolean Storm_Tornado_aimAtPlayerOnSpawn = false;
    @ConfigComment({ "Accuracy of tornado aimed at player" })
    public static int Storm_Tornado_aimAtPlayerAngleVariance = 5;
    @ConfigComment({ "Makes weather boring! or peacefull?" })
    public static boolean Storm_NoTornadosOrCyclones = false;
    public static int Storm_OddsTo1OfHighWindWaterSpout = 150;
    @ConfigComment({
        "Maximum possible stormfronts (or, more corrently, cloud formations) per player. Reducing this will improve network bandwidth." })
    public static int Storm_MaxPerPlayerPerLayer = 20;
    public static int Storm_Deadly_CollideDistance = 128;
    public static int Storm_LightningStrikeBaseValueOddsTo1 = 200;
    public static boolean Storm_NoRainVisual = false;
    public static int Storm_MaxRadius = 300;
    public static int Storm_AllTypes_TickRateDelay = 60;
    public static int Storm_Rain_WaterBuildUpRate = 10;
    public static int Storm_Rain_WaterSpendRate = 3;
    public static int Storm_Rain_WaterBuildUpOddsTo1FromSource = 15;
    public static int Storm_Rain_WaterBuildUpOddsTo1FromNothing = 100;
    public static int Storm_Rain_WaterBuildUp = 150;
    public static double Storm_TemperatureAdjustRate = 0.1D;
    public static int Storm_HailPerTick = 10;
    public static int Storm_OddsTo1OfOceanBasedStorm = 300;
    public static int Storm_OddsTo1OfLandBasedStorm = -1;
    public static int Storm_OddsTo1OfProgressionBase = 15;
    public static int Storm_OddsTo1OfProgressionStageMultiplier = 3;
    public static int Storm_ParticleSpawnDelay = 0;
    public static int Player_Storm_Deadly_OddsTo1 = 30;
    public static int Player_Storm_Deadly_TimeBetweenInTicks = 72000;
    public static int Player_Storm_Rain_OddsTo1 = 150;
    public static boolean Server_Storm_Deadly_UseGlobalRate = false;
    public static int Server_Storm_Deadly_OddsTo1 = 30;
    public static int Server_Storm_Deadly_TimeBetweenInTicks = 72000;
    public static int Cloud_ParticleSpawnDelay = 0;
    public static int Cloud_Formation_MinDistBetweenSpawned = 256;
    public static boolean Cloud_Layer1_Enable = false;
    public static int Cloud_Layer0_Height = 200;
    public static boolean Wind_Particle_leafs = true;
    public static double Wind_Particle_effect_rate = 1.0D;
    public static boolean Wind_Particle_air = true;
    public static boolean Wind_Particle_sand = true;
    public static boolean Wind_Particle_waterfall = true;
    public static boolean Wind_Particle_fire = true;
    public static boolean Wind_NoWindEvents = false;
    public static int Thread_Particle_Process_Delay = 400;
    public static boolean Particle_RainSnow = true;
    public static boolean Particle_VanillaAndWeatherOnly = false;
    public static double Particle_Precipitation_effect_rate = 1.0D;
    public static double volWindScale = 0.05D;
    public static double volWaterfallScale = 0.5D;
    public static double volWindTreesScale = 0.5D;
    @ConfigComment({ "Volume scale for tornado sounds, from 0 (muted) to 1 (full volume)" })
    public static double volTornadoScale = 1.0D;
    @ConfigComment({ "Volume scale for siren sounds, from 0 (muted) to 1 (full volume)" })
    public static double volSirenScale = 1.0D;
    @ConfigComment({ "Volume scale for thunder and lightning sounds, from 0 (muted) to 1 (full volume)" })
    public static double volThunderScale = 1.0D;
    public static double sirenActivateDistance = 256.0D;
    public static double sensorActivateDistance = 256.0D;
    // NTNH: [FIX] (registry): Removed legacy 1.6.4 Block_*ID fields; Forge 1.7.10 auto-assigns block IDs via
    // GameRegistry
    public static boolean Block_WeatherMachineNoTornadosOrCyclones = false;
    public static String Dimension_List_Weather = "0";
    public static String Dimension_List_Clouds = "0";
    public static String Dimension_List_Storms = "0";
    public static String Dimension_List_WindEffects = "0";

    public String getConfigFileName() {
        return "Weather2" + File.separator + "Misc";
    }

    public String getCategory() {
        return "Weather2: Misc";
    }

    public void hookUpdatedValues() {
        WeatherUtilConfig.processLists();
        volTornadoScale = normalizeAudioScale(volTornadoScale);
        volSirenScale = normalizeAudioScale(volSirenScale);
        volThunderScale = normalizeAudioScale(volThunderScale);
        if (Storm_MaxRadius < 1) {
            Storm_MaxRadius = 1;
        }
        Tornado_DefaultDurationTicks = Math.max(1, Tornado_DefaultDurationTicks);

    }

    private static double normalizeAudioScale(double scale) {
        if (Double.isNaN(scale) || Double.isInfinite(scale)) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, scale));
    }

}
