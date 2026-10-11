package weather2.unit;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.ClientTickHandler;
import weather2.ServerTickHandler;
import weather2.api.WindReader;
import weather2.config.ConfigMisc;
import weather2.harness.MockStormFactory;
import weather2.harness.RecordingWorld;
import weather2.weathersystem.WeatherManagerBase;
import weather2.weathersystem.WeatherManagerClient;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.wind.WindManager;

public class WindPositionTest {

    private WeatherManagerClient manager;
    private WeatherManagerClient oldClientManager;
    private HashMap oldManagers;
    private boolean oldWindOn;

    @Before
    public void setUp() {
        this.oldClientManager = ClientTickHandler.weatherManager;
        this.oldManagers = ServerTickHandler.lookupDimToWeatherMan;
        this.oldWindOn = ConfigMisc.Misc_windOn;
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
        ConfigMisc.Misc_windOn = true;
        RecordingWorld world = RecordingWorld.create();
        world.isRemote = true;
        this.manager = new WeatherManagerClient(world);
        ClientTickHandler.weatherManager = this.manager;
    }

    @After
    public void tearDown() {
        ClientTickHandler.weatherManager = this.oldClientManager;
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
        ConfigMisc.Misc_windOn = this.oldWindOn;
    }

    @Test
    public void heightMultiplierClampsAtOneAndOnePointFive() {
        assertEquals(1.0F, WindManager.heightWindMultiplier(64.0D, 64.0D), 0.0F);
        assertEquals(1.25F, WindManager.heightWindMultiplier(96.0D, 64.0D), 0.0F);
        assertEquals(1.5F, WindManager.heightWindMultiplier(128.0D, 64.0D), 0.0F);
        assertEquals(1.0F, WindManager.heightWindMultiplier(32.0D, 64.0D), 0.0F);
    }

    @Test
    public void eventStrengthFadesByHorizontalDistanceAndAnglePointsTowardStorm() {
        StormObject storm = addStorm(this.manager, 4L, 0.0D, 0.0D);
        assertEquals(2.0F, localSpeed(this.manager.getWorld(), 0.0D, 0.0D, WindReader.WindType.EVENT), 0.0F);
        assertEquals(1.0F, localSpeed(this.manager.getWorld(), 128.0D, 0.0D, WindReader.WindType.EVENT), 0.0F);
        assertEquals(0.0F, localSpeed(this.manager.getWorld(), 256.0D, 0.0D, WindReader.WindType.EVENT), 0.0F);

        storm.posGround = Vec3.createVectorHelper(10.0D, 64.0D, 0.0D);
        assertEquals(-90.0F, WindReader.getLocalWindAngle(this.manager.getWorld(), POS, WindReader.WindType.EVENT),
            0.001F);
        storm.posGround = Vec3.createVectorHelper(0.0D, 64.0D, -10.0D);
        assertEquals(-180.0F, WindReader.getLocalWindAngle(this.manager.getWorld(), POS, WindReader.WindType.EVENT),
            0.001F);
    }

    @Test
    public void dominantUsesEventThenActiveGustThenHeightAdjustedGlobalWhileCloudStaysGlobal() {
        World world = this.manager.getWorld();
        this.manager.windMan.windSpeedGlobal = 0.4F;
        this.manager.windMan.windAngleGlobal = 25.0F;
        Vec3 highPos = Vec3.createVectorHelper(0.5D, 128.0D, 0.5D);
        assertEquals(0.6F, WindReader.getLocalWindSpeed(world, highPos, WindReader.WindType.DOMINANT), 0.001F);
        assertEquals(0.4F, WindReader.getLocalWindSpeed(world, highPos, WindReader.WindType.CLOUD), 0.001F);
        assertEquals(25.0F, WindReader.getLocalWindAngle(world, highPos, WindReader.WindType.CLOUD), 0.0F);

        this.manager.windMan.windSpeedGust = 0.8F;
        this.manager.windMan.windTimeGust = 0;
        assertEquals(0.0F, WindReader.getLocalWindSpeed(world, POS, WindReader.WindType.GUST), 0.0F);
        this.manager.windMan.windTimeGust = 20;
        assertEquals(0.8F, WindReader.getLocalWindSpeed(world, POS, WindReader.WindType.DOMINANT), 0.001F);

        addStorm(this.manager, 8L, 0.0D, 0.0D);
        assertEquals(2.0F, WindReader.getLocalWindSpeed(world, POS, WindReader.WindType.DOMINANT), 0.0F);
    }

    @Test
    public void localSpeedUsesFiveLoadedHeightColumnsAndIgnoresDeadOrPendingStorms() {
        RecordingWorld world = (RecordingWorld) this.manager.getWorld();
        this.manager.windMan.windSpeedGlobal = 0.4F;
        assertEquals(
            0.5F,
            WindReader.getLocalWindSpeed(
                world,
                Vec3.createVectorHelper(0.5D, 96.0D, 0.5D),
                WindReader.WindType.DOMINANT),
            0.001F);
        assertEquals(5, world.chunkExistsQueries);
        assertEquals(5, world.heightValueQueries);

        StormObject storm = addStorm(this.manager, 9L, 0.5D, 0.5D);
        storm.isDead = true;
        assertEquals(0.0F, WindReader.getLocalWindSpeed(world, POS, WindReader.WindType.EVENT), 0.0F);
        storm.isDead = false;
        storm.tornadoEndPending = true;
        assertEquals(0.0F, WindReader.getLocalWindSpeed(world, POS, WindReader.WindType.EVENT), 0.0F);
    }

    @Test
    public void tiesUseLowerStormIdAndRemoteInstrumentUsesItsOwnPosition() {
        addStorm(this.manager, 30L, 10.0D, 0.0D);
        addStorm(this.manager, 10L, -10.0D, 0.0D);
        assertEquals(90.0F, WindReader.getLocalWindAngle(this.manager.getWorld(), POS, WindReader.WindType.EVENT),
            0.001F);

        this.manager.getStormObjects().clear();
        this.manager.windMan.windSpeedGlobal = 0.35F;
        addStorm(this.manager, 2L, 0.0D, 0.0D);
        assertEquals(
            0.35F,
            localSpeed(this.manager.getWorld(), 300.0D, 0.0D, WindReader.WindType.DOMINANT),
            0.001F);
    }

    @Test
    public void missingAdjacentChunkUsesBaseWindWithoutReadingHeights() {
        RecordingWorld world = (RecordingWorld) this.manager.getWorld();
        this.manager.windMan.windSpeedGlobal = 0.35F;
        world.setChunkLoadedAt(1, 0, false);

        assertEquals(
            0.35F,
            WindReader.getLocalWindSpeed(
                world,
                Vec3.createVectorHelper(15.5D, 128.0D, 0.5D),
                WindReader.WindType.DOMINANT),
            0.001F);
        assertEquals(0, world.heightValueQueries);
        assertEquals(0, world.heightQueriesInUnloadedChunks);
    }

    @Test
    public void localQueriesRejectMissingManagerNonFinitePositionAndDisabledWind() {
        World world = this.manager.getWorld();
        ClientTickHandler.weatherManager = null;
        assertEquals(0.0F, WindReader.getLocalWindSpeed(world, POS, WindReader.WindType.DOMINANT), 0.0F);
        ClientTickHandler.weatherManager = this.manager;

        this.manager.windMan.windSpeedGlobal = 0.5F;
        assertEquals(
            0.0F,
            WindReader.getLocalWindSpeed(
                world,
                Vec3.createVectorHelper(Double.NaN, 64.0D, 0.0D),
                WindReader.WindType.DOMINANT),
            0.0F);
        ConfigMisc.Misc_windOn = false;
        assertEquals(0.0F, WindReader.getLocalWindSpeed(world, POS, WindReader.WindType.DOMINANT), 0.0F);
        assertEquals(0.0F, WindReader.getLocalWindAngle(world, POS, WindReader.WindType.DOMINANT), 0.0F);
    }

    @Test
    public void serverAndClientUseTheSameLocalStormCalculation() {
        RecordingWorld serverWorld = RecordingWorld.create();
        TestServerManager serverManager = new TestServerManager(serverWorld);
        serverManager.windMan.windSpeedGlobal = 0.4F;
        addStorm(serverManager, 5L, 30.0D, 40.0D);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), serverManager);
        addStorm(this.manager, 5L, 30.0D, 40.0D);

        float clientSpeed = WindReader.getLocalWindSpeed(
            this.manager.getWorld(),
            Vec3.createVectorHelper(0.0D, 64.0D, 0.0D),
            WindReader.WindType.EVENT);
        float serverSpeed = WindReader.getLocalWindSpeed(
            serverWorld,
            Vec3.createVectorHelper(0.0D, 64.0D, 0.0D),
            WindReader.WindType.EVENT);
        float clientAngle = WindReader.getLocalWindAngle(
            this.manager.getWorld(),
            Vec3.createVectorHelper(0.0D, 64.0D, 0.0D),
            WindReader.WindType.EVENT);
        float serverAngle = WindReader.getLocalWindAngle(
            serverWorld,
            Vec3.createVectorHelper(0.0D, 64.0D, 0.0D),
            WindReader.WindType.EVENT);

        assertEquals(clientSpeed, serverSpeed, 0.0F);
        assertEquals(clientAngle, serverAngle, 0.0F);
    }

    private static final Vec3 POS = Vec3.createVectorHelper(0.0D, 64.0D, 0.0D);

    private static float localSpeed(World world, double x, double z, WindReader.WindType type) {
        return WindReader.getLocalWindSpeed(world, Vec3.createVectorHelper(x, 64.0D, z), type);
    }

    private static StormObject addStorm(WeatherManagerBase manager, long id, double x, double z) {
        StormObject storm = MockStormFactory.create(StormObject.STATE_HIGHWIND, id, 0);
        storm.posGround = Vec3.createVectorHelper(x, 64.0D, z);
        manager.addStormObject(storm);
        return storm;
    }

    private static class TestServerManager extends WeatherManagerBase {

        private final World world;

        TestServerManager(World world) {
            super(world.provider.dimensionId);
            this.world = world;
        }

        @Override
        public World getWorld() {
            return this.world;
        }
    }
}
