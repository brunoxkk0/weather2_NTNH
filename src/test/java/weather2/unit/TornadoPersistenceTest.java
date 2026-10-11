package weather2.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.harness.MockStormFactory;
import weather2.harness.RecordingWorld;
import weather2.harness.TestEnvironment;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.TornadoHelper;

public class TornadoPersistenceTest {

    @Before
    public void setUp() {
        TestEnvironment.setupVanilla();
    }

    @After
    public void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    public void legacySaveDefaultsAreSafe() {
        NBTTagCompound legacy = legacyStorm(StormObject.STATE_FORMING);
        StormObject restored = MockStormFactory.create(StormObject.STATE_NORMAL);

        restored.readFromNBT(legacy);

        assertFalse(restored.pullPlayers);
        assertFalse(restored.pullLivingEntities);
        assertFalse(restored.controlledTornado);
        assertTrue(restored.tornadoActive);
        assertEquals(2400, restored.tornadoRemainingTicks);
    }

    @Test
    public void partialPacketPreservesPullOptions() {
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.pullPlayers = true;
        storm.pullLivingEntities = true;
        storm.nbtSyncForClient();
        NBTTagCompound packet = new NBTTagCompound();
        packet.setInteger("levelWater", 12);

        storm.nbtSyncFromServer(packet);

        assertTrue(storm.pullPlayers);
        assertTrue(storm.pullLivingEntities);
    }

    @Test
    public void explicitFalseOverridesTrue() {
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.pullPlayers = true;
        storm.nbtSyncForClient();
        NBTTagCompound packet = new NBTTagCompound();
        packet.setBoolean("softPullPlayers", false);

        storm.nbtSyncFromServer(packet);

        assertFalse(storm.pullPlayers);
    }

    @Test
    public void incrementalSyncDoesNotSendEveryDurationTick() {
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.tornadoActive = true;
        storm.tornadoRemainingTicks = 80;
        assertTrue(
            storm.nbtSyncForClient(true)
                .hasKey("softRemainingTicks"));

        --storm.tornadoRemainingTicks;
        ++storm.size;
        NBTTagCompound update = storm.nbtSyncForClient();

        assertTrue(update.hasKey("ID"));
        assertEquals(storm.ID, update.getLong("ID"));
        assertFalse(update.hasKey("softRemainingTicks"));
    }

    @Test
    public void fullSyncRestoresControlledTornadoForLateJoin() {
        StormObject source = MockStormFactory.create(StormObject.STATE_STAGE2, 84L);
        source.tornadoActive = true;
        source.controlledTornado = true;
        source.pullPlayers = false;
        source.pullLivingEntities = true;
        source.tornadoRemainingTicks = 1234;

        StormObject joined = MockStormFactory.create(StormObject.STATE_NORMAL);
        joined.nbtSyncFromServer(source.nbtSyncForClient(true));

        assertEquals(84L, joined.ID);
        assertEquals(StormObject.STATE_STAGE2, joined.levelCurIntensityStage);
        assertTrue(joined.tornadoActive);
        assertTrue(joined.controlledTornado);
        assertEquals(1234, joined.tornadoRemainingTicks);
        assertFalse(joined.pullPlayers);
        assertTrue(joined.pullLivingEntities);
    }

    @Test
    public void roundTripPreservesControlledDuration() {
        StormObject storm = MockStormFactory.create(StormObject.STATE_STAGE2);
        storm.pullPlayers = true;
        storm.pullLivingEntities = false;
        storm.controlledTornado = true;
        storm.tornadoActive = true;
        storm.tornadoRemainingTicks = 1379;

        NBTTagCompound saved = storm.writeToNBT();
        StormObject restored = MockStormFactory.create(StormObject.STATE_NORMAL);
        restored.readFromNBT(saved);

        assertTrue(restored.pullPlayers);
        assertFalse(restored.pullLivingEntities);
        assertTrue(restored.controlledTornado);
        assertTrue(restored.tornadoActive);
        assertEquals(1379, restored.tornadoRemainingTicks);
    }

    @Test
    public void invalidPersistedDurationExpiresBeforePhysics() {
        NBTTagCompound saved = legacyStorm(StormObject.STATE_FORMING);
        saved.setInteger("softVersion", 1);
        saved.setBoolean("softPullPlayers", true);
        saved.setBoolean("softPullLivingEntities", true);
        saved.setBoolean("softControlledTornado", true);
        saved.setBoolean("softTornadoActive", true);
        saved.setInteger("softRemainingTicks", 0);
        StormObject restored = MockStormFactory.create(StormObject.STATE_NORMAL);

        restored.readFromNBT(saved);

        assertFalse(restored.tornadoActive);
        assertEquals(0, restored.tornadoRemainingTicks);
        final int[] physicsCalls = { 0 };
        restored.tornadoHelper = new TornadoHelper(restored) {

            @Override
            public boolean forceRotate(World world) {
                ++physicsCalls[0];
                return true;
            }
        };
        restored.tornadoHelper.tick(RecordingWorld.create());
        assertEquals(0, physicsCalls[0]);
    }

    private static NBTTagCompound legacyStorm(int stage) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("ID", 17L);
        nbt.setInteger("levelCurIntensityStage", stage);
        nbt.setInteger("posX", 100);
        nbt.setInteger("posY", 200);
        nbt.setInteger("posZ", 100);
        nbt.setDouble("vecX", 0.0D);
        nbt.setDouble("vecY", 0.0D);
        nbt.setDouble("vecZ", 0.0D);
        return nbt;
    }
}
