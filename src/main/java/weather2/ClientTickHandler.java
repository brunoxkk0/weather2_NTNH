package weather2;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import CoroUtil.packet.PacketHelper;
import cpw.mods.fml.client.FMLClientHandler;
import weather2.client.SceneEnhancer;
import weather2.client.gui.GuiEZConfig;
import weather2.config.ConfigMisc;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.EntityRendererProxyWeather2Mini;
import weather2.weathersystem.WeatherManagerClient;

public class ClientTickHandler {

    public static volatile World lastWorld;
    public static volatile WeatherManagerClient weatherManager;
    public static SceneEnhancer sceneEnhancer;
    private static boolean needsWeatherSync;
    public boolean hasOpenedConfig = false;
    public GuiButton configButton;

    public ClientTickHandler() {
        if (sceneEnhancer == null) {
            sceneEnhancer = new SceneEnhancer();
            (new Thread(sceneEnhancer, "Weather2 Scene Enhancer")).start();
        }

    }

    public void onRenderScreenTick() {}

    public void onTickInGUI(GuiScreen guiscreen) {}

    public void onTickInGame() {
        Minecraft mc = FMLClientHandler.instance()
            .getClient();
        WorldClient world = mc.theWorld;
        if (ConfigMisc.Misc_proxyRenderOverrideEnabled) {
            if (!(mc.entityRenderer instanceof EntityRendererProxyWeather2Mini)) {
                EntityRendererProxyWeather2Mini msg = new EntityRendererProxyWeather2Mini(mc, mc.getResourceManager());
                mc.entityRenderer = msg;
            }
        } else if (mc.entityRenderer instanceof EntityRendererProxyWeather2Mini) {
            mc.entityRenderer = new EntityRenderer(mc, mc.getResourceManager());
        }

        if (world != null && world != lastWorld) {
            init(world);
        } else if (world == null && lastWorld != null) {
            clearClientWorld(null);
        }

        if (world != null && weatherManager != null) {
            weatherManager.tick();
            requestInitialWeatherSync(mc);
            if (ConfigMisc.Misc_ForceVanillaCloudsOff && world.provider.dimensionId == 0) {
                mc.gameSettings.clouds = false;
            }
        }

        if (world != null && weatherManager != null && sceneEnhancer != null
            && WeatherUtilConfig.listDimensionsWindEffects.contains(Integer.valueOf(world.provider.dimensionId))) {
            sceneEnhancer.tickClient();
        }

        if (world != null && mc.ingameGUI.getChatGUI()
            .getSentMessages()
            .size() > 0) {
            String msg1 = (String) mc.ingameGUI.getChatGUI()
                .getSentMessages()
                .get(
                    mc.ingameGUI.getChatGUI()
                        .getSentMessages()
                        .size() - 1);
            if (msg1.equals("/weather2 config")) {
                mc.ingameGUI.getChatGUI()
                    .getSentMessages()
                    .remove(
                        mc.ingameGUI.getChatGUI()
                            .getSentMessages()
                            .size() - 1);
                mc.displayGuiScreen(new GuiEZConfig());
            }
        }

    }

    public static void checkClientWeather() {
        try {
            checkClientWeather(
                FMLClientHandler.instance()
                    .getClient().theWorld);
        } catch (Exception var1) {
            System.out.println(
                "Warning, Weather2 weatherManager init failed for some reason, printing stacktrace, report to https://github.com/Corosauce/weather2/issues");
            var1.printStackTrace();
        }

    }

    static void checkClientWeather(World world) {
        if (world != null && (weatherManager == null || lastWorld != world)) {
            setClientWorld(world);
        }
    }

    public static void init(World world) {
        Weather.dbg("Initializing WeatherManagerClient for client world");
        setClientWorld(world);
    }

    private static void setClientWorld(World world) {
        if (lastWorld != null && lastWorld != world) {
            if (weatherManager != null) {
                weatherManager.reset();
            }
            if (sceneEnhancer != null) {
                sceneEnhancer.setWorld(world);
            }
        } else if (sceneEnhancer != null) {
            sceneEnhancer.setWorld(world);
        }
        lastWorld = world;
        weatherManager = new WeatherManagerClient(world);
        needsWeatherSync = true;
    }

    static void clearClientWorld(World world) {
        if (world != null && world != lastWorld) {
            return;
        }
        if (weatherManager != null) {
            weatherManager.reset();
        }
        if (sceneEnhancer != null) {
            sceneEnhancer.setWorld(null);
        }
        weatherManager = null;
        lastWorld = null;
        needsWeatherSync = false;
    }

    private static void requestInitialWeatherSync(Minecraft mc) {
        if (!needsWeatherSync || mc.thePlayer == null || mc.getNetHandler() == null) {
            return;
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setString("command", "syncRequest");
        data.setInteger("dimensionId", mc.theWorld.provider.dimensionId);
        Weather.eventChannel.sendToServer(PacketHelper.getNBTPacket(data, Weather.eventChannelName));
        needsWeatherSync = false;
    }

    static void scheduleWeatherData(final Object handler, final NBTTagCompound data) {
        final Minecraft mc = Minecraft.getMinecraft();
        final World packetWorld = mc.theWorld;
        if (handler == null || packetWorld == null) {
            return;
        }
        mc.func_152344_a(new Runnable() {
            @Override
            public void run() {
                if (isCurrentWeatherData(handler, mc.getNetHandler(), packetWorld, mc.theWorld)) {
                    checkClientWeather(packetWorld);
                    applyWeatherData(packetWorld, data);
                }
            }
        });
    }

    static boolean isCurrentWeatherData(Object packetHandler, Object currentHandler, World packetWorld,
        World currentWorld) {
        return packetHandler != null && packetHandler == currentHandler && packetWorld != null
            && packetWorld == currentWorld;
    }

    static void applyWeatherData(World world, NBTTagCompound data) {
        if (world == null || world != lastWorld || weatherManager == null
            || weatherManager.dim != world.provider.dimensionId
            || data.hasKey("dimensionId") && data.getInteger("dimensionId") != world.provider.dimensionId) {
            return;
        }
        weatherManager.nbtSyncFromServer(data);
    }

    static void getField(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & -17);
        field.set((Object) null, newValue);
    }
}
