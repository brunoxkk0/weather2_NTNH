package weather2.api;

import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import weather2.ClientTickHandler;
import weather2.ServerTickHandler;
import weather2.config.ConfigMisc;
import weather2.weathersystem.WeatherManagerBase;

public class WindReader {

    public static float getWindAngle(World parWorld, Vec3 parLocation) {
        return getWindAngle(parWorld, parLocation, WindReader.WindType.DOMINANT);
    }

    public static float getWindAngle(World parWorld, Vec3 parLocation, WindReader.WindType parWindType) {
        WeatherManagerBase wMan = null;
        if (parWorld.isRemote) {
            wMan = getWeatherManagerClient();
        } else {
            wMan = (WeatherManagerBase) ServerTickHandler.lookupDimToWeatherMan
                .get(Integer.valueOf(parWorld.provider.dimensionId));
        }

        return wMan != null
            ? (parWindType == WindReader.WindType.DOMINANT ? wMan.windMan.getWindAngleForPriority()
                : (parWindType == WindReader.WindType.EVENT ? wMan.windMan.getWindAngleForEvents()
                    : (parWindType == WindReader.WindType.GUST ? wMan.windMan.getWindAngleForGusts()
                        : (parWindType == WindReader.WindType.CLOUD ? wMan.windMan.getWindAngleForClouds() : 0.0F))))
            : 0.0F;
    }

    public static float getWindSpeed(World parWorld, Vec3 parLocation) {
        return getWindSpeed(parWorld, parLocation, WindReader.WindType.DOMINANT);
    }

    public static float getWindSpeed(World parWorld, Vec3 parLocation, WindReader.WindType parWindType) {
        WeatherManagerBase wMan = null;
        if (parWorld.isRemote) {
            wMan = getWeatherManagerClient();
        } else {
            wMan = (WeatherManagerBase) ServerTickHandler.lookupDimToWeatherMan
                .get(Integer.valueOf(parWorld.provider.dimensionId));
        }

        return wMan != null
            ? (parWindType == WindReader.WindType.DOMINANT ? wMan.windMan.getWindSpeedForPriority()
                : (parWindType == WindReader.WindType.EVENT ? wMan.windMan.getWindSpeedForEvents()
                    : (parWindType == WindReader.WindType.GUST ? wMan.windMan.getWindSpeedForGusts()
                        : (parWindType == WindReader.WindType.CLOUD ? wMan.windMan.getWindSpeedForClouds() : 0.0F))))
            : 0.0F;
    }

    public static float getLocalWindAngle(World world, Vec3 pos, WindType type) {
        if (world == null) {
            return 0.0F;
        }
        if (pos == null) {
            return getWindAngle(world, null, type);
        }
        if (!ConfigMisc.Misc_windOn || !isFinite(pos)) {
            return 0.0F;
        }
        WeatherManagerBase manager = getWeatherManager(world);
        return manager != null && manager.windMan != null ? manager.windMan.getLocalWindAngle(pos, type) : 0.0F;
    }

    public static float getLocalWindSpeed(World world, Vec3 pos, WindType type) {
        if (world == null) {
            return 0.0F;
        }
        if (pos == null) {
            return getWindSpeed(world, null, type);
        }
        if (!ConfigMisc.Misc_windOn || !isFinite(pos)) {
            return 0.0F;
        }
        WeatherManagerBase manager = getWeatherManager(world);
        return manager != null && manager.windMan != null ? manager.windMan.getLocalWindSpeed(world, pos, type) : 0.0F;
    }

    private static WeatherManagerBase getWeatherManager(World world) {
        if (world.provider == null) {
            return null;
        }
        WeatherManagerBase manager = world.isRemote ? getWeatherManagerClient()
            : (WeatherManagerBase) ServerTickHandler.lookupDimToWeatherMan
                .get(Integer.valueOf(world.provider.dimensionId));
        return manager != null && manager.dim == world.provider.dimensionId && manager.getWorld() == world ? manager
            : null;
    }

    private static boolean isFinite(Vec3 pos) {
        return !Double.isNaN(pos.xCoord) && !Double.isInfinite(pos.xCoord) && !Double.isNaN(pos.yCoord)
            && !Double.isInfinite(pos.yCoord) && !Double.isNaN(pos.zCoord) && !Double.isInfinite(pos.zCoord);
    }

    @SideOnly(Side.CLIENT)
    private static WeatherManagerBase getWeatherManagerClient() {
        return ClientTickHandler.weatherManager;
    }

    public static enum WindType {

        DOMINANT("DOMINANT", 0),
        EVENT("EVENT", 1),
        GUST("GUST", 2),
        CLOUD("CLOUD", 3);

        // $FF: synthetic field
        private static final WindReader.WindType[] $VALUES = new WindReader.WindType[] { DOMINANT, EVENT, GUST, CLOUD };

        private WindType(String var1, int var2) {}

    }
}
