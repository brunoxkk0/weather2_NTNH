package weather2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.world.World;

import org.junit.Test;

import weather2.harness.RecordingWorld;

public class WeatherPacketLifecycleTest {

    @Test
    public void queuedPacketRequiresItsOriginalConnectionAndWorld() {
        World world = RecordingWorld.create();
        Object handler = new Object();

        assertTrue(ClientTickHandler.isCurrentWeatherData(handler, handler, world, world));
        assertFalse(ClientTickHandler.isCurrentWeatherData(handler, new Object(), world, world));
        assertFalse(ClientTickHandler.isCurrentWeatherData(handler, handler, world, RecordingWorld.create()));
    }
}
