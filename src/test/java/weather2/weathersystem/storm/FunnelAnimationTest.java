package weather2.weathersystem.storm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import extendedrenderer.particle.entity.EntityRotFX;
import weather2.harness.MockStormFactory;
import weather2.harness.RecordingWorld;
import weather2.harness.TestEnvironment;
import weather2.util.WeatherUtilParticle;
import weather2.weathersystem.WeatherManagerBase;

public class FunnelAnimationTest {

    @Before
    public void setUp() {
        TestEnvironment.setupVanilla();
    }

    @After
    public void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    public void renderingKeepsFunnelParticleOrientationAcrossFramesAndTicks() {
        RecordingWorld world = RecordingWorld.create();
        world.isRemote = true;
        world.worldTime = 10L;
        StormObject storm = MockStormFactory.create(StormObject.STATE_STAGE3, 8L, 0);
        storm.manager = new TestManager(world);
        storm.layer = 0;
        storm.spinSpeed = 0.08D;
        storm.pos = Vec3.createVectorHelper(0.0D, StormObject.static_YPos_layer0, 0.0D);
        storm.tornadoActive = true;
        storm.tornadoRemainingTicks = 45;
        storm.pullPlayers = true;
        storm.pullLivingEntities = true;
        storm.listParticlesFunnel = new ArrayList();

        EntityRotFX particle = new EntityRotFX(world, 3.0D, 100.0D, 4.0D, 0.0D, 0.0D, 0.0D);
        particle.setMaxAge(120);
        WeatherUtilParticle.setParticleAge(particle, 17);
        storm.listParticlesFunnel.add(particle);
        particle.rotationYaw = 37.0F;
        particle.rotationPitch = -30.0F;
        float initialYaw = particle.rotationYaw;

        for (int tick = 10; tick < 13; tick++) {
            world.worldTime = tick;
            for (float partialTick : new float[] { 0.0F, 0.5F, 0.5F, 1.0F }) {
                storm.tickRender(partialTick);
                assertEquals(initialYaw, particle.rotationYaw, 0.0F);
                assertEquals(-30.0F, particle.rotationPitch, 0.0F);
                assertEquals(3.0D, particle.posX, 0.0D);
                assertEquals(100.0D, particle.posY, 0.0D);
                assertEquals(4.0D, particle.posZ, 0.0D);
            }
        }
        assertEquals(StormObject.STATE_STAGE3, storm.levelCurIntensityStage);
        assertEquals(45, storm.tornadoRemainingTicks);
        assertTrue(storm.pullPlayers);
        assertTrue(storm.pullLivingEntities);
        assertEquals(1, storm.listParticlesFunnel.size());
        assertEquals(17, WeatherUtilParticle.getParticleAge(particle));
        assertEquals(120, maxAge(particle));
    }

    private static int maxAge(EntityRotFX particle) {
        try {
            Field field = EntityFX.class.getDeclaredField("particleMaxAge");
            field.setAccessible(true);
            return field.getInt(particle);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static class TestManager extends WeatherManagerBase {

        private final World world;

        TestManager(World world) {
            super(0);
            this.world = world;
        }

        @Override
        public World getWorld() {
            return this.world;
        }
    }
}
