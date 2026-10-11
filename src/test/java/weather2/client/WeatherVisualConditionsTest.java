package weather2.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import weather2.config.ConfigMisc;
import weather2.harness.RecordingWorld;

public class WeatherVisualConditionsTest {

    @Test
    public void windMapsToSquallIntensityAndRampsAtMostFivePercentPerTick() {
        assertEquals(0.0F, SceneEnhancer.squallIntensity(0.7F), 0.0F);
        assertEquals(1.0F, SceneEnhancer.squallIntensity(1.4F), 0.0F);
        assertEquals(0.0F, SceneEnhancer.squallIntensity(Float.NaN), 0.0F);
        assertEquals(0.05F, SceneEnhancer.approachSquallIntensity(0.0F, 1.0F), 0.000001F);
        assertEquals(0.95F, SceneEnhancer.approachSquallIntensity(1.0F, 0.0F), 0.000001F);
        float ramped = 0.0F;
        for (int i = 0; i < 20; ++i) {
            ramped = SceneEnhancer.approachSquallIntensity(ramped, 1.0F);
        }
        assertEquals(1.0F, ramped, 0.000001F);
    }

    @Test
    public void extraParticlesRespectRateQualityAndSixteenParticleCap() {
        assertEquals(0, SceneEnhancer.extraParticleCount(1.0F, 0.0D, 0));
        assertEquals(0, SceneEnhancer.extraParticleCount(1.0F, 1.0D, 2));
        assertEquals(8, SceneEnhancer.extraParticleCount(1.0F, 1.0D, 1));
        assertEquals(0, SceneEnhancer.extraParticleCount(0.1F, 1.0D, 1));
        assertEquals(16, SceneEnhancer.extraParticleCount(1.0F, 2.0D, 0));
        assertEquals(8, SceneEnhancer.extraParticleCount(0.5F, 2.0D, 0));
        assertEquals(4, SceneEnhancer.extraParticleCount(0.5F, 0.5D, 0));
        assertEquals(0, SceneEnhancer.extraParticleCount(1.0F, Double.NaN, 0));
    }

    @Test
    public void sandSquallsRequireEnabledDryLoadedExposedSandyConditions() {
        assertFalse(SceneEnhancer.shouldEmitSandSquall(false, true, true, true, false));
        assertFalse(SceneEnhancer.shouldEmitSandSquall(true, false, true, true, false));
        assertFalse(SceneEnhancer.shouldEmitSandSquall(true, true, false, true, false));
        assertFalse(SceneEnhancer.shouldEmitSandSquall(true, true, true, true, true));
        assertTrue(SceneEnhancer.shouldEmitSandSquall(true, true, true, true, false));
    }

    @Test
    public void blizzardSnowRequiresColdPrecipitationAndEnabledBaseSnow() {
        assertFalse(SceneEnhancer.shouldEmitBlizzardSnow(false, true, true, true, true, true));
        assertFalse(SceneEnhancer.shouldEmitBlizzardSnow(true, false, true, true, true, true));
        assertFalse(SceneEnhancer.shouldEmitBlizzardSnow(true, true, false, true, true, true));
        assertFalse(SceneEnhancer.shouldEmitBlizzardSnow(true, true, true, false, true, true));
        assertFalse(SceneEnhancer.shouldEmitBlizzardSnow(true, true, true, true, false, true));
        assertFalse(SceneEnhancer.shouldEmitBlizzardSnow(true, true, true, true, true, false));
        assertTrue(SceneEnhancer.shouldEmitBlizzardSnow(true, true, true, true, true, true));
    }

    @Test
    public void newVisualTogglesAreDisabledByDefault() {
        assertFalse(ConfigMisc.Visual_SandSqualls);
        assertFalse(ConfigMisc.Visual_BlizzardWind);
    }

    @Test
    public void particlesAreOnlyPlacedInLoadedChunks() {
        RecordingWorld world = RecordingWorld.create();
        world.setChunkLoadedAt(1, 4, false);

        assertTrue(SceneEnhancer.isVisualParticleChunkLoaded(world, 15.99D, 64.0D));
        assertFalse(SceneEnhancer.isVisualParticleChunkLoaded(world, 16.0D, 64.0D));
        assertEquals(2, world.chunkExistsQueries);
    }
}
