package weather2.weathersystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.ServerTickHandler;
import weather2.api.event.TornadoEndReason;
import weather2.api.event.TornadoEndedEvent;
import weather2.config.ConfigMisc;
import weather2.harness.RecordingWorld;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.storm.StormObject;

public class TornadoLifecycleTest {

    private int oldWeatherMode;
    private boolean oldPreventThunder;
    private int oldStormCheckRate;
    private long oldStormId;
    private HashMap oldManagers;
    private List oldCloudDimensions;

    @Before
    public void setUp() {
        this.oldWeatherMode = ConfigMisc.lockServerWeatherMode;
        this.oldPreventThunder = ConfigMisc.preventServerThunderstorms;
        this.oldStormCheckRate = ConfigMisc.tickerRateSyncStormSpawnOrRemoveChecks;
        this.oldStormId = StormObject.lastUsedStormID;
        this.oldManagers = ServerTickHandler.lookupDimToWeatherMan;
        this.oldCloudDimensions = WeatherUtilConfig.listDimensionsClouds;
        ConfigMisc.lockServerWeatherMode = -1;
        ConfigMisc.preventServerThunderstorms = false;
        ConfigMisc.tickerRateSyncStormSpawnOrRemoveChecks = 20;
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
        WeatherUtilConfig.listDimensionsClouds = new ArrayList();
        WeatherUtilConfig.listDimensionsClouds.add(Integer.valueOf(0));
    }

    @After
    public void tearDown() {
        ConfigMisc.lockServerWeatherMode = this.oldWeatherMode;
        ConfigMisc.preventServerThunderstorms = this.oldPreventThunder;
        ConfigMisc.tickerRateSyncStormSpawnOrRemoveChecks = this.oldStormCheckRate;
        StormObject.lastUsedStormID = this.oldStormId;
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
        WeatherUtilConfig.listDimensionsClouds = this.oldCloudDimensions;
    }

    @Test
    public void controlledTornadoSurvivesPlayerDistanceSweep() {
        ConfigMisc.tickerRateSyncStormSpawnOrRemoveChecks = 1;
        LifecycleManager manager = new LifecycleManager(0);
        StormObject controlled = manager.addTornado(20L, 100);
        controlled.controlledTornado = true;
        StormObject natural = manager.addTornado(21L, 100);

        manager.tick();

        assertTrue(controlled.tornadoActive);
        assertEquals(99, controlled.tornadoRemainingTicks);
        assertTrue(manager.lookupStormObjectsByID.containsKey(Long.valueOf(controlled.ID)));
        assertFalse(natural.tornadoActive);
        assertFalse(manager.lookupStormObjectsByID.containsKey(Long.valueOf(natural.ID)));
        assertEquals(1, manager.removeSyncCount);
    }

    @Test
    public void cancelIsImmediatelyInvisible() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(10L, 5);

        assertTrue(manager.endTornado(storm.ID, TornadoEndReason.CANCELLED));

        assertFalse(storm.tornadoActive);
        assertNull(manager.getClosestStormAny(Vec3.createVectorHelper(0.0D, 0.0D, 0.0D), 100.0D));
        assertTrue(
            manager.getStormsAround(Vec3.createVectorHelper(0.0D, 0.0D, 0.0D), 100.0D)
                .isEmpty());
        manager.tick();
        assertFalse(manager.lookupStormObjectsByID.containsKey(Long.valueOf(storm.ID)));
    }

    @Test
    public void pendingCancellationDoesNotSurviveSaveReload() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(30L, 100);
        assertTrue(manager.endTornado(storm.ID, TornadoEndReason.CANCELLED));

        NBTTagCompound save = manager.saveDataToNBT();
        assertFalse(
            save.getCompoundTag("stormData")
                .hasKey("storm_" + storm.ID));

        LifecycleManager restored = new LifecycleManager(0);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), restored);
        restored.readFromNBT(save);
        assertFalse(restored.lookupStormObjectsByID.containsKey(Long.valueOf(storm.ID)));
    }

    @Test
    public void adjacentExpirationsDoNotSkipStorms() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject first = manager.addTornado(11L, 1);
        StormObject second = manager.addTornado(12L, 1);

        manager.tick();

        assertFalse(manager.lookupStormObjectsByID.containsKey(Long.valueOf(first.ID)));
        assertFalse(manager.lookupStormObjectsByID.containsKey(Long.valueOf(second.ID)));
        assertEquals(2, manager.removeSyncCount);
        assertEquals(2, manager.endedEvents.size());
        assertEquals(
            TornadoEndReason.EXPIRED,
            manager.endedEvents.get(0)
                .getReason());
        assertEquals(0, ((CountingStorm) first).ticks);
        assertEquals(0, ((CountingStorm) second).ticks);
    }

    @Test
    public void removalSyncAndEventHappenOnce() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(13L, 100);

        assertTrue(manager.endTornado(storm.ID, TornadoEndReason.CANCELLED));
        assertFalse(manager.endTornado(storm.ID, TornadoEndReason.CANCELLED));
        manager.tick();
        manager.tick();

        assertEquals(1, manager.removeSyncCount);
        assertEquals(1, manager.endedEvents.size());
        assertEquals(1, ((CountingStorm) storm).cleanupCount);
        TornadoEndedEvent event = manager.endedEvents.get(0);
        assertEquals(TornadoEndReason.CANCELLED, event.getReason());
        assertEquals(
            13L,
            event.getSnapshot()
                .getId());
    }

    @Test
    public void legacyDirectRemovalEmitsTornadoEndOnce() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(18L, 100);

        manager.removeStormObject(storm.ID);
        manager.tick();

        assertFalse(manager.lookupStormObjectsByID.containsKey(Long.valueOf(storm.ID)));
        assertEquals(1, manager.removeSyncCount);
        assertEquals(1, manager.endedEvents.size());
        assertEquals(
            TornadoEndReason.REMOVED,
            manager.endedEvents.get(0)
                .getReason());
        assertEquals(1, ((CountingStorm) storm).cleanupCount);
    }

    @Test
    public void deadTornadoEmitsTornadoEndOnce() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(19L, 100);
        storm.setDead();

        manager.tick();

        assertFalse(manager.lookupStormObjectsByID.containsKey(Long.valueOf(storm.ID)));
        assertEquals(1, manager.removeSyncCount);
        assertEquals(1, manager.endedEvents.size());
        assertEquals(
            TornadoEndReason.REMOVED,
            manager.endedEvents.get(0)
                .getReason());
        assertEquals(1, ((CountingStorm) storm).cleanupCount);
    }

    @Test
    public void cancelledStormDoesNotRunAnotherPhysicsTick() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(14L, 100);
        manager.endTornado(storm.ID, TornadoEndReason.CANCELLED);

        manager.tick();

        assertEquals(0, ((CountingStorm) storm).ticks);
    }

    @Test
    public void naturalEndKeepsCloud() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(15L, 100);

        storm.stagePrev();
        manager.tick();

        assertEquals(StormObject.STATE_HAIL, storm.levelCurIntensityStage);
        assertFalse(storm.tornadoActive);
        assertTrue(manager.lookupStormObjectsByID.containsKey(Long.valueOf(storm.ID)));
        assertEquals(0, manager.removeSyncCount);
        assertEquals(
            TornadoEndReason.NATURAL,
            manager.endedEvents.get(0)
                .getReason());
        assertEquals(
            0,
            manager.endedEvents.get(0)
                .getSnapshot()
                .getIntensity());
    }

    @Test
    public void unloadedDimensionDoesNotConsumeDuration() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(16L, 100);
        manager.world = null;

        manager.tick();

        assertTrue(storm.tornadoActive);
        assertEquals(100, storm.tornadoRemainingTicks);
        assertTrue(manager.endedEvents.isEmpty());
    }

    @Test
    public void resetCleansStormOnlyOnce() {
        LifecycleManager manager = new LifecycleManager(0);
        StormObject storm = manager.addTornado(17L, 100);

        manager.reset();

        assertTrue(
            manager.getStormObjects()
                .isEmpty());
        assertEquals(1, ((CountingStorm) storm).cleanupCount);
    }

    @Test
    public void restoredIdsCannotCollideAcrossDimensions() {
        RecordingWorld world = RecordingWorld.create();
        LifecycleManager first = new LifecycleManager(0, world);
        LifecycleManager second = new LifecycleManager(1, world);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), first);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(1), second);
        StormObject.lastUsedStormID = 0L;
        NBTTagCompound firstSave = savedManager(3L, 8L);
        NBTTagCompound secondSave = savedManager(5L, -1L);

        first.readFromNBT(firstSave);
        second.readFromNBT(secondSave);
        StormObject next = new StormObject(first);
        next.initFirstTime();

        assertEquals(9L, next.ID);
        assertNotNull(first.lookupStormObjectsByID.get(Long.valueOf(8L)));
    }

    private static NBTTagCompound savedManager(long nextId, long activeId) {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("lastUsedIDStorm", nextId);
        NBTTagCompound storms = new NBTTagCompound();
        if (activeId >= 0L) {
            NBTTagCompound storm = new NBTTagCompound();
            storm.setLong("ID", activeId);
            storm.setInteger("posX", 0);
            storm.setInteger("posY", 200);
            storm.setInteger("posZ", 0);
            storm.setDouble("vecX", 0.0D);
            storm.setDouble("vecY", 0.0D);
            storm.setDouble("vecZ", 0.0D);
            storms.setTag("storm_" + activeId, storm);
        }
        data.setTag("stormData", storms);
        return data;
    }

    private static final class LifecycleManager extends WeatherManagerServer {

        private RecordingWorld world;
        private final List<TornadoEndedEvent> endedEvents = new ArrayList<TornadoEndedEvent>();
        private int removeSyncCount;

        private LifecycleManager(int dim) {
            this(dim, RecordingWorld.create());
        }

        private LifecycleManager(int dim, RecordingWorld world) {
            super(dim);
            this.world = world;
            this.windMan.chanceOfWindGustEvent = 0.0F;
            this.world.playerEntities = new ArrayList();
        }

        @Override
        public net.minecraft.world.World getWorld() {
            return this.world;
        }

        @Override
        public void syncStormRemove(StormObject storm) {
            ++this.removeSyncCount;
        }

        @Override
        protected void syncStormUpdate(java.util.Set stormObjectsData) {}

        @Override
        protected void postTornadoEnded(TornadoEndedEvent event) {
            this.endedEvents.add(event);
        }

        @Override
        public void syncStormNew(StormObject storm) {}

        private StormObject addTornado(long id, int remaining) {
            CountingStorm storm = new CountingStorm(this);
            storm.ID = id;
            storm.levelCurIntensityStage = StormObject.STATE_FORMING;
            storm.tornadoActive = true;
            storm.tornadoRemainingTicks = remaining;
            storm.pos = Vec3.createVectorHelper(0.0D, 200.0D, 0.0D);
            storm.posGround = Vec3.createVectorHelper(0.0D, 64.0D, 0.0D);
            storm.posBaseFormationPos = storm.posGround;
            storm.maxHeight = 60;
            this.addStormObject(storm);
            return storm;
        }
    }

    private static final class CountingStorm extends StormObject {

        private int ticks;
        private int cleanupCount;

        private CountingStorm(WeatherManagerBase manager) {
            super(manager);
        }

        @Override
        public void tick() {
            ++this.ticks;
        }

        @Override
        public void cleanup() {
            ++this.cleanupCount;
            super.cleanup();
        }

        @Override
        public void setDead() {
            this.isDead = true;
            this.cleanup();
        }
    }

}
