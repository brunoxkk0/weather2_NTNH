package weather2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import CoroUtil.util.CoroUtilFile;
import weather2.weathersystem.storm.StormObject;

public class ServerTickHandlerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private long oldStormId;
    private ArrayList oldManagers;
    private HashMap oldManagerLookup;
    private String oldWorldFolder;

    @Before
    public void setUp() {
        this.oldStormId = StormObject.lastUsedStormID;
        StormObject.lastUsedStormID = 0L;
        this.oldManagers = ServerTickHandler.listWeatherMans;
        this.oldManagerLookup = ServerTickHandler.lookupDimToWeatherMan;
        ServerTickHandler.listWeatherMans = new ArrayList();
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
        this.oldWorldFolder = CoroUtilFile.lastWorldFolder;
        CoroUtilFile.lastWorldFolder = "ServerTickHandlerTest-" + this.temporaryFolder.getRoot()
            .getName();
        ServerTickHandler.resetSavedStormIdsScan();
    }

    @After
    public void tearDown() {
        StormObject.lastUsedStormID = this.oldStormId;
        ServerTickHandler.listWeatherMans = this.oldManagers;
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagerLookup;
        CoroUtilFile.lastWorldFolder = this.oldWorldFolder;
        ServerTickHandler.resetSavedStormIdsScan();
    }

    @Test
    public void scansEveryDimensionSaveAndBackupBeforeAllocatingIds() throws Exception {
        writeSave("WeatherData_0.dat", 12L, 16L);
        writeSave("WeatherData_7_BACKUP0.dat", 18L, 90L);

        assertTrue(ServerTickHandler.scanSavedStormIds(this.temporaryFolder.getRoot()));

        assertEquals(91L, StormObject.lastUsedStormID);
    }

    @Test
    public void directManagerRegistrationWaitsForSavedIdScan() {
        ServerTickHandler.addWorldToWeather(0);

        assertTrue(ServerTickHandler.listWeatherMans.isEmpty());
        assertTrue(ServerTickHandler.lookupDimToWeatherMan.isEmpty());
        assertEquals(0L, StormObject.lastUsedStormID);
    }

    @Test
    public void failedScanRemainsPendingUntilRetrySucceeds() throws Exception {
        File notDirectory = new File(this.temporaryFolder.getRoot(), "not-a-directory");
        assertTrue(notDirectory.createNewFile());

        assertFalse(ServerTickHandler.scanSavedStormIdsIfNeeded(notDirectory, 0L));
        assertFalse(ServerTickHandler.scanSavedStormIdsIfNeeded(notDirectory, 199L));

        assertTrue(notDirectory.delete());
        assertTrue(notDirectory.mkdir());
        writeSave(notDirectory, "WeatherData_7_BACKUP0.dat", 18L, 90L);

        assertTrue(ServerTickHandler.scanSavedStormIdsIfNeeded(notDirectory, 200L));
        assertEquals(91L, StormObject.lastUsedStormID);
    }

    @Test
    public void inaccessibleParentIsNotTreatedAsAnEmptyWeatherFolder() throws Exception {
        File parentFile = new File(this.temporaryFolder.getRoot(), "not-a-directory");
        assertTrue(parentFile.createNewFile());

        assertFalse(ServerTickHandler.scanSavedStormIds(new File(parentFile, "weather2")));
    }

    @Test
    public void nonFileWeatherDataEntryDoesNotCompleteScan() throws Exception {
        File nonFile = new File(this.temporaryFolder.getRoot(), "WeatherData_7_BACKUP0.dat");
        assertTrue(nonFile.mkdir());

        assertFalse(ServerTickHandler.scanSavedStormIds(this.temporaryFolder.getRoot()));
    }

    @Test
    public void unreadableWeatherDataFileKeepsScanPending() throws Exception {
        writeSave("WeatherData_0.dat", 12L, 16L);
        File unreadable = new File(this.temporaryFolder.getRoot(), "WeatherData_7_BACKUP0.dat");
        assertTrue(unreadable.createNewFile());

        assertFalse(ServerTickHandler.scanSavedStormIdsIfNeeded(this.temporaryFolder.getRoot(), 0L));
        assertEquals(17L, StormObject.lastUsedStormID);
        assertTrue(unreadable.delete());
        assertTrue(ServerTickHandler.scanSavedStormIdsIfNeeded(this.temporaryFolder.getRoot(), 200L));
    }

    private void writeSave(String name, long nextId, long stormId) throws Exception {
        writeSave(this.temporaryFolder.getRoot(), name, nextId, stormId);
    }

    private void writeSave(File directory, String name, long nextId, long stormId) throws Exception {
        NBTTagCompound save = new NBTTagCompound();
        save.setLong("lastUsedIDStorm", nextId);
        NBTTagCompound storms = new NBTTagCompound();
        NBTTagCompound storm = new NBTTagCompound();
        storm.setLong("ID", stormId);
        storms.setTag("storm_" + stormId, storm);
        save.setTag("stormData", storms);
        File file = new File(directory, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(save, output);
        }
    }
}
