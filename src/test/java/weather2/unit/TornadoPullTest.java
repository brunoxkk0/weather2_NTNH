package weather2.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import extendedrenderer.particle.entity.EntityRotFX;
import sun.misc.Unsafe;
import weather2.harness.MockStormFactory;
import weather2.harness.RecordingWorld;
import weather2.harness.TestEnvironment;
import weather2.util.WeatherUtilEntity;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.TornadoHelper;

public class TornadoPullTest {

    private static final Unsafe UNSAFE = unsafe();

    @Before
    public void setUp() {
        TestEnvironment.setupVanilla();
    }

    @After
    public void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    public void defaultsDoNotPullLivingEntities() throws Exception {
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        EntityPlayerMP player = (EntityPlayerMP) UNSAFE.allocateInstance(EntityPlayerMP.class);
        TestLiving creature = new TestLiving(null);

        assertFalse(storm.shouldPull(player));
        assertFalse(storm.shouldPull(creature));
        storm.spinEntity(player);
        storm.spinEntity(creature);
        assertEquals(0.0D, player.motionX, 0.0D);
        assertEquals(0.0D, creature.motionX, 0.0D);
    }

    @Test
    public void playersAndCreaturesAreIndependent() throws Exception {
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        EntityPlayerMP player = (EntityPlayerMP) UNSAFE.allocateInstance(EntityPlayerMP.class);
        TestLiving creature = new TestLiving(null);

        storm.pullPlayers = true;
        assertTrue(storm.shouldPull(player));
        assertFalse(storm.shouldPull(creature));

        storm.pullLivingEntities = true;
        storm.pullPlayers = false;
        assertFalse(storm.shouldPull(player));
        assertTrue(storm.shouldPull(creature));
    }

    @Test
    public void tornadoHelperOnlyAppliesEnabledLivingPull() {
        RecordingWorld world = RecordingWorld.create();
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.pos = net.minecraft.util.Vec3.createVectorHelper(0.0D, 200.0D, 0.0D);
        storm.posGround = net.minecraft.util.Vec3.createVectorHelper(0.0D, 64.0D, 0.0D);
        storm.posBaseFormationPos = storm.posGround;
        storm.currentTopYBlock = 64;
        storm.maxHeight = 60;
        storm.scale = 1.0F;
        storm.strength = 100.0F;
        storm.tornadoActive = true;
        storm.tornadoHelper = new TornadoHelper(storm);
        TestLiving creature = new TestLiving(world);
        creature.setPosition(4.0D, 100.0D, 0.0D);
        creature.onGround = true;
        net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(world);
        item.setPosition(4.0D, 100.0D, 0.0D);
        net.minecraft.entity.item.EntityBoat boat = new net.minecraft.entity.item.EntityBoat(world);
        boat.setPosition(4.0D, 100.0D, 0.0D);
        world.queryEntities.add(creature);
        world.queryEntities.add(item);
        world.queryEntities.add(boat);

        assertFalse(storm.tornadoHelper.forceRotate(world));

        assertEquals(0.0D, creature.motionX, 0.0D);
        assertEquals(0.0D, creature.motionY, 0.0D);
        assertEquals(0.0D, creature.motionZ, 0.0D);

        storm.pullLivingEntities = true;
        assertTrue(storm.tornadoHelper.forceRotate(world));

        assertTrue(Math.abs(creature.motionX) + Math.abs(creature.motionY) + Math.abs(creature.motionZ) > 0.0D);
        assertEquals(0.0D, item.motionX, 0.0D);
        assertEquals(0.0D, item.motionY, 0.0D);
        assertEquals(0.0D, item.motionZ, 0.0D);
        assertEquals(0.0D, boat.motionX, 0.0D);
        assertEquals(0.0D, boat.motionY, 0.0D);
        assertEquals(0.0D, boat.motionZ, 0.0D);
    }

    @Test
    public void explicitPlayerPullStillRespectsWindShelter() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.setHeightValue(4, 0, 100);
        world.setHeightValue(4, -5, 100);
        world.setHeightValue(4, 5, 100);
        world.setHeightValue(-1, 0, 100);
        world.setHeightValue(9, 0, 100);
        EntityPlayerMP player = (EntityPlayerMP) UNSAFE.allocateInstance(EntityPlayerMP.class);
        RecordingWorld.setField(player, "worldObj", world);
        player.posX = 4.0D;
        player.posY = 65.0D;
        player.posZ = 0.0D;
        world.queryEntities.add(player);

        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.pullPlayers = true;
        storm.tornadoActive = true;
        storm.currentTopYBlock = 64;
        storm.maxHeight = 60;
        storm.pos = net.minecraft.util.Vec3.createVectorHelper(0.0D, 200.0D, 0.0D);
        storm.posGround = net.minecraft.util.Vec3.createVectorHelper(0.0D, 64.0D, 0.0D);
        storm.posBaseFormationPos = storm.posGround;
        storm.tornadoHelper = new TornadoHelper(storm);

        assertFalse(storm.tornadoHelper.forceRotate(world));
        assertEquals(0.0D, player.motionX, 0.0D);
        assertEquals(0.0D, player.motionY, 0.0D);
        assertEquals(0.0D, player.motionZ, 0.0D);
    }

    @Test
    public void tornadoHelperOnlyAppliesEnabledPlayerPull() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.pos = net.minecraft.util.Vec3.createVectorHelper(0.0D, 200.0D, 0.0D);
        storm.posGround = net.minecraft.util.Vec3.createVectorHelper(0.0D, 64.0D, 0.0D);
        storm.posBaseFormationPos = storm.posGround;
        storm.currentTopYBlock = 64;
        storm.maxHeight = 60;
        storm.scale = 1.0F;
        storm.strength = 100.0F;
        storm.tornadoActive = true;
        storm.tornadoHelper = new TornadoHelper(storm);
        EntityPlayerMP player = (EntityPlayerMP) UNSAFE.allocateInstance(EntityPlayerMP.class);
        RecordingWorld.setField(player, "worldObj", world);
        player.capabilities = new PlayerCapabilities();
        player.posX = 4.0D;
        player.posY = 100.0D;
        player.posZ = 0.0D;
        player.onGround = true;
        world.queryEntities.add(player);

        assertFalse(WeatherUtilEntity.shouldIgnoreTornadoFlightCheck(player));
        assertFalse(storm.tornadoHelper.forceRotate(world));
        assertEquals(0.0D, player.motionX, 0.0D);
        assertEquals(0.0D, player.motionY, 0.0D);
        assertEquals(0.0D, player.motionZ, 0.0D);

        storm.pullPlayers = true;

        assertTrue(storm.tornadoHelper.forceRotate(world));
        assertTrue(Math.abs(player.motionX) + Math.abs(player.motionY) + Math.abs(player.motionZ) > 0.0D);
        assertTrue(
            player.getEntityData()
                .hasKey("weather2TornadoPullUntil"));
        assertTrue(WeatherUtilEntity.shouldIgnoreTornadoFlightCheck(player));

        player.getEntityData()
            .setLong("weather2TornadoPullUntil", world.getTotalWorldTime() - 1L);
        assertFalse(WeatherUtilEntity.shouldIgnoreTornadoFlightCheck(player));

        player.getEntityData()
            .setLong("weather2TornadoPullUntil", world.getTotalWorldTime() + 100L);
        RecordingWorld otherDimension = RecordingWorld.create();
        RecordingWorld.setField(otherDimension.provider, "dimensionId", Integer.valueOf(1));
        RecordingWorld.setField(player, "worldObj", otherDimension);
        assertFalse(
            "A tornado pull grace period must not follow a player to another dimension.",
            WeatherUtilEntity.shouldIgnoreTornadoFlightCheck(player));
    }

    @Test
    public void itemsAndVehiclesAreNotPulled() {
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        RecordingWorld world = RecordingWorld.create();

        assertFalse(storm.shouldPull(new net.minecraft.entity.item.EntityItem(world)));
        assertFalse(storm.shouldPull(new net.minecraft.entity.item.EntityBoat(world)));
    }

    @Test
    public void funnelParticlesStillRotate() {
        RecordingWorld world = RecordingWorld.create();
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.pos = net.minecraft.util.Vec3.createVectorHelper(64.0D, 200.0D, 64.0D);
        storm.posGround = net.minecraft.util.Vec3.createVectorHelper(64.0D, 64.0D, 64.0D);
        storm.posBaseFormationPos = storm.posGround;
        storm.maxHeight = 60;
        storm.scale = 1.0F;
        storm.strength = 100.0F;
        EntityRotFX particle = new EntityRotFX(world, 60.0D, 70.0D, 64.0D, 0.0D, 0.0D, 0.0D);

        assertFalse(storm.shouldPull(particle));
        storm.spinEntity(particle);

        assertTrue(Math.abs(particle.motionX) + Math.abs(particle.motionZ) > 0.0D);
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final class TestLiving extends EntityLivingBase {

        private TestLiving(World world) {
            super(world);
        }

        @Override
        public ItemStack getHeldItem() {
            return null;
        }

        @Override
        public ItemStack getEquipmentInSlot(int slot) {
            return null;
        }

        @Override
        public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

        @Override
        public ItemStack[] getLastActiveItems() {
            return new ItemStack[0];
        }

        @Override
        public boolean handleWaterMovement() {
            return false;
        }
    }
}
