package weather2.integration;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import org.junit.Test;

import weather2.entity.EntityIceBall;
import weather2.entity.EntityLightningBolt;
import weather2.entity.EntityMovingBlock;
import weather2.harness.MockBlockFactory;
import weather2.harness.RecordingWorld;
import weather2.harness.TestEnvironment;
import weather2.volcano.VolcanoObject;
import weather2.weathersystem.WeatherManagerBase;
import weather2.weathersystem.wind.WindManager;

public class LegacyWeatherSafetyTest {

    private Block oldAir;
    private Block oldFire;

    @org.junit.Before
    public void setUp() {
        TestEnvironment.setupVanilla();
        this.oldAir = Blocks.air;
        this.oldFire = Blocks.fire;
    }

    @org.junit.After
    public void tearDown() {
        MockBlockFactory.setStaticBlock("air", this.oldAir);
        MockBlockFactory.setStaticBlock("fire", this.oldFire);
        TestEnvironment.resetAll();
    }

    @Test
    public void lightningDoesNotStartFire() {
        MockBlockFactory.setStaticBlock("air", new Block(Material.air) {

            @Override
            public boolean isAir(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
                return true;
            }
        });
        MockBlockFactory.setStaticBlock("fire", new MockFire());
        RecordingWorld world = RecordingWorld.create();
        world.defaultBlock = Blocks.air;
        world.putBlock(8, 63, 8, Blocks.stone);

        EntityLightningBolt lightning = new EntityLightningBolt(world, 8.0D, 64.0D, 8.0D);
        for (int i = 0; i < 12; ++i) lightning.onUpdate();

        assertEquals(0, world.blockWrites);
    }

    @Test
    public void hailDoesNotDamageEntities() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        EntityIceBall hail = new EntityIceBall(world);
        DamageTarget target = new DamageTarget(world);
        Method onImpact = EntityIceBall.class.getDeclaredMethod("onImpact", MovingObjectPosition.class);
        onImpact.setAccessible(true);

        onImpact.invoke(hail, new MovingObjectPosition(target));

        assertEquals(0, target.damageCalls);
    }

    @Test
    public void legacySavedThrownDebrisLoadsDead() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.putBlock(4, 64, 4, Blocks.stone);
        EntityMovingBlock debris = new EntityMovingBlock(world);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("Tile", "minecraft:stone");
        legacy.setByte("Metadata", (byte) 0);
        legacy.setInteger("blocktype", EntityMovingBlock.grabbed);
        legacy.setTag("TileEntity", new NBTTagCompound());

        Method read = EntityMovingBlock.class.getDeclaredMethod("readEntityFromNBT", NBTTagCompound.class);
        read.setAccessible(true);
        read.invoke(debris, legacy);

        org.junit.Assert.assertTrue(debris.isDead);
        debris.onUpdate();

        assertEquals(0, world.blockWrites);
        org.junit.Assert.assertSame(Blocks.stone, world.getBlock(4, 64, 4));
    }

    @Test
    public void legacyDebrisAndVolcanoesRemainInert() {
        RecordingWorld world = RecordingWorld.create();
        world.tileEntity = new TileEntityChest();
        EntityMovingBlock debris = new EntityMovingBlock(world, 4, 64, 4, new Block(Material.rock) {}, null);
        EntityMovingBlock oldThrownDebris = new EntityMovingBlock(world);
        oldThrownDebris.tile = new Block(Material.rock) {};
        oldThrownDebris.type = EntityMovingBlock.grabbed;
        WeatherManagerBase manager = new WeatherManagerBase(0) {

            @Override
            public World getWorld() {
                return world;
            }
        };
        manager.windMan = new WindManager(manager) {

            @Override
            public void tick() {}
        };
        TestVolcano volcano = new TestVolcano(manager);
        manager.addVolcanoObject(volcano);
        VolcanoObject legacyVolcano = new VolcanoObject(manager);
        legacyVolcano.tick();

        debris.onUpdate();
        oldThrownDebris.onUpdate();
        manager.tick();

        assertEquals(0, world.blockWrites);
        org.junit.Assert.assertTrue(debris.isDead);
        org.junit.Assert.assertTrue(oldThrownDebris.isDead);
        assertEquals(0, volcano.ticks);
    }

    private static final class MockFire extends BlockFire {

        private MockFire() {}

        @Override
        public boolean canPlaceBlockAt(World world, int x, int y, int z) {
            return true;
        }
    }

    private static final class TestVolcano extends VolcanoObject {

        private int ticks;

        private TestVolcano(WeatherManagerBase manager) {
            super(manager);
        }

        @Override
        public void tick() {
            ++this.ticks;
        }
    }

    private static final class DamageTarget extends Entity {

        private int damageCalls;

        private DamageTarget(World world) {
            super(world);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(NBTTagCompound tag) {}

        @Override
        public boolean attackEntityFrom(DamageSource source, float damage) {
            ++this.damageCalls;
            return true;
        }
    }
}
