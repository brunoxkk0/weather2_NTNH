package weather2.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.world.World;

import org.junit.Test;

import weather2.harness.RecordingWorld;

public class SceneEnhancerLifecycleTest {

    @Test
    public void delayedWorkerResultsOnlyPublishToTheirOriginalWorld() {
        World oldWorld = RecordingWorld.create();
        World currentWorld = RecordingWorld.create();

        assertTrue(SceneEnhancer.canPublishForWorld(oldWorld, oldWorld));
        assertFalse(SceneEnhancer.canPublishForWorld(oldWorld, currentWorld));
    }
}
