package weather2.util;

import cpw.mods.fml.client.FMLClientHandler;

public final class WeatherUtil {

    private WeatherUtil() {}

    public static boolean isPaused() {
        return FMLClientHandler.instance()
            .getClient()
            .isGamePaused();
    }
}
