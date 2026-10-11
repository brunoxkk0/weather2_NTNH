package weather2.weathersystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.After;
import org.junit.Test;

import weather2.ClientTickHandler;
import weather2.harness.RecordingWorld;
import weather2.weathersystem.storm.StormObject;

public class WeatherManagerClientSyncTest {

    private final WeatherManagerClient oldManager = ClientTickHandler.weatherManager;

    @After
    public void restoreManager() {
        ClientTickHandler.weatherManager = this.oldManager;
    }

    @Test
    public void newStormBelongsToReceivingManagerAndDuplicateSyncUpdatesIt() {
        WeatherManagerClient receivingManager = new WeatherManagerClient(RecordingWorld.create());
        ClientTickHandler.weatherManager = new WeatherManagerClient(RecordingWorld.create());

        receivingManager.nbtSyncFromServer(stormPacket(12L, 2));
        StormObject received = (StormObject) receivingManager.lookupStormObjectsByID.get(Long.valueOf(12L));

        assertEquals(1, receivingManager.getStormObjects().size());
        assertSame(receivingManager, received.manager);
        receivingManager.nbtSyncFromServer(stormPacket(12L, 3));
        assertEquals(3, received.levelCurIntensityStage);
    }

    private static NBTTagCompound stormPacket(long id, int stage) {
        NBTTagCompound packet = new NBTTagCompound();
        packet.setString("command", "syncStormNew");
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("ID", id);
        data.setInteger("layer", 0);
        data.setInteger("levelCurIntensityStage", stage);
        data.setInteger("posX", 10);
        data.setInteger("posY", 80);
        data.setInteger("posZ", 10);
        packet.setTag("data", data);
        return packet;
    }
}
