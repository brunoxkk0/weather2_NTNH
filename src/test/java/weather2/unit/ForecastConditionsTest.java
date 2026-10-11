package weather2.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.ServerTickHandler;
import weather2.block.TileEntityWeatherForecast;
import weather2.climate.ClimateEngine;
import weather2.climate.ClimateProfile;
import weather2.climate.ClimateProfileRegistry;
import weather2.config.ConfigMisc;
import weather2.harness.MockStormFactory;
import weather2.harness.RecordingWorld;
import weather2.harness.TestEnvironment;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

public class ForecastConditionsTest {

    private final ArrayList oldStormDimensions = new ArrayList(WeatherUtilConfig.listDimensionsStorms);
    private final HashMap oldManagers = ServerTickHandler.lookupDimToWeatherMan;
    private final boolean oldGlobalRate = ConfigMisc.Server_Storm_Deadly_UseGlobalRate;
    private final int oldPlayerCooldown = ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks;
    private final int oldServerOdds = ConfigMisc.Server_Storm_Deadly_OddsTo1;
    private final int oldPlayerOdds = ConfigMisc.Player_Storm_Deadly_OddsTo1;
    private final int oldCollisionDistance = ConfigMisc.Storm_Deadly_CollideDistance;

    @Before
    public void setUp() {
        TestEnvironment.resetAll();
        WeatherUtilConfig.listDimensionsStorms = new ArrayList();
        WeatherUtilConfig.listDimensionsStorms.add(Integer.valueOf(0));
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = true;
        ConfigMisc.Server_Storm_Deadly_OddsTo1 = 30;
        ConfigMisc.Player_Storm_Deadly_OddsTo1 = 30;
        ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks = 72000;
        ConfigMisc.Storm_Deadly_CollideDistance = 128;
        ClimateEngine.getProfile(0);
        registerProfile(true, StormObject.STATE_STAGE3, 300, 30, 0);
    }

    @After
    public void tearDown() {
        WeatherUtilConfig.listDimensionsStorms = this.oldStormDimensions;
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = this.oldGlobalRate;
        ConfigMisc.Player_Storm_Deadly_TimeBetweenInTicks = this.oldPlayerCooldown;
        ConfigMisc.Server_Storm_Deadly_OddsTo1 = this.oldServerOdds;
        ConfigMisc.Player_Storm_Deadly_OddsTo1 = this.oldPlayerOdds;
        ConfigMisc.Storm_Deadly_CollideDistance = this.oldCollisionDistance;
        TestEnvironment.resetAll();
    }

    @Test
    public void globalForecastUsesServerProfileAndCooldownAndOnlyMarksChangedSnapshot() {
        RecordingWorld world = RecordingWorld.create();
        world.worldTime = 1000L;
        TestManager manager = addManager(world);
        manager.lastStormFormed = 900L;
        TileEntityWeatherForecast forecast = forecast(world, -1, 64, 1);

        forecast.updateEntity();
        assertTrue(forecast.hasForecastSnapshot);
        assertEquals(TileEntityWeatherForecast.MODE_GLOBAL, forecast.forecastMode);
        assertTrue(forecast.forecastEnabled);
        assertEquals(200L, forecast.forecastRemainingTicks);
        assertEquals(StormObject.STATE_STAGE3, forecast.forecastMaxStage);
        assertEquals(30, forecast.forecastLandOdds);
        assertEquals(0, forecast.forecastOceanOdds);
        assertEquals(1000L, forecast.forecastSampleWorldTick);
        assertEquals(1, world.blockUpdates);
        assertEquals(-1, world.lastUpdatedX);
        assertEquals(64, world.lastUpdatedY);
        assertEquals(1, world.lastUpdatedZ);

        manager.lastStormFormed = 700L;
        forecast.updateEntity();
        assertEquals(0L, forecast.forecastRemainingTicks);
        assertEquals(2, world.blockUpdates);
    }

    @Test
    public void profileAndDimensionCanDisableGlobalForecast() {
        RecordingWorld world = RecordingWorld.create();
        world.worldTime = 1000L;
        addManager(world);
        registerProfile(false, 0, 300, 30, 0);
        TileEntityWeatherForecast forecast = forecast(world, 0, 64, 0);

        forecast.updateEntity();
        assertTrue(forecast.hasForecastSnapshot);
        assertFalse(forecast.forecastEnabled);
        assertEquals(-1L, forecast.forecastRemainingTicks);
        assertEquals(0, forecast.forecastMaxStage);

        registerProfile(true, StormObject.STATE_STAGE3, 300, 30, 0);
        WeatherUtilConfig.listDimensionsStorms.clear();
        world.worldTime = 1200L;
        forecast.updateEntity();
        assertFalse(forecast.forecastEnabled);
    }

    @Test
    public void perPlayerModeDoesNotClaimABlockLevelCooldown() {
        RecordingWorld world = RecordingWorld.create();
        world.worldTime = 1000L;
        addManager(world);
        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = false;
        TileEntityWeatherForecast forecast = forecast(world, 0, 64, 0);

        forecast.updateEntity();
        assertEquals(TileEntityWeatherForecast.MODE_PLAYER, forecast.forecastMode);
        assertTrue(forecast.forecastEnabled);
        assertEquals(-1L, forecast.forecastRemainingTicks);
    }

    @Test
    public void collisionDiagnosticUsesLoadedOpposingStormsWithoutTerrainQueries() {
        RecordingWorld world = RecordingWorld.create();
        world.worldTime = 1000L;
        TestManager manager = addManager(world);
        StormObject warm = addStorm(manager, 1L, StormObject.STATE_HIGHWIND, 8.0D, 0.5F);
        StormObject cold = addStorm(manager, 2L, StormObject.STATE_NORMAL, 16.0D, -0.5F);
        warm.pos = Vec3.createVectorHelper(8.0D, StormObject.static_YPos_layer0, 0.0D);
        cold.pos = Vec3.createVectorHelper(16.0D, StormObject.static_YPos_layer0, 0.0D);
        TileEntityWeatherForecast forecast = forecast(world, 0, 64, 0);

        forecast.updateEntity();
        assertTrue(forecast.forecastCollisionNearby);
        assertEquals(0, world.heightValueQueries);
        assertEquals(0, world.chunkExistsQueries);
    }

    @Test
    public void negativeCoordinatesUsePositiveOffsetForForecastUpdates() {
        RecordingWorld world = RecordingWorld.create();
        world.worldTime = 199L;
        addManager(world);
        TileEntityWeatherForecast forecast = forecast(world, -2, 64, 1);

        forecast.updateEntity();

        assertTrue(forecast.hasForecastSnapshot);
        assertEquals(1, world.blockUpdates);
    }

    private static TestManager addManager(World world) {
        TestManager manager = new TestManager(world);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), manager);
        return manager;
    }

    private static TileEntityWeatherForecast forecast(World world, int x, int y, int z) {
        TileEntityWeatherForecast forecast = new TileEntityWeatherForecast();
        RecordingWorld.setField(forecast, "worldObj", world);
        RecordingWorld.setField(forecast, "xCoord", Integer.valueOf(x));
        RecordingWorld.setField(forecast, "yCoord", Integer.valueOf(y));
        RecordingWorld.setField(forecast, "zCoord", Integer.valueOf(z));
        return forecast;
    }

    private static StormObject addStorm(TestManager manager, long id, int stage, double x, float temperature) {
        StormObject storm = MockStormFactory.create(stage, id, 0);
        storm.levelTemperature = temperature;
        storm.posGround = Vec3.createVectorHelper(x, 64.0D, 0.0D);
        storm.pos = Vec3.createVectorHelper(x, StormObject.static_YPos_layer0, 0.0D);
        manager.addStormObject(storm);
        return storm;
    }

    private static void registerProfile(boolean enabled, int maxStage, int cooldown, int landOdds,
        int oceanOdds) {
        ClimateProfileRegistry.register(ClimateProfile.builder(0, "forecast_test")
            .weatherEnabled(enabled)
            .exactStage(maxStage)
            .deadlyCooldown(cooldown)
            .spawnOdds(landOdds, oceanOdds)
            .build());
    }

    private static class TestManager extends WeatherManagerServer {

        private final World world;

        TestManager(World world) {
            super(0);
            this.world = world;
        }

        @Override
        public World getWorld() {
            return this.world;
        }
    }
}
