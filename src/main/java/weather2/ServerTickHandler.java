package weather2;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.google.common.collect.ImmutableList;

import CoroUtil.util.CoroUtilFile;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLInterModComms.IMCMessage;
import weather2.config.ConfigMisc;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerBase;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

public class ServerTickHandler {

    public static ArrayList listWeatherMans = new ArrayList();
    public static HashMap lookupDimToWeatherMan = new HashMap();
    public static World lastWorld;
    public static NBTTagCompound worldNBT = new NBTTagCompound();
    private static volatile Thread serverThread;
    private static boolean savedStormIdsScanned;
    private static long savedStormIdsRetryTick;
    private static long syncRequestTick;
    private static final Set<EntityPlayerMP> pendingWeatherSync = Collections
        .newSetFromMap(new ConcurrentHashMap<EntityPlayerMP, Boolean>());
    private static final Map<EntityPlayerMP, SyncRequestState> lastWeatherSync = new ConcurrentHashMap<EntityPlayerMP, SyncRequestState>();

    private static final class SyncRequestState {
        private final int dimension;
        private final long tick;

        private SyncRequestState(int dimension, long tick) {
            this.dimension = dimension;
            this.tick = tick;
        }
    }

    public static boolean isOnServerThread() {
        return serverThread != null && serverThread == Thread.currentThread();
    }

    static void writeWeatherData() {
        for (Object manager : lookupDimToWeatherMan.values()) {
            ((WeatherManagerServer) manager).writeToFile();
        }
    }

    public static void onTickInGame() {
        if (FMLCommonHandler.instance() != null && FMLCommonHandler.instance()
            .getMinecraftServerInstance() != null) {
            if (serverThread == null) {
                serverThread = Thread.currentThread();
            }
            ++syncRequestTick;
            WorldServer world = FMLCommonHandler.instance()
                .getMinecraftServerInstance()
                .worldServerForDimension(0);
            boolean canStartWeather = savedStormIdsScanned;
            if (world != null && !savedStormIdsScanned) {
                File saveFolder = new File(
                    CoroUtilFile.getWorldSaveFolderPath() + CoroUtilFile.getWorldFolderName()
                        + "weather2"
                        + File.separator);
                canStartWeather = scanSavedStormIdsIfNeeded(saveFolder, world.getTotalWorldTime());
            }
            if (world != null && lastWorld != world) {
                lastWorld = world;
            }

            if (world != null && world.getTotalWorldTime() % (long) ConfigMisc.Misc_AutoDataSaveIntervalInTicks == 0L) {
                Weather.writeOutData(false);
            }

            if (!canStartWeather) {
                return;
            }

            WorldServer[] worlds = DimensionManager.getWorlds();

            for (int testRainRequest = 0; testRainRequest < worlds.length; ++testRainRequest) {
                if (!lookupDimToWeatherMan.containsKey(Integer.valueOf(worlds[testRainRequest].provider.dimensionId))
                    && WeatherUtilConfig.listDimensionsWeather
                        .contains(Integer.valueOf(worlds[testRainRequest].provider.dimensionId))) {
                    addWorldToWeather(worlds[testRainRequest].provider.dimensionId);
                }

                WeatherManagerServer debugIMC = (WeatherManagerServer) lookupDimToWeatherMan
                    .get(Integer.valueOf(worlds[testRainRequest].provider.dimensionId));
                if (debugIMC != null) {
                    ((WeatherManagerServer) lookupDimToWeatherMan
                        .get(Integer.valueOf(worlds[testRainRequest].provider.dimensionId))).tick();
                }
            }

            processWeatherSyncRequests();

            boolean var8 = false;
            if (var8) {
                new ArrayList();
                ImmutableList var9 = FMLInterModComms.fetchRuntimeMessages(Weather.modID);

                for (int ex = 0; ex < var9.size(); ++ex) {
                    if (((IMCMessage) var9.get(ex)).key.equals("weather.raining")) {
                        NBTTagCompound i = ((IMCMessage) var9.get(ex)).getNBTValue();
                        String replyMod = i.getString("replymod");
                        i.setBoolean("isRaining", true);
                        FMLInterModComms.sendRuntimeMessage(replyMod, replyMod, "weather.raining", i);
                    }
                }
            }

            boolean var10 = false;
            if (var10) {
                try {
                    new ArrayList();
                    ImmutableList var11 = FMLInterModComms.fetchRuntimeMessages(Weather.modID);

                    for (int var12 = 0; var12 < var11.size(); ++var12) {
                        ;
                    }
                } catch (Exception var7) {
                    var7.printStackTrace();
                }
            }

        }
    }

    static boolean scanSavedStormIdsIfNeeded(File directory, long worldTime) {
        if (savedStormIdsScanned) {
            return true;
        }
        if (worldTime < savedStormIdsRetryTick) {
            return false;
        }

        savedStormIdsScanned = scanSavedStormIds(directory);
        if (!savedStormIdsScanned) {
            savedStormIdsRetryTick = worldTime + 200L;
            System.err.println(
                "Weather2 is waiting for saved storm IDs to be readable before starting weather managers; retrying in 200 ticks.");
        }
        return savedStormIdsScanned;
    }

    static void resetSavedStormIdsScan() {
        savedStormIdsScanned = false;
        savedStormIdsRetryTick = 0L;
    }

    static boolean scanSavedStormIds(File directory) {
        File[] files;
        try {
            if (!directory.exists()) {
                File parent = directory.getParentFile();
                if (parent == null) {
                    return false;
                }
                File[] siblings = parent.listFiles();
                if (siblings == null) {
                    return false;
                }
                for (int i = 0; i < siblings.length; ++i) {
                    if (siblings[i].getName()
                        .equals(directory.getName())) {
                        return false;
                    }
                }
                return true;
            }
            files = directory.listFiles();
        } catch (SecurityException exception) {
            exception.printStackTrace();
            return false;
        }
        if (files == null) {
            return false;
        }

        boolean success = true;
        for (int i = 0; i < files.length; ++i) {
            File file = files[i];
            String name = file.getName();
            if (!name.startsWith("WeatherData_") || !name.endsWith(".dat")) {
                continue;
            }

            try {
                if (!file.isFile()) {
                    success = false;
                    continue;
                }

                try (FileInputStream input = new FileInputStream(file)) {
                    StormObject.observeSavedStormIds(CompressedStreamTools.readCompressed(input));
                }
            } catch (Exception exception) {
                exception.printStackTrace();
                success = false;
            }
        }
        return success;
    }

    public static void addWorldToWeather(int dim) {
        if (!savedStormIdsScanned) {
            return;
        }
        Weather.dbg("Registering Weather2 manager for dim: " + dim);
        WeatherManagerServer wm = new WeatherManagerServer(dim);
        listWeatherMans.add(wm);
        lookupDimToWeatherMan.put(Integer.valueOf(dim), wm);
        wm.readFromFile();
    }

    public static void removeWorldFromWeather(int dim) {
        Weather.dbg("Deregistering Weather2 manager for dim: " + dim);
        WeatherManagerServer wm = (WeatherManagerServer) lookupDimToWeatherMan.get(Integer.valueOf(dim));
        if (wm != null) {
            wm.writeToFile();
            listWeatherMans.remove(wm);
            lookupDimToWeatherMan.remove(Integer.valueOf(dim));
        }
    }

    public static void playerJoinedServerSyncFull(EntityPlayerMP entP) {
        WeatherManagerServer wm = (WeatherManagerServer) lookupDimToWeatherMan
            .get(Integer.valueOf(entP.worldObj.provider.dimensionId));
        if (wm != null) {
            wm.playerJoinedServerSyncFull(entP);
        }

    }

    public static void requestWeatherSync(EntityPlayerMP player) {
        if (player != null) {
            pendingWeatherSync.add(player);
        }
    }

    static void processWeatherSyncRequests() {
        EntityPlayerMP[] requests = pendingWeatherSync.toArray(new EntityPlayerMP[pendingWeatherSync.size()]);
        for (int i = 0; i < requests.length; ++i) {
            EntityPlayerMP player = requests[i];
            if (player.worldObj == null || player.isDead) {
                pendingWeatherSync.remove(player);
                lastWeatherSync.remove(player);
                continue;
            }

            int dimension = player.worldObj.provider.dimensionId;
            if (!WeatherUtilConfig.listDimensionsWeather.contains(Integer.valueOf(dimension))) {
                pendingWeatherSync.remove(player);
                continue;
            }
            WeatherManagerServer manager = (WeatherManagerServer) lookupDimToWeatherMan.get(Integer.valueOf(dimension));
            if (manager == null) {
                continue;
            }

            SyncRequestState previous = lastWeatherSync.get(player);
            if (previous != null && !canAcceptWeatherSync(syncRequestTick, dimension, previous.dimension, previous.tick)) {
                pendingWeatherSync.remove(player);
                continue;
            }
            pendingWeatherSync.remove(player);
            lastWeatherSync.put(player, new SyncRequestState(dimension, syncRequestTick));
            manager.playerJoinedServerSyncFull(player);
        }
    }

    static boolean canAcceptWeatherSync(long tick, int dimension, int previousDimension, long previousTick) {
        return dimension != previousDimension || tick - previousTick >= 100L;
    }

    public static void removeWeatherSyncRequest(EntityPlayerMP player) {
        if (player != null) {
            pendingWeatherSync.remove(player);
            lastWeatherSync.remove(player);
        }
    }

    public static void initialize() {
        weather2.integration.IntegrationManager.logDiagnosticSummary();
        if (lookupDimToWeatherMan.get(Integer.valueOf(0)) == null) {
            addWorldToWeather(0);
        }

    }

    public static void reset() {
        ArrayList managers = new ArrayList(listWeatherMans);
        for (int i = 0; i < managers.size(); ++i) {
            WeatherManagerBase wm = (WeatherManagerBase) managers.get(i);
            int dim = wm.dim;
            if (lookupDimToWeatherMan.containsKey(Integer.valueOf(dim))) {
                removeWorldFromWeather(dim);
            }
        }

        if (listWeatherMans.size() > 0 || lookupDimToWeatherMan.size() > 0) {
            Weather.dbg(
                "Weather2 reset state failed to manually clear lists, listWeatherMans.size(): " + listWeatherMans.size()
                    + " - lookupDimToWeatherMan.size(): "
                    + lookupDimToWeatherMan.size()
                    + " - forcing a full clear of lists");
            listWeatherMans.clear();
            lookupDimToWeatherMan.clear();
        }

        StormObject.lastUsedStormID = 0L;
        resetSavedStormIdsScan();
        pendingWeatherSync.clear();
        lastWeatherSync.clear();
        syncRequestTick = 0L;
        serverThread = null;

    }

}
