package weather2.weathersystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.Test;

import weather2.harness.RecordingWorld;
import weather2.weathersystem.storm.StormObject;

public class WeatherFullSyncTest {

    @Test
    public void snapshotPacketsRestoreStormWindAndVanillaWeather() {
        World world = RecordingWorld.create();
        WeatherManagerClient manager = new WeatherManagerClient(world);

        manager.nbtSyncFromServer(stormPacket());
        manager.nbtSyncFromServer(windPacket());
        manager.nbtSyncFromServer(weatherPacket());

        assertEquals(1, manager.getStormObjects().size());
        assertEquals(2, ((StormObject) manager.getStormObjects().get(0)).levelCurIntensityStage);
        assertEquals(0.5F, manager.windMan.getWindSpeedForPriority(), 0.0001F);
        assertTrue(manager.isVanillaRainActiveOnServer);
        assertTrue(manager.isVanillaThunderActiveOnServer);
    }

    private static NBTTagCompound stormPacket() {
        NBTTagCompound packet = new NBTTagCompound();
        packet.setString("command", "syncStormNew");
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("ID", 23L);
        data.setInteger("layer", 0);
        data.setInteger("levelCurIntensityStage", 2);
        data.setInteger("posX", 0);
        data.setInteger("posY", 80);
        data.setInteger("posZ", 0);
        packet.setTag("data", data);
        return packet;
    }

    private static NBTTagCompound windPacket() {
        NBTTagCompound packet = new NBTTagCompound();
        packet.setString("command", "syncWindUpdate");
        NBTTagCompound data = new NBTTagCompound();
        data.setFloat("windSpeedGlobal", 0.5F);
        packet.setTag("data", data);
        return packet;
    }

    private static NBTTagCompound weatherPacket() {
        NBTTagCompound packet = new NBTTagCompound();
        packet.setString("command", "syncWeatherUpdate");
        packet.setBoolean("isVanillaRainActiveOnServer", true);
        packet.setBoolean("isVanillaThunderActiveOnServer", true);
        return packet;
    }
}
