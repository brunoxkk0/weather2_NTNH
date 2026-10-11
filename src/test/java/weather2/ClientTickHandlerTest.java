package weather2;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.harness.RecordingWorld;
import weather2.weathersystem.WeatherManagerClient;

public class ClientTickHandlerTest {

    private World oldWorld;
    private WeatherManagerClient oldManager;

    @Before
    public void setUp() {
        this.oldWorld = ClientTickHandler.lastWorld;
        this.oldManager = ClientTickHandler.weatherManager;
    }

    @After
    public void tearDown() {
        ClientTickHandler.lastWorld = this.oldWorld;
        ClientTickHandler.weatherManager = this.oldManager;
    }

    @Test
    public void loginSyncUsesManagerForCurrentClientWorld() {
        World previousWorld = RecordingWorld.create();
        World currentWorld = RecordingWorld.create();
        ClientTickHandler.lastWorld = previousWorld;
        ClientTickHandler.weatherManager = new WeatherManagerClient(previousWorld.provider.dimensionId);
        WeatherManagerClient previousManager = ClientTickHandler.weatherManager;

        ClientTickHandler.checkClientWeather(currentWorld);

        assertSame(currentWorld, ClientTickHandler.lastWorld);
        assertNotSame(previousManager, ClientTickHandler.weatherManager);
    }
}
