package weather2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
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

    @Test
    public void packetForOtherDimensionIsIgnored() {
        World world = RecordingWorld.create();
        ClientTickHandler.lastWorld = world;
        ClientTickHandler.weatherManager = new WeatherManagerClient(world);
        NBTTagCompound packet = new NBTTagCompound();
        packet.setString("command", "syncWeatherUpdate");
        packet.setInteger("dimensionId", 7);

        ClientTickHandler.applyWeatherData(world, packet);

        assertFalse(ClientTickHandler.weatherManager.isVanillaRainActiveOnServer);
    }

    @Test
    public void packetWithoutWorldDoesNotInitializeManager() {
        WeatherManagerClient oldManager = ClientTickHandler.weatherManager;
        ClientTickHandler.lastWorld = null;
        ClientTickHandler.weatherManager = null;
        try {
            ClientTickHandler.applyWeatherData(null, new NBTTagCompound());
            assertNull(ClientTickHandler.weatherManager);
        } finally {
            ClientTickHandler.weatherManager = oldManager;
        }
    }

    @Test
    public void delayedPacketRequiresTheSameHandlerAndWorld() {
        World world = RecordingWorld.create();
        Object handler = new Object();

        assertTrue(ClientTickHandler.isCurrentWeatherData(handler, handler, world, world));
        assertFalse(ClientTickHandler.isCurrentWeatherData(handler, new Object(), world, world));
        assertFalse(ClientTickHandler.isCurrentWeatherData(handler, handler, world, RecordingWorld.create()));
    }
}
