package weather2.weathersystem;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.climate.ClimateEngine;
import weather2.climate.ClimateProfile;
import weather2.climate.ClimateProfileRegistry;
import weather2.config.ConfigMisc;
import weather2.harness.MockStormFactory;
import weather2.harness.TestEnvironment;
import weather2.player.PlayerData;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.storm.StormObject;

public class StormCooldownTest {

    private final String oldProfiles = ConfigMisc.Dimension_Weather_Profiles;
    private final ArrayList oldStormDimensions = new ArrayList(WeatherUtilConfig.listDimensionsStorms);
    private final boolean oldGlobalRate = ConfigMisc.Server_Storm_Deadly_UseGlobalRate;
    private final int oldServerInterval = ConfigMisc.Server_Storm_Deadly_TimeBetweenInTicks;
    private final int oldPlayerInterval = ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks;
    private final HashMap oldPlayerData = PlayerData.playerNBT;

    @Before
    public void setUp() {
        TestEnvironment.resetAll();
        ClimateEngine.getProfile(0);
        WeatherUtilConfig.listDimensionsStorms = new ArrayList();
        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = false;
        ConfigMisc.Server_Storm_Deadly_TimeBetweenInTicks = 72000;
        ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks = 72000;
        PlayerData.playerNBT = new HashMap();
    }

    @After
    public void tearDown() {
        ConfigMisc.Dimension_Weather_Profiles = this.oldProfiles;
        WeatherUtilConfig.listDimensionsStorms = this.oldStormDimensions;
        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = this.oldGlobalRate;
        ConfigMisc.Server_Storm_Deadly_TimeBetweenInTicks = this.oldServerInterval;
        ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks = this.oldPlayerInterval;
        PlayerData.playerNBT = this.oldPlayerData;
        TestEnvironment.resetAll();
    }

    @Test
    public void remainingCooldownUsesWorldAgeAndInclusiveReadyBoundary() {
        assertEquals(71900L, ClimateEngine.remainingCooldownTicks(100L, 0L, 72000));
        assertEquals(0L, ClimateEngine.remainingCooldownTicks(72000L, 0L, 72000));
        assertEquals(100L, ClimateEngine.remainingCooldownTicks(1000L, 800L, 300));
        assertEquals(0L, ClimateEngine.remainingCooldownTicks(1100L, 800L, 300));
        assertEquals(300L, ClimateEngine.remainingCooldownTicks(1000L, 2000L, 300));
        assertEquals(0L, ClimateEngine.remainingCooldownTicks(Long.MAX_VALUE, 1L, 300));
        assertEquals(-1L, ClimateEngine.remainingCooldownTicks(100L, 0L, 0));
    }

    @Test
    public void naturalCooldownRequiresEnabledStormAndAtLeastOneSpawnChannel() {
        int dimension = 901;
        WeatherUtilConfig.listDimensionsStorms.add(Integer.valueOf(dimension));
        register(dimension, true, StormObject.STATE_THUNDER, 300, 30, 0);
        assertEquals(300, ClimateEngine.getNaturalStormCooldownTicks(dimension, false));

        register(dimension, false, StormObject.STATE_THUNDER, 300, 30, 0);
        assertEquals(-1, ClimateEngine.getNaturalStormCooldownTicks(dimension, false));
        register(dimension, true, 0, 300, 30, 0);
        assertEquals(-1, ClimateEngine.getNaturalStormCooldownTicks(dimension, false));
        register(dimension, true, StormObject.STATE_THUNDER, 0, 30, 0);
        assertEquals(-1, ClimateEngine.getNaturalStormCooldownTicks(dimension, false));
        register(dimension, true, StormObject.STATE_THUNDER, 300, 0, 0);
        assertEquals(-1, ClimateEngine.getNaturalStormCooldownTicks(dimension, false));
    }

    @Test
    public void selectedModeCanDisableNaturalFormationAndProfileCooldownWins() {
        int dimension = 902;
        WeatherUtilConfig.listDimensionsStorms.add(Integer.valueOf(dimension));
        register(dimension, true, StormObject.STATE_THUNDER, 300, 30, 0);
        ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks = -1;
        assertEquals(-1, ClimateEngine.getNaturalStormCooldownTicks(dimension, false));

        ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks = 100;
        assertEquals(300, ClimateEngine.getNaturalStormCooldownTicks(dimension, false));
        ConfigMisc.Server_Storm_Deadly_TimeBetweenInTicks = -1;
        assertEquals(-1, ClimateEngine.getNaturalStormCooldownTicks(dimension, true));
    }

    @Test
    public void naturalFormationRecordsOnlyTheSelectedClock() {
        StormObject storm = MockStormFactory.create(StormObject.STATE_THUNDER, 1L, 7);
        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = true;
        storm.recordNaturalStormFormation(123L);
        assertEquals(123L, storm.manager.lastStormFormed);
        assertEquals(300L, ClimateEngine.remainingCooldownTicks(123L, storm.manager.lastStormFormed, 300));
        assertEquals(
            0L,
            PlayerData.getPlayerNBT(storm.userSpawnedFor)
                .getLong("lastStormDeadlyTime"));

        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = false;
        storm.recordNaturalStormFormation(456L);
        assertEquals(123L, storm.manager.lastStormFormed);
        assertEquals(
            456L,
            PlayerData.getPlayerNBT(storm.userSpawnedFor)
                .getLong("lastStormDeadlyTime"));
    }

    private static void register(int dimension, boolean enabled, int maxStage, int cooldown, int landOdds,
        int oceanOdds) {
        ClimateProfileRegistry.register(
            ClimateProfile.builder(dimension, "test_" + dimension)
                .weatherEnabled(enabled)
                .exactStage(maxStage)
                .deadlyCooldown(cooldown)
                .spawnOdds(landOdds, oceanOdds)
                .build());
    }
}
