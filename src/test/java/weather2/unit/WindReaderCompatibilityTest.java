package weather2.unit;

import static org.junit.Assert.assertEquals;

import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.ClientTickHandler;
import weather2.ServerTickHandler;
import weather2.api.WindReader;
import weather2.harness.RecordingWorld;
import weather2.weathersystem.WeatherManagerClient;

public class WindReaderCompatibilityTest {

    private WeatherManagerClient oldManager;
    private WeatherManagerClient manager;
    private java.util.HashMap oldServerManagers;
    private World world;

    @Before
    public void setUp() {
        this.oldManager = ClientTickHandler.weatherManager;
        this.oldServerManagers = ServerTickHandler.lookupDimToWeatherMan;
        ServerTickHandler.lookupDimToWeatherMan = new java.util.HashMap();
        RecordingWorld recordingWorld = RecordingWorld.create();
        recordingWorld.isRemote = true;
        this.world = recordingWorld;
        this.manager = new WeatherManagerClient(recordingWorld);
        ClientTickHandler.weatherManager = this.manager;
        this.manager.windMan.windSpeedGlobal = 0.4F;
        this.manager.windMan.windAngleGlobal = 35.0F;
        this.manager.windMan.windSpeedEvent = 1.2F;
        this.manager.windMan.windAngleEvent = -80.0F;
        this.manager.windMan.windTimeEvent = 12;
    }

    @After
    public void tearDown() {
        ClientTickHandler.weatherManager = this.oldManager;
        ServerTickHandler.lookupDimToWeatherMan = this.oldServerManagers;
    }

    @Test
    public void legacyGettersIgnorePositionAndLocalNullPositionDelegatesToThem() {
        Vec3 near = Vec3.createVectorHelper(0.0D, 64.0D, 0.0D);
        Vec3 far = Vec3.createVectorHelper(10000.0D, 255.0D, -10000.0D);

        assertEquals(
            WindReader.getWindSpeed(this.world, near, WindReader.WindType.EVENT),
            WindReader.getWindSpeed(this.world, far, WindReader.WindType.EVENT),
            0.0F);
        assertEquals(
            WindReader.getWindAngle(this.world, near, WindReader.WindType.DOMINANT),
            WindReader.getWindAngle(this.world, far, WindReader.WindType.DOMINANT),
            0.0F);
        assertEquals(
            WindReader.getWindSpeed(this.world, null, WindReader.WindType.EVENT),
            WindReader.getLocalWindSpeed(this.world, null, WindReader.WindType.EVENT),
            0.0F);
        assertEquals(
            WindReader.getWindAngle(this.world, null, WindReader.WindType.DOMINANT),
            WindReader.getLocalWindAngle(this.world, null, WindReader.WindType.DOMINANT),
            0.0F);
    }
}
