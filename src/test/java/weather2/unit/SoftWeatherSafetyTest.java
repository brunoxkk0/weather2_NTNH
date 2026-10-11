package weather2.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.harness.MockBlockFactory;
import weather2.harness.MockStormFactory;
import weather2.harness.RecordingWorld;
import weather2.harness.TestEnvironment;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerBase;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.TornadoHelper;

public class SoftWeatherSafetyTest {

    private Block oldAir;
    private Block oldSnow;
    private Block oldSnowLayer;

    @Before
    public void setUp() {
        TestEnvironment.setupVanilla();
        this.oldAir = Blocks.air;
        this.oldSnow = Blocks.snow;
        this.oldSnowLayer = Blocks.snow_layer;
    }

    @After
    public void tearDown() {
        MockBlockFactory.setStaticBlock("air", this.oldAir);
        MockBlockFactory.setStaticBlock("snow", this.oldSnow);
        MockBlockFactory.setStaticBlock("snow_layer", this.oldSnowLayer);
        TestEnvironment.resetAll();
    }

    @Test
    public void tornadoDoesNotMutateTerrainForAnyStage() {
        Block[] terrain = { Blocks.grass, new Block(Material.glass) {}, new Block(Material.plants) {} };
        for (int stage = StormObject.STATE_FORMING; stage <= StormObject.STATE_STAGE5; ++stage) {
            for (int type = StormObject.TYPE_LAND; type <= StormObject.TYPE_WATER; ++type) {
                RecordingWorld world = RecordingWorld.create();
                world.defaultBlock = terrain[(stage + type) % terrain.length];
                TileEntityChest chest = new TileEntityChest();
                world.tileEntity = chest;
                NBTTagCompound before = new NBTTagCompound();
                chest.writeToNBT(before);
                StormObject storm = stormAt(stage, stage, world);
                storm.stormType = type;

                storm.tornadoHelper.tick(world);

                NBTTagCompound after = new NBTTagCompound();
                chest.writeToNBT(after);
                assertEquals("stage " + stage + " changed blocks", 0, world.blockWrites);
                assertTrue("stage " + stage + " spawned debris", world.spawned.isEmpty());
                assertEquals("stage " + stage + " changed a container", before.toString(), after.toString());
            }
        }
    }

    @Test
    public void destructiveSettingsAreNotPresentedByTheGui() {
        assertFalse(
            WeatherUtilConfig.listSettingsServer
                .contains(Integer.valueOf(WeatherUtilConfig.CMD_BTN_COMP_SNOWFALLBLOCKS)));
        assertFalse(
            WeatherUtilConfig.listSettingsServer
                .contains(Integer.valueOf(WeatherUtilConfig.CMD_BTN_PREF_BLOCKDESTRUCTION)));
    }

    @Test
    public void snowDoesNotPlaceBlocks() {
        MockBlockFactory.setStaticBlock("air", new AirBlock());
        MockBlockFactory.setStaticBlock("snow", new SnowLayerBlock());
        MockBlockFactory.setStaticBlock("snow_layer", new SnowLayerBlock());
        RecordingWorld world = RecordingWorld.create();
        world.defaultBlock = Blocks.air;
        world.groundBlock = new Block(Material.rock) {};
        world.groundHeight = 64;
        StormObject storm = stormAt(StormObject.STATE_NORMAL, 100L, world);
        storm.size = 32;
        storm.canSnowFromCloudTemperature = true;
        storm.levelTemperature = -1.0F;
        storm.attrib_precipitation = true;

        storm.tickSnowFall();

        assertEquals(0, world.blockWrites);
    }

    private static final class AirBlock extends Block {

        private AirBlock() {
            super(Material.air);
        }

        @Override
        public boolean isAir(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
            return true;
        }
    }

    private static final class SnowLayerBlock extends Block {

        private SnowLayerBlock() {
            super(Material.snow);
        }

        @Override
        public boolean canPlaceBlockAt(World world, int x, int y, int z) {
            return true;
        }
    }

    private static StormObject stormAt(int stage, long id, RecordingWorld world) {
        StormObject storm = MockStormFactory.create(stage, id, world.provider.dimensionId);
        storm.pos = Vec3.createVectorHelper(64.0D, 200.0D, 64.0D);
        storm.posBaseFormationPos = Vec3.createVectorHelper(64.0D, 64.0D, 64.0D);
        storm.posGround = Vec3.createVectorHelper(64.0D, 64.0D, 64.0D);
        storm.currentTopYBlock = 64;
        storm.maxHeight = 60;
        storm.manager = new WeatherManagerBase(world.provider.dimensionId) {

            @Override
            public net.minecraft.world.World getWorld() {
                return world;
            }
        };
        storm.tornadoHelper = new TornadoHelper(storm);
        return storm;
    }
}
