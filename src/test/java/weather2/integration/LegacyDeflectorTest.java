package weather2.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.ServerTickHandler;
import weather2.block.BlockWeatherDeflector;
import weather2.block.TileEntityWeatherDeflector;
import weather2.harness.RecordingWorld;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

public class LegacyDeflectorTest {

    private static boolean tileMappingAdded;
    private HashMap oldManagers;
    private long oldStormId;

    @Before
    public void setUp() {
        this.oldManagers = ServerTickHandler.lookupDimToWeatherMan;
        this.oldStormId = StormObject.lastUsedStormID;
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
        StormObject.lastUsedStormID = 0L;
        if (!tileMappingAdded) {
            TileEntity.addMapping(TileEntityWeatherDeflector.class, "WeatherDeflector");
            tileMappingAdded = true;
        }
    }

    @After
    public void tearDown() {
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
        StormObject.lastUsedStormID = this.oldStormId;
    }

    @Test
    public void legacyRegistryNamesStayStableAndBlockKeepsItsTileEntity() {
        BlockWeatherDeflector block = new BlockWeatherDeflector();
        block.setBlockName("weather2:WeatherDeflector");

        assertTrue(
            block.getUnlocalizedName()
                .contains("WeatherDeflector"));
        assertTrue(block.createNewTileEntity(null, 0) instanceof TileEntityWeatherDeflector);
        assertEquals(null, block.getCreativeTabToDisplayOn());
    }

    @Test
    public void legacyNbtFieldsRoundTripWithoutOperationalState() {
        TileEntityWeatherDeflector deflector = new TileEntityWeatherDeflector();
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setLong("power", 87654321L);
        legacy.setByte("deflectorState", (byte) 3);
        legacy.setInteger("stateTimer", 120);
        legacy.setBoolean("isFieldActive", true);
        legacy.setInteger("blackoutCooldown", 120);

        deflector.readFromNBT(legacy);
        NBTTagCompound saved = new NBTTagCompound();
        deflector.writeToNBT(saved);

        assertEquals(87654321L, saved.getLong("power"));
        assertEquals(3, saved.getByte("deflectorState"));
        assertEquals(120, saved.getInteger("stateTimer"));
        assertTrue(saved.getBoolean("isFieldActive"));
        assertEquals(120, saved.getInteger("blackoutCooldown"));
    }

    @Test
    public void legacyDeflectorDoesNotTickOrUsePower() {
        TileEntityWeatherDeflector deflector = new TileEntityWeatherDeflector();
        deflector.power = 50000000L;
        deflector.isFieldActive = true;

        deflector.updateEntity();

        assertEquals(50000000L, deflector.power);
        assertTrue(deflector.isFieldActive);
    }

    @Test
    public void legacyDeflectorDoesNotCancelStorms() {
        RecordingWorld world = RecordingWorld.create();
        RecordingManager manager = new RecordingManager(world);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), manager);
        StormObject storm = new StormObject(manager);
        storm.ID = 1L;
        storm.levelCurIntensityStage = StormObject.STATE_STAGE1;
        storm.tornadoActive = true;
        storm.tornadoRemainingTicks = 100;
        storm.pos = Vec3.createVectorHelper(19.0D, 200.0D, 0.0D);
        storm.posGround = Vec3.createVectorHelper(19.0D, 64.0D, 0.0D);
        manager.addStormObject(storm);

        TileEntityWeatherDeflector deflector = new TileEntityWeatherDeflector() {

            @Override
            public void markDirty() {}
        };
        RecordingWorld.setField(deflector, "worldObj", world);
        RecordingWorld.setField(deflector, "xCoord", Integer.valueOf(19));
        deflector.isFieldActive = true;

        deflector.updateEntity();

        assertNotNull(manager.lookupStormObjectsByID.get(Long.valueOf(storm.ID)));
        assertTrue(storm.tornadoActive);
        assertFalse(storm.tornadoEndPending);
    }

    private static final class RecordingManager extends WeatherManagerServer {

        private final RecordingWorld world;

        private RecordingManager(RecordingWorld world) {
            super(0);
            this.world = world;
        }

        @Override
        public net.minecraft.world.World getWorld() {
            return this.world;
        }

        @Override
        public void syncStormRemove(StormObject storm) {}
    }
}
