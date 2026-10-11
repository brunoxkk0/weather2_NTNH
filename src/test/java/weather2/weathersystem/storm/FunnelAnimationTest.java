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
    public void phaseIsContinuousAcrossTicksAndClampsPartialTick() {
        assertEquals(StormObject.funnelPhase(10L, 1.0F, 0.12D), StormObject.funnelPhase(11L, 0.0F, 0.12D), 0.0D);
        assertEquals(StormObject.funnelPhase(5L, 0.0F, 0.12D), StormObject.funnelPhase(5L, -4.0F, 0.12D), 0.0D);
        assertEquals(StormObject.funnelPhase(6L, 0.0F, 0.12D), StormObject.funnelPhase(5L, 4.0F, 0.12D), 0.0D);
        assertEquals(StormObject.funnelPhase(5L, 0.0F, 0.12D), StormObject.funnelPhase(5L, Float.NaN, 0.12D), 0.0D);
        assertEquals(StormObject.funnelPhase(15L, 0.25F, 0.12D), StormObject.funnelPhase(15L, 0.25F, 0.12D), 0.0D);
    }

    @Test
    public void renderInterpolationRotatesFunnelWithoutChangingSimulationState() {
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
        float initialYaw = particle.rotationYaw;

        storm.tickRender(0.0F);
        float firstYaw = particle.rotationYaw;
        storm.tickRender(0.5F);

        assertTrue(Math.abs(particle.rotationYaw - firstYaw) > 1.0F);
        assertTrue(Math.abs(firstYaw - initialYaw) > 1.0F);
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
