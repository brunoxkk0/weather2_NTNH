package weather2.block;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import org.junit.Test;

import weather2.config.ConfigMisc;
import weather2.harness.RecordingWorld;

public class ForecastSyncTest {

    static {
        TileEntity.addMapping(TileEntityWeatherForecast.class, "WeatherForecast");
    }

    @Test
    public void descriptionPacketCarriesServerSnapshotAndCoordinates() {
        TileEntityWeatherForecast server = forecast(4, 65, -7);
        fillSnapshot(server);

        S35PacketUpdateTileEntity packet = (S35PacketUpdateTileEntity) server.getDescriptionPacket();
        TileEntityWeatherForecast client = forecast(4, 65, -7);
        ConfigMisc.Server_Storm_Deadly_UseGlobalRate = false;
        client.onDataPacket(null, packet);

        assertTrue(client.hasForecastSnapshot);
        assertEquals(TileEntityWeatherForecast.MODE_GLOBAL, client.forecastMode);
        assertTrue(client.forecastEnabled);
        assertEquals(75L, client.forecastRemainingTicks);
        assertEquals(8, client.forecastMaxStage);
        assertEquals(12, client.forecastLandOdds);
        assertEquals(1000L, client.forecastSampleWorldTick);
    }

    @Test
    public void packetForDifferentCoordinatesAndMalformedSnapshotAreIgnored() {
        TileEntityWeatherForecast server = forecast(1, 2, 3);
        fillSnapshot(server);
        NBTTagCompound wrongPosition = new NBTTagCompound();
        wrongPosition.setTag("forecast", server.forecastSyncData());
        TileEntityWeatherForecast wrongPositionClient = forecast(9, 2, 3);
        wrongPositionClient.onDataPacket(null, new S35PacketUpdateTileEntity(1, 2, 3, 0, wrongPosition));
        assertFalse(wrongPositionClient.hasForecastSnapshot);

        NBTTagCompound invalid = server.forecastSyncData();
        invalid.setByte("mode", (byte) 7);
        TileEntityWeatherForecast invalidClient = forecast(1, 2, 3);
        invalidClient.readForecastSyncData(invalid);
        assertFalse(invalidClient.hasForecastSnapshot);

        NBTTagCompound invalidStage = server.forecastSyncData();
        invalidStage.setInteger("maxStage", 10);
        TileEntityWeatherForecast invalidStageClient = forecast(1, 2, 3);
        invalidStageClient.readForecastSyncData(invalidStage);
        assertFalse(invalidStageClient.hasForecastSnapshot);
    }

    @Test
    public void snapshotFreshnessUsesCurrentWorldTimeAndSaveNbtStaysTransient() {
        TileEntityWeatherForecast forecast = forecast(0, 64, 0);
        fillSnapshot(forecast);
        assertFalse(forecast.hasFreshForecastSnapshot(999L));
        assertTrue(forecast.hasFreshForecastSnapshot(1400L));
        assertFalse(forecast.hasFreshForecastSnapshot(1401L));
        assertFalse(forecast.shouldShowForecastWaiting(1400L));
        assertTrue(forecast.shouldShowForecastWaiting(1401L));

        NBTTagCompound save = new NBTTagCompound();
        forecast.writeToNBT(save);
        assertFalse(save.hasKey("forecast"));
        assertFalse(save.hasKey("forecastMode"));
        assertFalse(save.hasKey("forecastSampleWorldTick"));

        TileEntityWeatherForecast empty = forecast(0, 64, 0);
        assertFalse(empty.hasFreshForecastSnapshot(1000L));
        assertFalse(empty.shouldShowForecastWaiting(1000L));
        empty.forecastMode = TileEntityWeatherForecast.MODE_GLOBAL;
        empty.forecastModeKnown = true;
        assertTrue(empty.shouldShowForecastWaiting(1000L));
        empty.forecastMode = TileEntityWeatherForecast.MODE_PLAYER;
        assertFalse(empty.shouldShowForecastWaiting(1000L));

        NBTTagCompound waitingData = new NBTTagCompound();
        waitingData.setBoolean("available", false);
        waitingData.setByte("mode", TileEntityWeatherForecast.MODE_GLOBAL);
        empty.readForecastSyncData(waitingData);
        assertTrue(empty.shouldShowForecastWaiting(1000L));
    }

    private static TileEntityWeatherForecast forecast(int x, int y, int z) {
        TileEntityWeatherForecast forecast = new TileEntityWeatherForecast();
        RecordingWorld.setField(forecast, "xCoord", Integer.valueOf(x));
        RecordingWorld.setField(forecast, "yCoord", Integer.valueOf(y));
        RecordingWorld.setField(forecast, "zCoord", Integer.valueOf(z));
        return forecast;
    }

    private static void fillSnapshot(TileEntityWeatherForecast forecast) {
        forecast.hasForecastSnapshot = true;
        forecast.forecastModeKnown = true;
        forecast.forecastMode = TileEntityWeatherForecast.MODE_GLOBAL;
        forecast.forecastEnabled = true;
        forecast.forecastMaxStage = 8;
        forecast.forecastLandOdds = 12;
        forecast.forecastOceanOdds = 5;
        forecast.forecastProgressionOdds = 30;
        forecast.forecastRemainingTicks = 75L;
        forecast.forecastCollisionNearby = true;
        forecast.forecastSampleWorldTick = 1000L;
    }
}
