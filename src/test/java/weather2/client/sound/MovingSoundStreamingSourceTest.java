package weather2.client.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.Vec3;

import org.junit.Test;

import sun.misc.Unsafe;
import weather2.harness.MockStormFactory;
import weather2.harness.RecordingWorld;
import weather2.weathersystem.storm.StormObject;

public class MovingSoundStreamingSourceTest {

    private static final Unsafe UNSAFE = getUnsafe();

    @Test
    public void attenuatesFromOriginalVolumeAndClampsAtRange() {
        assertEquals(0.25F, MovingSoundStreamingSource.attenuatedVolume(0.25F, 0.0D, 100.0F), 0.0F);
        assertEquals(0.125F, MovingSoundStreamingSource.attenuatedVolume(0.25F, 50.0D, 100.0F), 0.0F);
        assertEquals(0.0F, MovingSoundStreamingSource.attenuatedVolume(0.25F, 100.0D, 100.0F), 0.0F);
        assertEquals(0.0F, MovingSoundStreamingSource.attenuatedVolume(0.25F, 0.0D, 0.0F), 0.0F);
        assertEquals(0.0F, MovingSoundStreamingSource.attenuatedVolume(0.0F, 0.0D, 100.0F), 0.0F);
        assertEquals(1.5F, MovingSoundStreamingSource.attenuatedVolume(1.5F, 0.0D, 100.0F), 0.0F);
        assertEquals(0.0F, MovingSoundStreamingSource.attenuatedVolume(-0.5F, 0.0D, 100.0F), 0.0F);
    }

    @Test
    public void repeatedUpdatesDoNotCompoundAndChangedWorldStopsSound() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        EntityClientPlayerMP player = playerAt(world, 0.0D, 64.0D, 0.0D);
        MovingSoundStreamingSource sound = sound(world, Vec3.createVectorHelper(50.0D, 64.0D, 0.0D));

        sound.updateSound(player, world);
        assertEquals(0.125F, getFloatField(sound, "volume"), 0.0F);
        sound.updateSound(player, world);
        assertEquals(0.125F, getFloatField(sound, "volume"), 0.0F);
        assertFalse(getBooleanField(sound, "donePlaying"));

        sound.updateSound(player, RecordingWorld.create());
        assertTrue(getBooleanField(sound, "donePlaying"));
        assertEquals(0.0F, getFloatField(sound, "volume"), 0.0F);
    }

    @Test
    public void missingPlayerOrSourceAndDeadStormStopSound() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        MovingSoundStreamingSource noPlayer = sound(world, Vec3.createVectorHelper(0.0D, 64.0D, 0.0D));
        noPlayer.updateSound(null, world);
        assertStopped(noPlayer);

        EntityClientPlayerMP player = playerAt(world, 0.0D, 64.0D, 0.0D);
        MovingSoundStreamingSource noSource = sound(world, null);
        noSource.updateSound(player, world);
        assertStopped(noSource);

        MovingSoundStreamingSource deadStormSound = sound(world, null);
        StormObject storm = MockStormFactory.create(StormObject.STATE_FORMING);
        storm.isDead = true;
        setField(deadStormSound, "storm", storm);
        deadStormSound.updateSound(player, world);
        assertStopped(deadStormSound);
    }

    @Test
    public void invalidRangeStopsSound() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        MovingSoundStreamingSource sound = sound(world, Vec3.createVectorHelper(0.0D, 64.0D, 0.0D));
        sound.cutOffRange = 0.0F;

        sound.updateSound(playerAt(world, 0.0D, 64.0D, 0.0D), world);

        assertStopped(sound);
    }

    private static MovingSoundStreamingSource sound(RecordingWorld world, Vec3 source) throws Exception {
        MovingSoundStreamingSource sound = (MovingSoundStreamingSource) UNSAFE
            .allocateInstance(MovingSoundStreamingSource.class);
        setField(sound, "baseVolume", Float.valueOf(0.25F));
        sound.cutOffRange = 100.0F;
        sound.realSource = source;
        setField(sound, "sourceWorld", world);
        return sound;
    }

    private static EntityClientPlayerMP playerAt(RecordingWorld world, double x, double y, double z) throws Exception {
        EntityClientPlayerMP player = (EntityClientPlayerMP) UNSAFE.allocateInstance(EntityClientPlayerMP.class);
        player.worldObj = world;
        player.posX = x;
        player.posY = y;
        player.posZ = z;
        return player;
    }

    private static void assertStopped(MovingSoundStreamingSource sound) {
        assertTrue(getBooleanField(sound, "donePlaying"));
        assertEquals(0.0F, getFloatField(sound, "volume"), 0.0F);
    }

    private static float getFloatField(Object object, String fieldName) {
        return ((Float) getField(object, fieldName)).floatValue();
    }

    private static boolean getBooleanField(Object object, String fieldName) {
        return ((Boolean) getField(object, fieldName)).booleanValue();
    }

    private static Object getField(Object object, String fieldName) {
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalArgumentException(fieldName);
    }

    private static void setField(Object object, String fieldName, Object value) throws Exception {
        Field field = object.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
