package weather2.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.util.Vec3;

import org.junit.Test;

import weather2.harness.RecordingWorld;
import weather2.util.WeatherUtilEntity;

public class WeatherExposureTest {

    private static final Vec3 POS = Vec3.createVectorHelper(0.5D, 64.0D, 0.5D);

    @Test
    public void openSkyIsWindExposed() {
        RecordingWorld world = RecordingWorld.create();

        assertTrue(WeatherUtilEntity.isPosWindExposed(world, POS));
    }

    @Test
    public void roofAndSingleOpeningAreProtected() {
        RecordingWorld world = coveredWorld();
        world.setHeightValue(0, -5, 64);

        assertFalse(WeatherUtilEntity.isPosWindExposed(world, POS));
    }

    @Test
    public void oppositeOpenPathsExposeNorthSouthAndEastWestPassages() {
        RecordingWorld northSouth = coveredWorld();
        northSouth.setHeightValue(0, -5, 64);
        northSouth.setHeightValue(0, 5, 64);
        assertTrue(WeatherUtilEntity.isPosWindExposed(northSouth, POS));

        RecordingWorld eastWest = coveredWorld();
        eastWest.setHeightValue(-5, 0, 64);
        eastWest.setHeightValue(5, 0, 64);
        assertTrue(WeatherUtilEntity.isPosWindExposed(eastWest, POS));
    }

    @Test
    public void blockedEndpointAndUnloadedChunkStayProtectedWithoutTerrainQueries() {
        RecordingWorld blocked = coveredWorld();
        blocked.setHeightValue(0, -5, 64);
        blocked.setHeightValue(0, 5, 64);
        blocked.blockRayEndpoint(0, -5);
        assertFalse(WeatherUtilEntity.isPosWindExposed(blocked, POS));

        RecordingWorld unloaded = RecordingWorld.create();
        unloaded.setChunkLoaded(false);
        assertFalse(WeatherUtilEntity.isPosWindExposed(unloaded, POS));
        assertEquals(0, unloaded.heightValueQueries);
        assertEquals(0, unloaded.rayTraceQueries);

        RecordingWorld border = RecordingWorld.create();
        border.setHeightValue(15, 0, 100);
        border.setHeightValue(15, -5, 100);
        border.setHeightValue(15, 5, 100);
        border.setChunkLoadedAt(1, 0, false);
        assertFalse(WeatherUtilEntity.isPosWindExposed(border, Vec3.createVectorHelper(15.5D, 64.0D, 0.5D)));
        assertEquals(0, border.heightQueriesInUnloadedChunks);
        assertEquals(0, border.rayTraceQueries);
    }

    @Test
    public void skyOnlyEntitiesDoNotUseSidePassages() {
        RecordingWorld world = coveredWorld();
        world.setHeightValue(0, -5, 64);
        world.setHeightValue(0, 5, 64);
        net.minecraft.entity.item.EntityItem entity = new net.minecraft.entity.item.EntityItem(world);
        entity.posX = POS.xCoord;
        entity.posY = POS.yCoord;
        entity.posZ = POS.zCoord;

        assertFalse(WeatherUtilEntity.isEntityWindExposed(entity, true));
        assertTrue(WeatherUtilEntity.isEntityWindExposed(entity, false));
    }

    private static RecordingWorld coveredWorld() {
        RecordingWorld world = RecordingWorld.create();
        world.setHeightValue(0, 0, 100);
        world.setHeightValue(0, -5, 100);
        world.setHeightValue(0, 5, 100);
        world.setHeightValue(-5, 0, 100);
        world.setHeightValue(5, 0, 100);
        return world;
    }
}
