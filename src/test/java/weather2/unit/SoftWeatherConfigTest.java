package weather2.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.climate.ClimateEngine;
import weather2.climate.ClimateProfile;
import weather2.climate.ClimateProfileRegistry;
import weather2.config.ConfigMisc;
import weather2.harness.TestEnvironment;
import weather2.util.WeatherUtilConfig;

public class SoftWeatherConfigTest {

    private String profiles;
    private String weather;
    private String clouds;
    private String storms;
    private String windEffects;
    private boolean aimAtPlayer;
    private boolean noWindEvents;
    private int tornadoDuration;

    @Before
    public void setUp() {
        this.profiles = ConfigMisc.Dimension_Weather_Profiles;
        this.weather = ConfigMisc.Dimension_List_Weather;
        this.clouds = ConfigMisc.Dimension_List_Clouds;
        this.storms = ConfigMisc.Dimension_List_Storms;
        this.windEffects = ConfigMisc.Dimension_List_WindEffects;
        this.aimAtPlayer = ConfigMisc.Storm_Tornado_aimAtPlayerOnSpawn;
        this.noWindEvents = ConfigMisc.Wind_NoWindEvents;
        this.tornadoDuration = ConfigMisc.Tornado_DefaultDurationTicks;
        TestEnvironment.resetAll();
    }

    @After
    public void tearDown() {
        ConfigMisc.Dimension_Weather_Profiles = this.profiles;
        ConfigMisc.Dimension_List_Weather = this.weather;
        ConfigMisc.Dimension_List_Clouds = this.clouds;
        ConfigMisc.Dimension_List_Storms = this.storms;
        ConfigMisc.Dimension_List_WindEffects = this.windEffects;
        ConfigMisc.Storm_Tornado_aimAtPlayerOnSpawn = this.aimAtPlayer;
        ConfigMisc.Wind_NoWindEvents = this.noWindEvents;
        ConfigMisc.Tornado_DefaultDurationTicks = this.tornadoDuration;
        WeatherUtilConfig.processLists();
        TestEnvironment.resetAll();
    }

    @Test
    public void freshDefaultsAgreeAcrossAllProfileSources() {
        assertEquals("0, 5, false, 72000, 0.25, 30, 0, false", ClimateEngine.DEFAULT_WEATHER_PROFILES);
        assertEquals(ClimateEngine.DEFAULT_WEATHER_PROFILES, ConfigMisc.Dimension_Weather_Profiles);
        assertEquals("0", ConfigMisc.Dimension_List_Weather);
        assertEquals("0", ConfigMisc.Dimension_List_Clouds);
        assertEquals("0", ConfigMisc.Dimension_List_Storms);
        assertEquals("0", ConfigMisc.Dimension_List_WindEffects);
        assertEquals(2400, ConfigMisc.Tornado_DefaultDurationTicks);
        assertFalse(ConfigMisc.Storm_Tornado_aimAtPlayerOnSpawn);
        assertFalse(ConfigMisc.Wind_NoWindEvents);

        ClimateEngine.ensureProfilesLoaded();
        ClimateProfile overworld = ClimateProfileRegistry.get(0);
        assertEquals(5, overworld.maxStage);
        assertFalse(overworld.alwaysProgresses);
        assertEquals(72000, overworld.deadlyCooldown);
        assertEquals(0.25F, overworld.lightningMultiplier, 0.001F);
        assertEquals(30, overworld.landSpawnOdds);
        assertEquals(0, overworld.oceanSpawnOdds);
        assertFalse(overworld.grabBlocks);
        assertFalse(ClimateProfileRegistry.get(16).weatherEnabled);
        assertFalse(ClimateProfileRegistry.get(18).weatherEnabled);
        assertFalse(ClimateProfileRegistry.get(22).weatherEnabled);
        assertFalse(ClimateProfileRegistry.get(24).weatherEnabled);

        WeatherUtilConfig.processLists();
        assertEquals(Arrays.asList(Integer.valueOf(0)), WeatherUtilConfig.listDimensionsWeather);
        assertEquals(Arrays.asList(Integer.valueOf(0)), WeatherUtilConfig.listDimensionsStorms);
    }

    @Test
    public void legacySixAndEightFieldProfilesRemainReadable() {
        ClimateProfileRegistry.loadFromConfig("duna, 5, 72000, 0.25, 30, true");
        ClimateProfile sixFields = ClimateProfileRegistry.get(16);
        assertEquals(5, sixFields.maxStage);
        assertFalse(sixFields.alwaysProgresses);
        assertEquals(72000, sixFields.deadlyCooldown);
        assertEquals(0.25F, sixFields.lightningMultiplier, 0.001F);
        assertEquals(30, sixFields.landSpawnOdds);

        ClimateProfileRegistry.loadFromConfig("eve, 8, true, 800, 6.0, 3, 7, true");
        ClimateProfile eightFields = ClimateProfileRegistry.get(18);
        assertEquals(8, eightFields.maxStage);
        assertTrue(eightFields.alwaysProgresses);
        assertEquals(800, eightFields.deadlyCooldown);
        assertEquals(6.0F, eightFields.lightningMultiplier, 0.001F);
        assertEquals(3, eightFields.landSpawnOdds);
        assertEquals(7, eightFields.oceanSpawnOdds);
    }

    @Test
    public void legacyDestructionSettingsCannotOverrideSafety() {
        ClimateProfileRegistry.loadFromConfig("duna, 9, true, 800, 6.0, 3, 7, true");
        assertFalse(ClimateProfileRegistry.get(16).grabBlocks);
        assertFalse(ClimateProfileRegistry.isGrabAllowed(16));
        assertFalse(ClimateEngine.isDimensionGrabAllowed(16));

        ClimateProfile programmatic = ClimateProfile.builder(77, "legacy")
            .grabBlocks(true)
            .build();
        ClimateProfileRegistry.register(programmatic);
        assertFalse(programmatic.grabBlocks);
        assertFalse(ClimateProfileRegistry.isGrabAllowed(77));
    }

    @Test
    public void oldBuiltInModpackDefaultsMigrateToSoftProfile() {
        String oldDefaults = "0, 5, false, 3600, 0.25, 25, 0, true; " + "duna, 9, false, 1800, 1.0, 10, 0, true; "
            + "eve, 9, true, 800, 6.0, 3, 7, true; "
            + "laythe, 9, false, 1800, 1.0, 10, 20, true; "
            + "tekto, 8, false, 1800, 2.0, 10, 0, true";
        ClimateProfileRegistry.loadFromConfig(oldDefaults);

        assertEquals(72000, ClimateProfileRegistry.get(0).deadlyCooldown);
        assertEquals(30, ClimateProfileRegistry.get(0).landSpawnOdds);
        assertFalse(ClimateProfileRegistry.get(0).grabBlocks);
        assertFalse(ClimateProfileRegistry.get(16).weatherEnabled);
        assertFalse(ClimateProfileRegistry.get(18).weatherEnabled);
        assertFalse(ClimateProfileRegistry.get(22).weatherEnabled);
        assertFalse(ClimateProfileRegistry.get(24).weatherEnabled);
    }

    @Test
    public void unknownDimensionsDoNotEnableWeather() {
        ClimateProfileRegistry.loadFromConfig("madeupmoon, 9, true, 800, 6.0, 3, 7, true");
        assertFalse(ClimateProfileRegistry.has(999));
        assertFalse(ClimateProfileRegistry.get(999).weatherEnabled);
    }

    @Test
    public void oldDimensionListsMigrateOnlyWhenTheyMatchThePreviousDefaults() {
        ConfigMisc.Dimension_List_Weather = "0,-127";
        ConfigMisc.Dimension_List_Clouds = "0 -127";
        ConfigMisc.Dimension_List_Storms = "0, -127";
        ConfigMisc.Dimension_List_WindEffects = "0,-127";

        WeatherUtilConfig.processLists();

        assertEquals("0", ConfigMisc.Dimension_List_Weather);
        assertEquals("0", ConfigMisc.Dimension_List_Clouds);
        assertEquals("0", ConfigMisc.Dimension_List_Storms);
        assertEquals("0", ConfigMisc.Dimension_List_WindEffects);
        assertEquals(Arrays.asList(Integer.valueOf(0)), WeatherUtilConfig.listDimensionsWeather);
    }
}
