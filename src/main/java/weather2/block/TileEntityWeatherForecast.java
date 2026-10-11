package weather2.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import weather2.ClientTickHandler;
import weather2.ServerTickHandler;
import weather2.api.WindReader;
import weather2.climate.ClimateEngine;
import weather2.climate.ClimateProfile;
import weather2.config.ConfigMisc;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

public class TileEntityWeatherForecast extends TileEntity {

    public static final byte MODE_GLOBAL = 0;
    public static final byte MODE_PLAYER = 1;
    private static final int FORECAST_INTERVAL_TICKS = 200;
    private static final long FORECAST_MAX_AGE_TICKS = 400L;

    public float smoothAngle = 0.0F;
    public float smoothSpeed = 0.0F;
    public float smoothAngleRotationalVel = 0.0F;
    public float smoothAngleRotationalVelAccel = 0.0F;
    public float smoothAngleAdj = 0.1F;
    public float smoothSpeedAdj = 0.1F;
    public float cachedCloudWindAngle = 0.0F;
    public float cachedWindAngle = 0.0F;
    public float cachedWindSpeed = 0.0F;
    public StormObject lastTickStormObject = null;
    public List storms = new ArrayList();

    public transient boolean hasForecastSnapshot;
    public transient boolean forecastModeKnown;
    public transient byte forecastMode = MODE_PLAYER;
    public transient boolean forecastEnabled;
    public transient int forecastMaxStage;
    public transient int forecastLandOdds;
    public transient int forecastOceanOdds;
    public transient int forecastProgressionOdds;
    public transient long forecastRemainingTicks = -1L;
    public transient boolean forecastCollisionNearby;
    public transient long forecastSampleWorldTick;

    @Override
    public void updateEntity() {
        if (this.worldObj == null) return;

        long worldTime = this.worldObj.getTotalWorldTime();
        if (!this.worldObj.isRemote) {
            long positionOffset = Math.floorMod((long) this.xCoord + (long) this.zCoord,
                (long) FORECAST_INTERVAL_TICKS);
            if (Math.floorMod(worldTime, (long) FORECAST_INTERVAL_TICKS) == positionOffset
                && this.updateServerForecast()) {
                this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
            }
        } else if (worldTime % FORECAST_INTERVAL_TICKS == 0L
            && ClientTickHandler.weatherManager != null
            && ClientTickHandler.weatherManager.getWorld() == this.worldObj) {
            Vec3 pos = Vec3.createVectorHelper((double) this.xCoord, (double) this.yCoord, (double) this.zCoord);
            this.cachedCloudWindAngle = WindReader.getLocalWindAngle(this.worldObj, pos, WindReader.WindType.CLOUD);
            this.cachedWindAngle = WindReader.getLocalWindAngle(this.worldObj, pos, WindReader.WindType.DOMINANT);
            this.cachedWindSpeed = WindReader.getLocalWindSpeed(this.worldObj, pos, WindReader.WindType.DOMINANT);
            this.lastTickStormObject = ClientTickHandler.weatherManager.getClosestStorm(
                Vec3.createVectorHelper(
                    (double) this.xCoord,
                    (double) ((Integer) StormObject.layers.get(0)).intValue(),
                    (double) this.zCoord),
                1024.0D,
                StormObject.STATE_THUNDER,
                true);
            this.storms = ClientTickHandler.weatherManager.getStormsAround(
                Vec3.createVectorHelper(
                    (double) this.xCoord,
                    (double) ((Integer) StormObject.layers.get(0)).intValue(),
                    (double) this.zCoord),
                1024.0D);
        }
    }

    private boolean updateServerForecast() {
        World world = this.worldObj;
        if (world == null || world.isRemote || world.provider == null) return false;

        boolean globalMode = ConfigMisc.Server_Storm_Deadly_UseGlobalRate;
        byte nextMode = globalMode ? MODE_GLOBAL : MODE_PLAYER;
        int dimension = world.provider.dimensionId;
        int cooldown = ClimateEngine.getNaturalStormCooldownTicks(dimension, globalMode);
        boolean enabled = cooldown > 0;
        boolean modeChanged = !this.forecastModeKnown || this.forecastMode != nextMode;
        Object managerObject = ServerTickHandler.lookupDimToWeatherMan == null ? null
            : ServerTickHandler.lookupDimToWeatherMan.get(Integer.valueOf(dimension));
        WeatherManagerServer manager = managerObject instanceof WeatherManagerServer
            ? (WeatherManagerServer) managerObject
            : null;

        if (enabled && globalMode && (manager == null || manager.getWorld() != world)) {
            boolean changed = this.hasForecastSnapshot || modeChanged;
            this.forecastMode = nextMode;
            this.forecastModeKnown = true;
            this.hasForecastSnapshot = false;
            this.forecastRemainingTicks = -1L;
            return changed;
        }

        ClimateProfile profile = ClimateEngine.getProfile(dimension);
        long now = world.getTotalWorldTime();
        long remaining = enabled && globalMode
            ? ClimateEngine.remainingCooldownTicks(now, manager.lastStormFormed, cooldown)
            : -1L;
        int progressionOdds = Math.max(-1, globalMode ? ConfigMisc.Server_Storm_Deadly_OddsTo1
            : ConfigMisc.Player_Storm_Deadly_OddsTo1);
        boolean collisionNearby = enabled && progressionOdds > 0 && manager != null
            && hasNearbyThermalCollision(manager.getStormObjects(), this.xCoord, this.zCoord);
        boolean changed = !this.hasForecastSnapshot || modeChanged
            || this.forecastEnabled != enabled || this.forecastMaxStage != profile.maxStage
            || this.forecastLandOdds != profile.landSpawnOdds || this.forecastOceanOdds != profile.oceanSpawnOdds
            || this.forecastProgressionOdds != progressionOdds || this.forecastRemainingTicks != remaining
            || this.forecastCollisionNearby != collisionNearby || this.forecastSampleWorldTick != now;

        this.forecastMode = nextMode;
        this.forecastModeKnown = true;
        this.forecastEnabled = enabled;
        this.forecastMaxStage = profile.maxStage;
        this.forecastLandOdds = profile.landSpawnOdds;
        this.forecastOceanOdds = profile.oceanSpawnOdds;
        this.forecastProgressionOdds = progressionOdds;
        this.forecastRemainingTicks = remaining;
        this.forecastCollisionNearby = collisionNearby;
        this.forecastSampleWorldTick = now;
        this.hasForecastSnapshot = true;
        return changed;
    }

    private boolean hasNearbyThermalCollision(List stormList, int x, int z) {
        int distance = ConfigMisc.Storm_Deadly_CollideDistance;
        if (distance <= 0) return false;
        double maxDistance = (double) distance;
        Vec3 forecastPos = Vec3.createVectorHelper((double) x + 0.5D,
            (double) StormObject.static_YPos_layer0, (double) z + 0.5D);
        for (int i = 0; i < stormList.size(); ++i) {
            StormObject storm = (StormObject) stormList.get(i);
            if (storm == null || storm.isDead || storm.tornadoEndPending || storm.pos == null
                || storm.levelTemperature == 0.0F || storm.pos.distanceTo(forecastPos) >= maxDistance) {
                continue;
            }
            for (int j = 0; j < stormList.size(); ++j) {
                StormObject candidate = (StormObject) stormList.get(j);
                if (candidate != null && candidate != storm && !candidate.isDead && !candidate.tornadoEndPending
                    && candidate.levelCurIntensityStage <= 0 && candidate.pos != null
                    && candidate.pos.distanceTo(storm.pos) < maxDistance
                    && (storm.levelTemperature < 0.0F && candidate.levelTemperature > 0.0F
                        || storm.levelTemperature > 0.0F && candidate.levelTemperature < 0.0F)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasFreshForecastSnapshot(long worldTime) {
        return this.hasForecastSnapshot && worldTime >= this.forecastSampleWorldTick
            && worldTime - this.forecastSampleWorldTick <= FORECAST_MAX_AGE_TICKS;
    }

    public boolean shouldShowForecastWaiting(long worldTime) {
        return this.forecastModeKnown && this.forecastMode == MODE_GLOBAL
            && !this.hasFreshForecastSnapshot(worldTime);
    }

    public NBTTagCompound forecastSyncData() {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("available", this.hasForecastSnapshot);
        if (!this.hasForecastSnapshot) {
            if (this.worldObj != null && !this.worldObj.isRemote) {
                data.setByte(
                    "mode",
                    ConfigMisc.Server_Storm_Deadly_UseGlobalRate ? MODE_GLOBAL : MODE_PLAYER);
            } else if (this.forecastModeKnown) {
                data.setByte("mode", this.forecastMode);
            }
            return data;
        }
        data.setByte("mode", this.forecastMode);
        data.setBoolean("enabled", this.forecastEnabled);
        data.setInteger("maxStage", this.forecastMaxStage);
        data.setInteger("landOdds", this.forecastLandOdds);
        data.setInteger("oceanOdds", this.forecastOceanOdds);
        data.setInteger("progressionOdds", this.forecastProgressionOdds);
        data.setLong("remainingTicks", this.forecastRemainingTicks);
        data.setBoolean("collisionNearby", this.forecastCollisionNearby);
        data.setLong("sampleWorldTick", this.forecastSampleWorldTick);
        return data;
    }

    public void readForecastSyncData(NBTTagCompound data) {
        if (data == null || !data.hasKey("available", 1)) {
            this.clearForecastSnapshot();
            return;
        }
        byte available = data.getByte("available");
        if (available < 0 || available > 1) return;
        if (available == 0) {
            this.hasForecastSnapshot = false;
            if (data.hasKey("mode", 1) && isValidMode(data.getByte("mode"))) {
                this.forecastMode = data.getByte("mode");
                this.forecastModeKnown = true;
            } else {
                this.forecastModeKnown = false;
            }
            return;
        }
        if (!data.hasKey("mode", 1) || !data.hasKey("enabled", 1) || !data.hasKey("maxStage", 3)
            || !data.hasKey("landOdds", 3) || !data.hasKey("oceanOdds", 3)
            || !data.hasKey("progressionOdds", 3) || !data.hasKey("remainingTicks", 4)
            || !data.hasKey("collisionNearby", 1) || !data.hasKey("sampleWorldTick", 4)) {
            return;
        }
        byte mode = data.getByte("mode");
        byte enabled = data.getByte("enabled");
        byte collisionNearby = data.getByte("collisionNearby");
        int maxStage = data.getInteger("maxStage");
        int landOdds = data.getInteger("landOdds");
        int oceanOdds = data.getInteger("oceanOdds");
        int progressionOdds = data.getInteger("progressionOdds");
        long remaining = data.getLong("remainingTicks");
        long sampleTick = data.getLong("sampleWorldTick");
        if (!isValidMode(mode) || enabled < 0 || enabled > 1 || collisionNearby < 0 || collisionNearby > 1
            || maxStage < 0 || maxStage > 9 || landOdds < 0 || oceanOdds < 0
            || progressionOdds < -1 || remaining < -1L || remaining > Integer.MAX_VALUE || sampleTick < 0L) {
            return;
        }

        this.forecastMode = mode;
        this.forecastModeKnown = true;
        this.forecastEnabled = enabled == 1;
        this.forecastMaxStage = maxStage;
        this.forecastLandOdds = landOdds;
        this.forecastOceanOdds = oceanOdds;
        this.forecastProgressionOdds = progressionOdds;
        this.forecastRemainingTicks = remaining;
        this.forecastCollisionNearby = collisionNearby == 1;
        this.forecastSampleWorldTick = sampleTick;
        this.hasForecastSnapshot = true;
    }

    private static boolean isValidMode(byte mode) {
        return mode == MODE_GLOBAL || mode == MODE_PLAYER;
    }

    private void clearForecastSnapshot() {
        this.hasForecastSnapshot = false;
        this.forecastModeKnown = false;
        this.forecastMode = MODE_PLAYER;
        this.forecastEnabled = false;
        this.forecastMaxStage = 0;
        this.forecastLandOdds = 0;
        this.forecastOceanOdds = 0;
        this.forecastProgressionOdds = 0;
        this.forecastRemainingTicks = -1L;
        this.forecastCollisionNearby = false;
        this.forecastSampleWorldTick = 0L;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.clearForecastSnapshot();
    }

    @Override
    public Packet getDescriptionPacket() {
        if (this.worldObj != null && !this.worldObj.isRemote) this.updateServerForecast();
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("forecast", this.forecastSyncData());
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        if (packet.func_148856_c() != this.xCoord || packet.func_148855_d() != this.yCoord
            || packet.func_148854_e() != this.zCoord) {
            return;
        }
        NBTTagCompound nbt = packet.func_148857_g();
        if (nbt == null || !nbt.hasKey("forecast", 10)) {
            this.clearForecastSnapshot();
            return;
        }
        this.readForecastSyncData(nbt.getCompoundTag("forecast"));
    }
}
