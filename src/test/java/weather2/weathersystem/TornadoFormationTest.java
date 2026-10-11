package weather2.weathersystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import weather2.api.event.TornadoFormEvent;
import weather2.api.event.TornadoOrigin;
import weather2.block.TileEntityWeatherMachine;
import weather2.harness.RecordingWorld;
import weather2.weathersystem.storm.StormObject;

public class TornadoFormationTest {

    private HashMap oldManagers;
    private long oldStormId;

    @Before
    public void setUp() {
        this.oldManagers = ServerTickHandler.lookupDimToWeatherMan;
        this.oldStormId = StormObject.lastUsedStormID;
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
    }

    @After
    public void tearDown() {
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
        StormObject.lastUsedStormID = this.oldStormId;
    }

    @Test
    public void naturalPromotionFiresOnce() {
        FormationManager manager = new FormationManager(0);
        StormObject storm = manager.addStorm(20L, StormObject.STATE_HAIL);

        storm.stageNext();
        storm.stageNext();

        assertEquals(StormObject.STATE_STAGE1, storm.levelCurIntensityStage);
        assertTrue(storm.tornadoActive);
        assertEquals(1, manager.formEvents.size());
        assertEquals(
            TornadoOrigin.NATURAL,
            manager.formEvents.get(0)
                .getOrigin());
    }

    @Test
    public void waterFormationSnapshotKeepsCycloneType() {
        FormationManager manager = new FormationManager(0);
        StormObject storm = manager.addStorm(26L, StormObject.STATE_FORMING);
        storm.stormType = StormObject.TYPE_WATER;

        assertTrue(manager.tryActivateTornado(storm, TornadoOrigin.NATURAL));
        assertEquals(1, manager.formEvents.size());
        assertTrue(
            manager.formEvents.get(0)
                .getSnapshot()
                .isWaterBased());
    }

    @Test
    public void ordinaryCloudDoesNotPostFormationEvent() {
        FormationManager manager = new FormationManager(0);
        StormObject cloud = manager.addStorm(25L, StormObject.STATE_THUNDER);

        assertFalse(manager.tryActivateTornado(cloud, TornadoOrigin.NATURAL));

        assertFalse(cloud.tornadoActive);
        assertTrue(manager.formEvents.isEmpty());
    }

    @Test
    public void vetoPreservesRainAndPreventsPull() {
        FormationManager manager = new FormationManager(0);
        manager.vetoFormation = true;
        StormObject storm = manager.addStorm(21L, StormObject.STATE_HAIL);
        storm.attrib_precipitation = true;

        storm.stageNext();

        assertEquals(StormObject.STATE_HAIL, storm.levelCurIntensityStage);
        assertTrue(storm.attrib_precipitation);
        assertTrue(storm.hasStormPeaked);
        assertFalse(storm.tornadoActive);
        assertFalse(storm.shouldPull(null));
        assertEquals(1, manager.formEvents.size());
    }

    @Test
    public void reloadDoesNotRefireFormation() {
        FormationManager source = new FormationManager(0);
        StormObject storm = source.addStorm(22L, StormObject.STATE_FORMING);
        storm.tornadoActive = true;
        storm.tornadoRemainingTicks = 1000;
        NBTTagCompound savedStorm = storm.writeToNBT();
        NBTTagCompound save = new NBTTagCompound();
        save.setLong("lastUsedIDStorm", 23L);
        NBTTagCompound storms = new NBTTagCompound();
        storms.setTag("storm_22", savedStorm);
        save.setTag("stormData", storms);
        FormationManager restored = new FormationManager(0);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), restored);

        restored.readFromNBT(save);

        assertTrue(
            ((StormObject) restored.getStormObjects()
                .get(0)).tornadoActive);
        assertTrue(restored.formEvents.isEmpty());
    }

    @Test
    public void machineCannotBypassFormationVeto() {
        RecordingWorld world = RecordingWorld.create();
        FormationManager manager = new FormationManager(0, world);
        manager.vetoFormation = true;
        StormObject storm = manager.addStorm(23L, StormObject.STATE_HAIL);
        TileEntityWeatherMachine machine = new TileEntityWeatherMachine();
        machine.lastTickStormObject = storm;
        machine.weatherType = 5;
        RecordingWorld.setField(machine, "worldObj", world);

        machine.updateEntity();
        machine.updateEntity();

        assertEquals(StormObject.STATE_HAIL, storm.levelCurIntensityStage);
        assertFalse(storm.tornadoActive);
        assertEquals(1, manager.formEvents.size());
        assertEquals(
            TornadoOrigin.MACHINE,
            manager.formEvents.get(0)
                .getOrigin());
    }

    @Test
    public void reentrantMutationIsRejected() {
        FormationManager manager = new FormationManager(0);
        StormObject storm = manager.addStorm(24L, StormObject.STATE_FORMING);
        manager.reenterStorm = storm;

        assertTrue(manager.tryActivateTornado(storm, TornadoOrigin.API));

        assertFalse(manager.reentrantResult);
        assertTrue(storm.tornadoActive);
        assertEquals(1, manager.formEvents.size());
    }

    private static final class FormationManager extends WeatherManagerServer {

        private final RecordingWorld world;
        private final List<TornadoFormEvent> formEvents = new ArrayList<TornadoFormEvent>();
        private boolean vetoFormation;
        private boolean reentrantResult = true;
        private StormObject reenterStorm;

        private FormationManager(int dim) {
            this(dim, RecordingWorld.create());
        }

        private FormationManager(int dim, RecordingWorld world) {
            super(dim);
            this.world = world;
            ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(dim), this);
        }

        @Override
        public net.minecraft.world.World getWorld() {
            return this.world;
        }

        @Override
        public void syncStormNew(StormObject storm) {}

        @Override
        protected void postTornadoForm(TornadoFormEvent event) {
            this.formEvents.add(event);
            if (this.reenterStorm != null) {
                this.reentrantResult = this.tryActivateTornado(this.reenterStorm, TornadoOrigin.API);
            }
            if (this.vetoFormation) {
                event.setCanceled(true);
            }
        }

        private StormObject addStorm(long id, int stage) {
            StormObject storm = new StormObject(this);
            storm.ID = id;
            storm.levelCurIntensityStage = stage;
            storm.pos = Vec3.createVectorHelper(0.0D, 200.0D, 0.0D);
            storm.posGround = Vec3.createVectorHelper(0.0D, 64.0D, 0.0D);
            storm.posBaseFormationPos = storm.posGround;
            storm.maxHeight = 60;
            this.addStormObject(storm);
            return storm;
        }
    }
}
