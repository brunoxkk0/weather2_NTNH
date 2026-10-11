package weather2.weathersystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.volcano.VolcanoObject;
import weather2.weathersystem.storm.StormObject;

public class LegacyWeatherLoadTest {

    private long oldVolcanoId;
    private long oldStormId;

    @Before
    public void setUp() {
        this.oldVolcanoId = VolcanoObject.lastUsedID;
        this.oldStormId = StormObject.lastUsedStormID;
    }

    @After
    public void tearDown() {
        VolcanoObject.lastUsedID = this.oldVolcanoId;
        StormObject.lastUsedStormID = this.oldStormId;
    }

    @Test
    public void persistedVolcanoesAreIgnored() {
        VolcanoObject.lastUsedID = 42L;
        NBTTagCompound oldSave = new NBTTagCompound();
        oldSave.setLong("lastUsedIDVolcano", 900L);
        NBTTagCompound volcanoes = new NBTTagCompound();
        volcanoes.setTag("volcano_899", new NBTTagCompound());
        oldSave.setTag("volcanoData", volcanoes);

        WeatherManagerBase manager = new WeatherManagerBase(0);
        manager.readFromNBT(oldSave);

        assertTrue(
            manager.getVolcanoObjects()
                .isEmpty());
        assertTrue(manager.lookupVolcanoes.isEmpty());
        assertEquals(42L, VolcanoObject.lastUsedID);
    }
}
