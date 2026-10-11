package weather2;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.weathersystem.WeatherManagerServer;

public class ServerAutosaveTest {

    private HashMap oldManagers;

    @Before
    public void setUp() {
        this.oldManagers = ServerTickHandler.lookupDimToWeatherMan;
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
    }

    @After
    public void tearDown() {
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
    }

    @Test
    public void autosavePersistsManagersInEveryLoadedDimension() {
        CapturingManager overworld = new CapturingManager(0);
        CapturingManager nether = new CapturingManager(-1);
        CapturingManager end = new CapturingManager(1);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), overworld);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(-1), nether);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(1), end);

        ServerTickHandler.writeWeatherData();

        assertEquals(1, overworld.saveCount);
        assertEquals(1, nether.saveCount);
        assertEquals(1, end.saveCount);
    }

    private static final class CapturingManager extends WeatherManagerServer {

        private int saveCount;

        private CapturingManager(int dimension) {
            super(dimension);
        }

        @Override
        public World getWorld() {
            return null;
        }

        @Override
        public void writeToFile() {
            ++this.saveCount;
        }
    }
}
