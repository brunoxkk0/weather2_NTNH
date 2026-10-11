package weather2.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.ServerTickHandler;
import weather2.api.TornadoApi;
import weather2.api.TornadoHandle;
import weather2.api.TornadoOptions;
import weather2.api.TornadoSnapshot;
import weather2.api.event.TornadoFormEvent;
import weather2.api.event.TornadoOrigin;
import weather2.climate.ClimateEngine;
import weather2.climate.ClimateProfile;
import weather2.climate.ClimateProfileRegistry;
import weather2.config.ConfigMisc;
import weather2.harness.MockBlockFactory;
import weather2.harness.RecordingWorld;
import weather2.player.PlayerData;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

public class TornadoApiTest {

    private HashMap oldManagers;
    private List oldWeatherDimensions;
    private List oldStormDimensions;
    private long oldStormId;
    private Thread oldServerThread;

    @Before
    public void setUp() throws Exception {
        this.oldManagers = ServerTickHandler.lookupDimToWeatherMan;
        this.oldWeatherDimensions = WeatherUtilConfig.listDimensionsWeather;
        this.oldStormDimensions = WeatherUtilConfig.listDimensionsStorms;
        this.oldStormId = StormObject.lastUsedStormID;
        this.oldServerThread = getServerThread();
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
        WeatherUtilConfig.listDimensionsWeather = new ArrayList();
        WeatherUtilConfig.listDimensionsStorms = new ArrayList();
        StormObject.lastUsedStormID = 0L;
        setServerThread(Thread.currentThread());
    }

    @After
    public void tearDown() throws Exception {
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
        WeatherUtilConfig.listDimensionsWeather = this.oldWeatherDimensions;
        WeatherUtilConfig.listDimensionsStorms = this.oldStormDimensions;
        StormObject.lastUsedStormID = this.oldStormId;
        setServerThread(this.oldServerThread);
    }

    @Test
    public void spawnUsesExistingIndicesAndSync() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);

        Optional<TornadoHandle> result = TornadoApi.spawn(world, 8.5D, -3.0D, new TornadoOptions(2, 200, false, true));

        assertTrue(result.isPresent());
        assertEquals(
            0L,
            result.get()
                .getId());
        StormObject storm = (StormObject) manager.lookupStormObjectsByID.get(
            Long.valueOf(
                result.get()
                    .getId()));
        assertNotNull(storm);
        assertTrue(
            manager.getStormObjects()
                .contains(storm));
        assertTrue(
            manager.getStormObjectsByLayer(0)
                .contains(storm));
        assertEquals(1, manager.syncNewCount);
        assertEquals(StormObject.STATE_FORMING + 2, storm.levelCurIntensityStage);
        assertEquals(
            TornadoOrigin.API,
            manager.formEvents.get(0)
                .getOrigin());
        assertTrue(storm.tornadoActive);
        assertTrue(storm.controlledTornado);
        assertEquals(200, storm.tornadoRemainingTicks);
        assertFalse(storm.pullPlayers);
        assertTrue(storm.pullLivingEntities);
    }

    @Test
    public void vetoReturnsEmptyWithoutRegistrationOrSync() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        manager.vetoFormation = true;

        Optional<TornadoHandle> result = TornadoApi.spawn(world, 0.0D, 0.0D, TornadoOptions.visual(1, 20));

        assertFalse(result.isPresent());
        assertTrue(
            manager.getStormObjects()
                .isEmpty());
        assertTrue(manager.lookupStormObjectsByID.isEmpty());
        assertEquals(0, manager.syncNewCount);
        assertEquals(1, manager.formEvents.size());
    }

    @Test
    public void controlledIntensityStaysFixedAndF5IgnoresNaturalCap() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        TornadoHandle handle = TornadoApi.spawn(world, 0.0D, 0.0D, TornadoOptions.visual(5, 20))
            .get();
        StormObject storm = (StormObject) manager.lookupStormObjectsByID.get(Long.valueOf(handle.getId()));

        assertEquals(9, storm.levelCurIntensityStage);
        assertEquals(9, storm.maxIntensityStage);
        storm.stageNext();
        storm.stagePrev();
        assertEquals(9, storm.levelCurIntensityStage);
    }

    @Test
    public void controlledTornadoIgnoresNaturalStormFormation() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        MockBlockFactory.initVanillaBlocks();
        world.defaultBlock = net.minecraft.init.Blocks.stone;
        ClimateProfile oldProfile = ClimateProfileRegistry.get(0);
        String oldProfiles = ConfigMisc.Dimension_Weather_Profiles;
        int oldTickRate = ConfigMisc.Storm_AllTypes_TickRateDelay;
        int oldRainBuildOdds = ConfigMisc.Storm_Rain_WaterBuildUpOddsTo1FromNothing;
        int oldRainOdds = ConfigMisc.Player_Storm_Rain_OddsTo1;
        boolean oldGlobalRate = ConfigMisc.Server_Storm_Deadly_UseGlobalRate;
        boolean hadPlayerData = PlayerData.playerNBT.containsKey("Weather2 API");
        Object oldPlayerData = PlayerData.playerNBT.get("Weather2 API");
        try {
            ConfigMisc.Dimension_Weather_Profiles = "0, 9, false, 72000, 1.0, 1, 0, false";
            ClimateEngine.ensureProfilesLoaded();
            ConfigMisc.Storm_AllTypes_TickRateDelay = 1;
            ConfigMisc.Storm_Rain_WaterBuildUpOddsTo1FromNothing = 1;
            ConfigMisc.Player_Storm_Rain_OddsTo1 = 1;
            ConfigMisc.Server_Storm_Deadly_UseGlobalRate = false;
            PlayerData.playerNBT.put("Weather2 API", new NBTTagCompound());

            TrackingStorm storm = new TrackingStorm(manager);
            storm.ID = 0L;
            storm.levelCurIntensityStage = StormObject.STATE_STAGE5;
            storm.maxIntensityStage = StormObject.STATE_STAGE5;
            storm.controlledTornado = true;
            storm.tornadoActive = true;
            storm.tornadoRemainingTicks = 20;
            storm.userSpawnedFor = "Weather2 API";
            storm.levelWater = 200;
            storm.levelWaterStartRaining = 100;
            storm.setPrecipitating(true);
            storm.currentTopYBlock = 64;
            manager.addStormObject(storm);

            storm.tickProgression();

            assertEquals(0, storm.realStormInitializations);
            assertEquals(StormObject.STATE_STAGE5, storm.levelCurIntensityStage);
            assertTrue(storm.tornadoActive);
            assertEquals(storm, manager.getActiveTornado(storm.ID));
        } finally {
            ConfigMisc.Dimension_Weather_Profiles = oldProfiles;
            ClimateEngine.ensureProfilesLoaded();
            ClimateProfileRegistry.register(oldProfile);
            ConfigMisc.Storm_AllTypes_TickRateDelay = oldTickRate;
            ConfigMisc.Storm_Rain_WaterBuildUpOddsTo1FromNothing = oldRainBuildOdds;
            ConfigMisc.Player_Storm_Rain_OddsTo1 = oldRainOdds;
            ConfigMisc.Server_Storm_Deadly_UseGlobalRate = oldGlobalRate;
            if (hadPlayerData) {
                PlayerData.playerNBT.put("Weather2 API", oldPlayerData);
            } else {
                PlayerData.playerNBT.remove("Weather2 API");
            }
        }
    }

    @Test
    public void snapshotsAreDetachedAndSetPullSyncsImmediately() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        TornadoHandle handle = TornadoApi.spawn(world, 12.0D, 18.0D, TornadoOptions.visual(1, 40))
            .get();
        TornadoSnapshot snapshot = TornadoApi.get(world, handle.getId())
            .get();
        StormObject storm = (StormObject) manager.lookupStormObjectsByID.get(Long.valueOf(handle.getId()));
        storm.pos.xCoord = 999.0D;

        assertTrue(TornadoApi.setPull(world, handle.getId(), true, false));

        assertEquals(12.0D, snapshot.getX(), 0.0D);
        assertFalse(snapshot.isPullPlayers());
        assertTrue(storm.pullPlayers);
        assertFalse(storm.pullLivingEntities);
        assertEquals(1, manager.syncUpdateCount);
        NBTTagCompound synced = manager.lastUpdate;
        assertTrue(synced.getBoolean("softPullPlayers"));
        assertFalse(synced.getBoolean("softPullLivingEntities"));
        assertEquals(
            handle,
            TornadoApi.get(world, handle.getId())
                .get()
                .getHandle());
        List<TornadoSnapshot> snapshots = TornadoApi.list(world);
        assertEquals(1, snapshots.size());
        try {
            snapshots.clear();
            fail("Tornado list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // API collections are detached and read-only.
        }
    }

    @Test
    public void cancelIsDimensionBoundAndOnlySucceedsOnce() {
        RecordingWorld firstWorld = this.addWorld(0);
        RecordingManager first = this.manager(0, firstWorld);
        RecordingWorld secondWorld = this.addWorld(1);
        this.manager(1, secondWorld);
        TornadoHandle handle = TornadoApi.spawn(firstWorld, 0.0D, 0.0D, TornadoOptions.visual(0, 40))
            .get();

        assertFalse(TornadoApi.cancel(secondWorld, handle.getId()));
        assertTrue(
            TornadoApi.get(firstWorld, handle.getId())
                .isPresent());
        assertTrue(TornadoApi.cancel(firstWorld, handle.getId()));
        assertFalse(TornadoApi.cancel(firstWorld, handle.getId()));
        assertFalse(
            TornadoApi.get(firstWorld, handle.getId())
                .isPresent());
        assertEquals(1, first.pendingEnds());
    }

    @Test
    public void handleCannotActOnDuplicateIdInAnotherDimension() {
        RecordingWorld firstWorld = this.addWorld(0);
        this.manager(0, firstWorld);
        RecordingWorld secondWorld = this.addWorld(1);
        RecordingManager second = this.manager(1, secondWorld);
        TornadoHandle handle = TornadoApi.spawn(firstWorld, 0.0D, 0.0D, TornadoOptions.visual(0, 40))
            .get();
        StormObject collision = new StormObject(second);
        collision.ID = handle.getId();
        collision.levelCurIntensityStage = StormObject.STATE_FORMING;
        collision.tornadoActive = true;
        collision.tornadoRemainingTicks = 40;
        collision.pos = net.minecraft.util.Vec3.createVectorHelper(0.0D, 100.0D, 0.0D);
        second.addStormObject(collision);

        assertIllegalArgument(() -> TornadoApi.cancel(secondWorld, handle));
        assertIllegalArgument(() -> TornadoApi.setPull(secondWorld, handle, true, true));
        assertIllegalArgument(() -> TornadoApi.get(secondWorld, handle));

        assertTrue(collision.tornadoActive);
        assertFalse(collision.tornadoEndPending);
        assertFalse(collision.pullPlayers);
    }

    @Test
    public void queriesIncludeFormingCyclones() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        StormObject cyclone = new StormObject(manager);
        cyclone.ID = 0L;
        cyclone.stormType = StormObject.TYPE_WATER;
        cyclone.levelCurIntensityStage = StormObject.STATE_FORMING;
        cyclone.tornadoActive = true;
        cyclone.tornadoRemainingTicks = 60;
        cyclone.pos = net.minecraft.util.Vec3.createVectorHelper(2.0D, 100.0D, 3.0D);
        manager.addStormObject(cyclone);
        StormObject.lastUsedStormID = 1L;

        TornadoSnapshot snapshot = TornadoApi.get(world, 0L)
            .get();

        assertTrue(snapshot.isWaterBased());
        assertEquals(0, snapshot.getIntensity());
        assertEquals(
            1,
            TornadoApi.list(world)
                .size());
        assertTrue(TornadoApi.cancel(world, 0L));
    }

    @Test
    public void rejectsInvalidCoordinatesAndMissingOptionsBeforeEvent() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        assertInvalidSpawn(world, Double.NaN, 0.0D, TornadoOptions.visual(1, 20));
        assertInvalidSpawn(world, Double.POSITIVE_INFINITY, 0.0D, TornadoOptions.visual(1, 20));
        assertInvalidSpawn(world, 30_000_000.0D, 0.0D, TornadoOptions.visual(1, 20));
        assertInvalidSpawn(world, 0.0D, 0.0D, null);
        assertTrue(manager.formEvents.isEmpty());
        assertTrue(
            manager.getStormObjects()
                .isEmpty());
    }

    @Test
    public void requiresLoadedChunkManagerEnabledDimensionAndServerThread() throws Exception {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        world.setChunkLoaded(false);
        assertIllegalState(() -> TornadoApi.spawn(world, 0.0D, 0.0D, TornadoOptions.visual(1, 20)));
        assertTrue(manager.formEvents.isEmpty());
        assertTrue(
            manager.getStormObjects()
                .isEmpty());

        world.setChunkLoaded(true);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread other = new Thread(() -> {
            try {
                TornadoApi.spawn(world, 0.0D, 0.0D, TornadoOptions.visual(1, 20));
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        other.start();
        other.join();
        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(manager.formEvents.isEmpty());
        assertTrue(
            manager.getStormObjects()
                .isEmpty());

        WeatherUtilConfig.listDimensionsWeather.clear();
        assertIllegalState(() -> TornadoApi.spawn(world, 0.0D, 0.0D, TornadoOptions.visual(1, 20)));
        assertTrue(manager.formEvents.isEmpty());
    }

    @Test
    public void rejectsStaleManagerOfWrongType() {
        RecordingWorld world = this.addWorld(0);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), new Object());

        assertIllegalState(() -> TornadoApi.list(world));
    }

    @Test
    public void missingManagerIsRejectedWithoutLoadingDimension() {
        RecordingWorld world = this.addWorld(0);

        assertIllegalState(() -> TornadoApi.spawn(world, 0.0D, 0.0D, TornadoOptions.visual(1, 20)));

        assertTrue(ServerTickHandler.lookupDimToWeatherMan.isEmpty());
    }

    @Test
    public void apiMutationDuringFormationEventIsRejected() {
        RecordingWorld world = this.addWorld(0);
        RecordingManager manager = this.manager(0, world);
        manager.tryReentrantSpawn = true;

        assertTrue(
            TornadoApi.spawn(world, 0.0D, 0.0D, TornadoOptions.visual(1, 20))
                .isPresent());

        assertTrue(manager.reentrantMutationRejected);
        assertEquals(1, manager.formEvents.size());
        assertEquals(1, manager.syncNewCount);
        assertEquals(
            1,
            manager.getStormObjects()
                .size());
    }

    private RecordingWorld addWorld(int dim) {
        RecordingWorld world = RecordingWorld.create();
        RecordingWorld.setField(world.provider, "dimensionId", Integer.valueOf(dim));
        WeatherUtilConfig.listDimensionsWeather.add(Integer.valueOf(dim));
        WeatherUtilConfig.listDimensionsStorms.add(Integer.valueOf(dim));
        return world;
    }

    private RecordingManager manager(int dim, RecordingWorld world) {
        RecordingManager manager = new RecordingManager(dim, world);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(dim), manager);
        return manager;
    }

    private static void assertInvalidSpawn(WorldServer world, double x, double z, TornadoOptions options) {
        try {
            TornadoApi.spawn(world, x, z, options);
            fail("Expected invalid tornado request to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected validation before storm creation.
        }
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
            fail("Expected unavailable world state to be rejected");
        } catch (IllegalStateException expected) {
            // Expected validation before storm creation.
        }
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected a handle from another dimension to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected dimension validation before acting on a storm.
        }
    }

    private static Thread getServerThread() throws Exception {
        Field field = ServerTickHandler.class.getDeclaredField("serverThread");
        field.setAccessible(true);
        return (Thread) field.get(null);
    }

    private static void setServerThread(Thread thread) throws Exception {
        Field field = ServerTickHandler.class.getDeclaredField("serverThread");
        field.setAccessible(true);
        field.set(null, thread);
    }

    private static final class RecordingManager extends WeatherManagerServer {

        private final WorldServer world;
        private final List<TornadoFormEvent> formEvents = new ArrayList<TornadoFormEvent>();
        private boolean vetoFormation;
        private boolean tryReentrantSpawn;
        private boolean reentrantMutationRejected;
        private int syncNewCount;
        private int syncUpdateCount;
        private NBTTagCompound lastUpdate;

        private RecordingManager(int dimension, WorldServer world) {
            super(dimension);
            this.world = world;
        }

        @Override
        public net.minecraft.world.World getWorld() {
            return this.world;
        }

        @Override
        public void syncStormNew(StormObject storm) {
            ++this.syncNewCount;
        }

        @Override
        protected void syncStormUpdate(java.util.Set stormObjectsData) {
            ++this.syncUpdateCount;
            if (!stormObjectsData.isEmpty()) {
                this.lastUpdate = (NBTTagCompound) stormObjectsData.iterator()
                    .next();
            }
        }

        @Override
        protected void postTornadoForm(TornadoFormEvent event) {
            this.formEvents.add(event);
            if (this.tryReentrantSpawn) {
                try {
                    TornadoApi.spawn((WorldServer) this.world, 1.0D, 1.0D, TornadoOptions.visual(0, 20));
                } catch (IllegalStateException expected) {
                    this.reentrantMutationRejected = true;
                }
            }
            if (this.vetoFormation) {
                event.setCanceled(true);
            }
        }

        private int pendingEnds() {
            int count = 0;
            for (Object object : this.getStormObjects()) {
                if (((StormObject) object).tornadoEndPending) ++count;
            }
            return count;
        }
    }

    private static final class TrackingStorm extends StormObject {

        private int realStormInitializations;

        private TrackingStorm(RecordingManager manager) {
            super(manager);
        }

        @Override
        public void initRealStorm(net.minecraft.entity.player.EntityPlayer player, StormObject stormToAbsorb) {
            ++this.realStormInitializations;
        }
    }
}
