package weather2.weathersystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;

import CoroUtil.packet.PacketHelper;
import CoroUtil.util.CoroUtilEntity;
import cpw.mods.fml.common.event.FMLInterModComms;
import weather2.Weather;
import weather2.api.TornadoOptions;
import weather2.api.TornadoSnapshot;
import weather2.api.event.TornadoEndReason;
import weather2.api.event.TornadoEndedEvent;
import weather2.api.event.TornadoFormEvent;
import weather2.api.event.TornadoOrigin;
import weather2.config.ConfigMisc;
import weather2.entity.EntityLightningBolt;
import weather2.util.WeatherUtilConfig;
import weather2.volcano.VolcanoObject;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.wind.WindManager;

public class WeatherManagerServer extends WeatherManagerBase {

    public int syncRange = 256;
    private int tickerSyncWeatherCheckVanilla = 0;
    private int tickerSyncWeatherLowWind = 0;
    private int tickerSyncWeatherHighWind = 0;
    private int tickerSyncWindAndIMC = 0;
    private int tickerSyncStormSpawnOrRemoveChecks = 0;
    private boolean tornadoEventDispatching;

    public WeatherManagerServer(int parDim) {
        super(parDim);
    }

    public World getWorld() {
        return DimensionManager.getWorld(this.dim);
    }

    public void tick() {
        World world = this.getWorld();
        if (world != null) {
            this.tickTornadoDurations();
            this.processPendingTornadoEnds();
        }
        super.tick();
        ++this.tickerSyncWeatherCheckVanilla;
        ++this.tickerSyncWeatherLowWind;
        ++this.tickerSyncWeatherHighWind;
        ++this.tickerSyncWindAndIMC;
        ++this.tickerSyncStormSpawnOrRemoveChecks;
        if (world != null) {
            if (!ConfigMisc.overcastMode && ConfigMisc.lockServerWeatherMode != -1) {
                world.getWorldInfo()
                    .setRaining(ConfigMisc.lockServerWeatherMode == 1);
                world.getWorldInfo()
                    .setThundering(ConfigMisc.lockServerWeatherMode == 1);
            }

            if (ConfigMisc.preventServerThunderstorms) {
                world.getWorldInfo()
                    .setThundering(false);
            }

            // NTNH start: immediate detection & sync of vanilla /weather rain, thunder, and clear transitions
            boolean curRain = world.isRaining();
            boolean curThunder = world.isThundering();
            boolean rainStarted = curRain && !this.isVanillaRainActiveOnServer;
            boolean thunderStarted = curThunder && !this.isVanillaThunderActiveOnServer;
            boolean rainStopped = !curRain && this.isVanillaRainActiveOnServer;
            if (curRain != this.isVanillaRainActiveOnServer || curThunder != this.isVanillaThunderActiveOnServer
                || this.tickerSyncWeatherCheckVanilla >= ConfigMisc.tickerRateSyncWeatherCheckVanilla) {
                this.isVanillaRainActiveOnServer = curRain;
                this.isVanillaThunderActiveOnServer = curThunder;
                this.syncWeatherVanilla();
                this.tickerSyncWeatherCheckVanilla = 0;
            }

            if ((rainStarted || thunderStarted)
                && WeatherUtilConfig.listDimensionsClouds.contains(Integer.valueOf(world.provider.dimensionId))) {
                HashSet updatedStorms = new HashSet();
                for (int pIdx = 0; pIdx < world.playerEntities.size(); ++pIdx) {
                    EntityPlayer entP = (EntityPlayer) world.playerEntities.get(pIdx);
                    Vec3 playerSkyPos = Vec3
                        .createVectorHelper(entP.posX, (double) StormObject.static_YPos_layer0, entP.posZ);
                    StormObject so = this.getClosestStormAny(playerSkyPos, (double) ConfigMisc.Misc_simBoxRadiusCutoff);
                    if (so == null) {
                        so = new StormObject(this);
                        so.initFirstTime();
                        so.pos = Vec3.createVectorHelper(entP.posX, (double) StormObject.static_YPos_layer0, entP.posZ);
                        so.posGround = Vec3.createVectorHelper(entP.posX, entP.posY, entP.posZ);
                        so.size = Math.max(so.size, 150);
                        so.layer = 0;
                        so.userSpawnedFor = CoroUtilEntity.getName(entP);
                        so.levelWater = Math.max(so.levelWater, so.levelWaterStartRaining * 2);
                        so.setPrecipitating(true);
                        so.hasStormPeaked = false;
                        if (curThunder && so.levelCurIntensityStage < StormObject.STATE_THUNDER) {
                            so.levelCurIntensityStage = StormObject.STATE_THUNDER;
                            so.maxIntensityStage = StormObject.STATE_THUNDER;
                        }
                        this.addStormObject(so);
                        this.syncStormNew(so);
                    } else {
                        if (so.pos.distanceTo(playerSkyPos) > (double) (so.size / 2)) {
                            so.pos = Vec3
                                .createVectorHelper(entP.posX, (double) StormObject.static_YPos_layer0, entP.posZ);
                            so.posGround = Vec3.createVectorHelper(entP.posX, entP.posY, entP.posZ);
                        }
                        so.size = Math.max(so.size, 150);
                        so.levelWater = Math.max(so.levelWater, so.levelWaterStartRaining * 2);
                        so.setPrecipitating(true);
                        so.hasStormPeaked = false;
                        if (curThunder && so.levelCurIntensityStage < StormObject.STATE_THUNDER) {
                            so.levelCurIntensityStage = StormObject.STATE_THUNDER;
                            so.maxIntensityStage = StormObject.STATE_THUNDER;
                        }
                        updatedStorms.add(so.nbtSyncForClient());
                    }
                }
                if (updatedStorms.size() > 0) {
                    this.syncStormUpdate(updatedStorms);
                }
            } else if (rainStopped) {
                HashSet clearedStorms = new HashSet();
                for (int sIdx = 0; sIdx < this.getStormObjects()
                    .size(); ++sIdx) {
                    StormObject so = (StormObject) this.getStormObjects()
                        .get(sIdx);
                    if (so.attrib_precipitation || so.levelCurIntensityStage == StormObject.STATE_THUNDER) {
                        so.setPrecipitating(false);
                        so.levelWater = 0;
                        if (so.levelCurIntensityStage == StormObject.STATE_THUNDER) {
                            so.setNoStorm();
                        }
                        clearedStorms.add(so.nbtSyncForClient());
                    }
                }
                if (clearedStorms.size() > 0) {
                    this.syncStormUpdate(clearedStorms);
                }
            } else if (curRain && this.tickerSyncWeatherLowWind == 1
                && WeatherUtilConfig.listDimensionsClouds.contains(Integer.valueOf(world.provider.dimensionId))) {
                    for (int pIdx = 0; pIdx < world.playerEntities.size(); ++pIdx) {
                        EntityPlayer entP = (EntityPlayer) world.playerEntities.get(pIdx);
                        Vec3 playerSkyPos = Vec3
                            .createVectorHelper(entP.posX, (double) StormObject.static_YPos_layer0, entP.posZ);
                        StormObject activeThunderStorm = null;
                        for (int sIdx = 0; sIdx < this.getStormObjects()
                            .size(); ++sIdx) {
                            StormObject candidate = (StormObject) this.getStormObjects()
                                .get(sIdx);
                            if (!candidate.isDead && candidate.layer == 0
                                && candidate.levelCurIntensityStage >= StormObject.STATE_THUNDER
                                && candidate.pos.distanceTo(playerSkyPos)
                                    <= (double) ConfigMisc.Misc_simBoxRadiusCutoff) {
                                activeThunderStorm = candidate;
                                break;
                            }
                        }
                        StormObject so = activeThunderStorm != null ? activeThunderStorm
                            : this.getClosestStormAny(playerSkyPos, (double) ConfigMisc.Misc_simBoxRadiusCutoff);
                        if (so != null) {
                            if (curThunder && so.pos.distanceTo(playerSkyPos) > (double) (so.size / 2)) {
                                so.pos = Vec3
                                    .createVectorHelper(entP.posX, (double) StormObject.static_YPos_layer0, entP.posZ);
                                so.posGround = Vec3.createVectorHelper(entP.posX, entP.posY, entP.posZ);
                            }
                            if (so.pos.distanceTo(playerSkyPos) <= (double) so.size) {
                                so.levelWater = Math.max(so.levelWater, so.levelWaterStartRaining);
                                so.setPrecipitating(true);
                                if (curThunder && so.levelCurIntensityStage <= StormObject.STATE_THUNDER) {
                                    so.levelCurIntensityStage = StormObject.STATE_THUNDER;
                                    so.hasStormPeaked = false;
                                    so.levelCurStagesIntensity = 0.0F;
                                }
                            }
                        }
                    }
                }
            // NTNH end

            boolean shouldUpdateHighWind = false;
            boolean shouldUpdateLowWind = false;
            if (this.tickerSyncWeatherHighWind == ConfigMisc.tickerRateSyncWeatherHighWind) {
                shouldUpdateHighWind = true;
                this.tickerSyncWeatherHighWind = 0;
            }

            if (this.tickerSyncWeatherLowWind == ConfigMisc.tickerRateSyncWeatherLowWind) {
                shouldUpdateLowWind = true;
                this.tickerSyncWeatherLowWind = 0;
            }

            int i;
            if (shouldUpdateHighWind || shouldUpdateLowWind) {
                HashSet rand = new HashSet();

                for (i = 0; i < this.getStormObjects()
                    .size(); ++i) {
                    StormObject entP = (StormObject) this.getStormObjects()
                        .get(i);
                    if (entP.levelCurIntensityStage >= StormObject.STATE_HIGHWIND) {
                        if (shouldUpdateHighWind) {
                            rand.add(entP.nbtSyncForClient());
                        }
                    } else if (shouldUpdateLowWind) {
                        rand.add(entP.nbtSyncForClient());
                    }
                }

                if (rand.size() > 0) {
                    this.syncStormUpdate(rand);
                }
            }

            if (this.tickerSyncWindAndIMC == ConfigMisc.tickerRateSyncWindAndIMC) {
                this.syncWindUpdate(this.windMan);
                this.nbtStormsForIMC();
                this.tickerSyncWindAndIMC = 0;
            }

            if (WeatherUtilConfig.listDimensionsClouds.contains(Integer.valueOf(world.provider.dimensionId))
                && this.tickerSyncStormSpawnOrRemoveChecks == ConfigMisc.tickerRateSyncStormSpawnOrRemoveChecks) {
                this.tickerSyncStormSpawnOrRemoveChecks = 0;

                EntityPlayer var10;
                List stormsWithoutPlayers = new ArrayList();
                for (int var7 = 0; var7 < this.getStormObjects()
                    .size(); ++var7) {
                    StormObject var9 = (StormObject) this.getStormObjects()
                        .get(var7);
                    var10 = world.getClosestPlayer(
                        var9.posGround.xCoord,
                        var9.posGround.yCoord,
                        var9.posGround.zCoord,
                        (double) ConfigMisc.Misc_simBoxRadiusCutoff);
                    if (var10 == null && !(var9.controlledTornado && (var9.tornadoActive || var9.tornadoEndPending))) {
                        stormsWithoutPlayers.add(var9);
                    }
                }
                for (int var7 = 0; var7 < stormsWithoutPlayers.size(); ++var7) {
                    this.removeStormSafely(((StormObject) stormsWithoutPlayers.get(var7)).ID);
                }

                Random var8 = new Random();

                for (i = 0; i < world.playerEntities.size(); ++i) {
                    var10 = (EntityPlayer) world.playerEntities.get(i);
                    if (this.getStormObjectsByLayer(0)
                        .size() < ConfigMisc.Storm_MaxPerPlayerPerLayer * world.playerEntities.size()
                        && var8.nextInt(5) == 0) {
                        this.trySpawnNearPlayerForLayer(var10, 0);
                    }

                    if (this.getStormObjectsByLayer(1)
                        .size() < ConfigMisc.Storm_MaxPerPlayerPerLayer * world.playerEntities.size()
                        && ConfigMisc.Cloud_Layer1_Enable
                        && var8.nextInt(5) == 0) {
                        ;
                    }
                }
            }

            this.processPendingTornadoEnds();
        }

    }

    public void trySpawnNearPlayerForLayer(EntityPlayer entP, int layer) {
        Random rand = new Random();
        byte tryCountMax = 10;
        int tryCountCur = 0;
        boolean spawnX = true;
        boolean spawnZ = true;
        Vec3 tryPos = null;
        StormObject soClose = null;
        EntityPlayer playerClose = null;
        short closestToPlayer = 128;
        float windOffsetDist = (float) Math.min(256, ConfigMisc.Misc_simBoxRadiusCutoff / 4 * 3);
        double angle = (double) this.windMan.getWindAngleForClouds();
        double vecX = -Math.sin(Math.toRadians(angle)) * (double) windOffsetDist;

        int var20;
        int var21;
        for (double vecZ = Math.cos(Math.toRadians(angle)) * (double) windOffsetDist; tryCountCur++ == 0
            || tryCountCur < tryCountMax && (soClose != null || playerClose != null); playerClose = entP.worldObj
                .getClosestPlayer((double) var20, 50.0D, (double) var21, (double) closestToPlayer)) {
            var20 = (int) (entP.posX - vecX
                + (double) rand.nextInt(ConfigMisc.Misc_simBoxRadiusSpawn)
                - (double) rand.nextInt(ConfigMisc.Misc_simBoxRadiusSpawn));
            var21 = (int) (entP.posZ - vecZ
                + (double) rand.nextInt(ConfigMisc.Misc_simBoxRadiusSpawn)
                - (double) rand.nextInt(ConfigMisc.Misc_simBoxRadiusSpawn));
            tryPos = Vec3.createVectorHelper(
                (double) var20,
                (double) ((Integer) StormObject.layers.get(layer)).intValue(),
                (double) var21);
            soClose = this.getClosestStormAny(tryPos, (double) ConfigMisc.Cloud_Formation_MinDistBetweenSpawned);
        }

        if (soClose == null) {
            StormObject so = new StormObject(this);
            so.initFirstTime();
            so.pos = tryPos;
            so.layer = layer;
            so.userSpawnedFor = CoroUtilEntity.getName(entP);
            this.addStormObject(so);
            this.syncStormNew(so);
        } else {
            Weather.dbg("couldnt find space to spawn cloud formation");
        }

    }

    public boolean endTornado(long id, TornadoEndReason reason) {
        if (this.tornadoEventDispatching) {
            return false;
        }
        StormObject storm = (StormObject) this.lookupStormObjectsByID.get(Long.valueOf(id));
        if (storm == null || !storm.tornadoActive || storm.tornadoEndPending || reason == null) {
            return false;
        }

        storm.tornadoEndPending = true;
        storm.tornadoEndReason = reason;
        storm.tornadoActive = false;
        storm.tornadoRemainingTicks = 0;
        return true;
    }

    public boolean tryActivateTornado(StormObject storm, TornadoOrigin origin) {
        if (this.tornadoEventDispatching || storm == null
            || origin == null
            || storm.manager != this
            || storm.isDead
            || storm.tornadoActive
            || storm.tornadoEndPending
            || storm.tornadoFormationPending
            || storm.tornadoFormationVetoed
            || storm.levelCurIntensityStage < StormObject.STATE_FORMING) {
            return false;
        }
        StormObject indexed = (StormObject) this.lookupStormObjectsByID.get(Long.valueOf(storm.ID));
        if (indexed != null && indexed != storm) {
            return false;
        }

        storm.tornadoFormationPending = true;
        TornadoFormEvent event = new TornadoFormEvent(new TornadoSnapshot(this.dim, storm), origin);
        try {
            this.tornadoEventDispatching = true;
            try {
                this.postTornadoForm(event);
            } finally {
                this.tornadoEventDispatching = false;
            }
        } finally {
            storm.tornadoFormationPending = false;
        }
        if (event.isCanceled() || storm.isDead || storm.levelCurIntensityStage < StormObject.STATE_FORMING) {
            storm.tornadoFormationVetoed = true;
            return false;
        }
        if (!storm.controlledTornado) {
            storm.pullPlayers = false;
            storm.pullLivingEntities = false;
            storm.tornadoRemainingTicks = ConfigMisc.Tornado_DefaultDurationTicks;
        } else if (storm.tornadoRemainingTicks <= 0) {
            storm.tornadoFormationVetoed = true;
            return false;
        }
        storm.tornadoActive = true;
        return true;
    }

    protected void postTornadoForm(TornadoFormEvent event) {
        MinecraftForge.EVENT_BUS.post(event);
    }

    public boolean isDispatchingTornadoEvent() {
        return this.tornadoEventDispatching;
    }

    public StormObject spawnControlledTornado(double x, double z, TornadoOptions options) {
        return this.spawnControlledTornado(x, z, options, TornadoOrigin.API);
    }

    public StormObject spawnControlledTornado(double x, double z, TornadoOptions options, TornadoOrigin origin) {
        if (this.tornadoEventDispatching) {
            throw new IllegalStateException("Tornado creation is not allowed during tornado events");
        }
        if (!StormObject.hasAvailableStormID()
            || this.lookupStormObjectsByID.containsKey(Long.valueOf(StormObject.lastUsedStormID))) {
            throw new IllegalStateException("Weather2 storm ID space is exhausted or inconsistent");
        }

        World world = this.getWorld();
        StormObject storm = new StormObject(this);
        storm.pos = Vec3.createVectorHelper(x, (double) StormObject.static_YPos_layer0, z);
        int groundY = world.getHeightValue(MathHelper.floor_double(x), MathHelper.floor_double(z));
        storm.posGround = Vec3.createVectorHelper(x, (double) groundY, z);
        storm.posBaseFormationPos = storm.posGround;
        storm.currentTopYBlock = groundY;
        storm.layer = 0;
        storm.stormType = StormObject.TYPE_LAND;
        storm.naturallySpawned = false;
        storm.userSpawnedFor = "Weather2 API";
        storm.levelCurStagesIntensity = 0.0F;
        storm.maxIntensityStage = StormObject.STATE_FORMING + options.getIntensity();
        storm.hasStormPeaked = true;
        storm.controlledTornado = true;
        storm.tornadoRemainingTicks = options.getDurationTicks();
        storm.pullPlayers = options.getPullPlayers();
        storm.pullLivingEntities = options.getPullLivingEntities();
        storm.levelWater = storm.levelWaterStartRaining * 2;
        storm.setPrecipitating(true);
        storm.initFirstTime();

        if (!storm.setIntensityStage(StormObject.STATE_FORMING + options.getIntensity(), origin)) {
            return null;
        }
        this.addStormObject(storm);
        this.syncStormNew(storm);
        return storm;
    }

    public StormObject getActiveTornado(long id) {
        StormObject storm = (StormObject) this.lookupStormObjectsByID.get(Long.valueOf(id));
        return storm != null && !storm.isDead
            && !storm.tornadoEndPending
            && storm.tornadoActive
            && (storm.isTornadoFormingOrGreater() || storm.isCycloneFormingOrGreater()) ? storm : null;
    }

    public boolean setTornadoPull(long id, boolean players, boolean livingEntities) {
        if (this.tornadoEventDispatching) {
            return false;
        }
        StormObject storm = this.getActiveTornado(id);
        if (storm == null) {
            return false;
        }
        storm.pullPlayers = players;
        storm.pullLivingEntities = livingEntities;
        HashSet update = new HashSet();
        update.add(storm.nbtSyncForClient());
        this.syncStormUpdate(update);
        return true;
    }

    @Override
    public void removeStormObject(long id) {
        this.removeStormSafely(id);
    }

    public boolean removeStormSafely(long id) {
        if (this.tornadoEventDispatching) {
            throw new IllegalStateException("Storm removal is not allowed during tornado events");
        }
        StormObject storm = (StormObject) this.lookupStormObjectsByID.get(Long.valueOf(id));
        if (storm == null) {
            return false;
        }
        if (storm.tornadoActive) {
            return this.endTornado(id, TornadoEndReason.REMOVED);
        }
        if (storm.tornadoEndPending) {
            storm.tornadoEndReason = TornadoEndReason.REMOVED;
            return true;
        }

        this.syncStormRemove(storm);
        super.removeStormObject(id);
        return true;
    }

    private void tickTornadoDurations() {
        List storms = new ArrayList(this.getStormObjects());
        for (int i = 0; i < storms.size(); ++i) {
            StormObject storm = (StormObject) storms.get(i);
            if (!storm.tornadoActive || storm.tornadoEndPending || storm.isDead) {
                continue;
            }

            if (storm.tornadoRemainingTicks <= 1) {
                this.endTornado(storm.ID, TornadoEndReason.EXPIRED);
            } else {
                --storm.tornadoRemainingTicks;
            }
        }
    }

    private void processPendingTornadoEnds() {
        List storms = new ArrayList(this.getStormObjects());
        for (int i = 0; i < storms.size(); ++i) {
            StormObject storm = (StormObject) storms.get(i);
            if (!storm.tornadoEndPending) {
                continue;
            }

            TornadoEndReason reason = storm.tornadoEndReason;
            TornadoSnapshot snapshot = new TornadoSnapshot(this.dim, storm);
            storm.tornadoEndPending = false;
            storm.tornadoEndReason = null;
            if (reason == TornadoEndReason.NATURAL) {
                Set update = new HashSet();
                update.add(storm.nbtSyncForClient());
                this.syncStormUpdate(update);
            } else {
                this.syncStormRemove(storm);
                super.removeStormObject(storm.ID);
            }
            this.postTornadoEnded(new TornadoEndedEvent(snapshot, reason));
        }
    }

    protected void postTornadoEnded(TornadoEndedEvent event) {
        this.tornadoEventDispatching = true;
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } finally {
            this.tornadoEventDispatching = false;
        }
    }

    public void playerJoinedServerSyncFull(EntityPlayerMP entP) {
        World world = this.getWorld();
        if (world != null) {
            int i;
            for (i = 0; i < this.getStormObjects()
                .size(); ++i) {
                this.syncStormNew(
                    (StormObject) this.getStormObjects()
                        .get(i),
                    entP);
            }

            this.syncWindUpdate(this.windMan, entP);

            this.isVanillaRainActiveOnServer = world.isRaining();
            this.isVanillaThunderActiveOnServer = world.isThundering();
            this.syncWeatherVanilla(entP);
        }

    }

    public void nbtStormsForIMC() {
        NBTTagCompound data = new NBTTagCompound();

        for (int i = 0; i < this.getStormObjects()
            .size(); ++i) {
            StormObject so = (StormObject) this.getStormObjects()
                .get(i);
            if (so.levelCurIntensityStage > 0 || so.attrib_precipitation) {
                NBTTagCompound nbtStorm = so.nbtForIMC();
                data.setTag("storm_" + so.ID, nbtStorm);
            }
        }

        if (!data.hasNoTags()) {
            FMLInterModComms.sendRuntimeMessage(Weather.instance, Weather.modID, "weather.storms", data);
        }

    }

    public void syncLightningNew(EntityLightningBolt parEnt) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncLightningNew");
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("posX", MathHelper.floor_double(parEnt.posX));
        nbt.setInteger("posY", MathHelper.floor_double(parEnt.posY));
        nbt.setInteger("posZ", MathHelper.floor_double(parEnt.posZ));
        nbt.setInteger("entityID", parEnt.getEntityId());
        data.setTag("data", nbt);
        Weather.eventChannel.sendToDimension(
            PacketHelper.getNBTPacket(data, Weather.eventChannelName),
            this.getWorld().provider.dimensionId);
        FMLInterModComms.sendRuntimeMessage(Weather.instance, Weather.modID, "weather.lightning", data);
    }

    public void syncWindUpdate(WindManager parManager) {
        this.syncWindUpdate(parManager, null);
    }

    private void syncWindUpdate(WindManager parManager, EntityPlayerMP player) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncWindUpdate");
        data.setTag("data", parManager.nbtSyncForClient());
        if (player == null) {
            Weather.eventChannel.sendToDimension(PacketHelper.getNBTPacket(data, Weather.eventChannelName), this.dim);
        } else {
            Weather.eventChannel.sendTo(PacketHelper.getNBTPacket(data, Weather.eventChannelName), player);
        }
        if (player == null) {
            FMLInterModComms.sendRuntimeMessage(Weather.instance, Weather.modID, "weather.wind", data);
        }
    }

    public void syncStormNew(StormObject parStorm) {
        this.syncStormNew(parStorm, (EntityPlayerMP) null);
    }

    public void syncStormNew(StormObject parStorm, EntityPlayerMP entP) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncStormNew");
        data.setTag("data", parStorm.nbtSyncForClient(true));
        if (entP == null) {
            Weather.eventChannel.sendToDimension(
                PacketHelper.getNBTPacket(data, Weather.eventChannelName),
                this.getWorld().provider.dimensionId);
        } else {
            Weather.eventChannel.sendTo(PacketHelper.getNBTPacket(data, Weather.eventChannelName), entP);
        }

    }

    protected void syncStormUpdate(Set stormObjectsData) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("stormCount", stormObjectsData.size());
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncStormUpdate");
        int stormNumber = 0;

        for (Iterator var4 = stormObjectsData.iterator(); var4.hasNext(); ++stormNumber) {
            NBTTagCompound stormObjectData = (NBTTagCompound) var4.next();
            data.setTag("storm" + stormNumber, stormObjectData);
        }

        Weather.eventChannel.sendToDimension(
            PacketHelper.getNBTPacket(data, Weather.eventChannelName),
            this.getWorld().provider.dimensionId);
    }

    public void syncStormRemove(StormObject parStorm) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncStormRemove");
        data.setTag("data", parStorm.nbtSyncForClient());
        data.getCompoundTag("data")
            .setLong("ID", parStorm.ID);
        data.getCompoundTag("data")
            .setBoolean("isDead", true);
        Weather.eventChannel.sendToDimension(
            PacketHelper.getNBTPacket(data, Weather.eventChannelName),
            this.getWorld().provider.dimensionId);
    }

    public void syncVolcanoNew(VolcanoObject parStorm) {
        this.syncVolcanoNew(parStorm, (EntityPlayerMP) null);
    }

    public void syncVolcanoNew(VolcanoObject parStorm, EntityPlayerMP entP) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncVolcanoNew");
        data.setTag("data", parStorm.nbtSyncForClient());
        if (entP == null) {
            Weather.eventChannel.sendToDimension(
                PacketHelper.getNBTPacket(data, Weather.eventChannelName),
                this.getWorld().provider.dimensionId);
        } else {
            Weather.eventChannel.sendTo(PacketHelper.getNBTPacket(data, Weather.eventChannelName), entP);
        }

    }

    public void syncVolcanoUpdate(VolcanoObject parStorm) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncVolcanoUpdate");
        data.setTag("data", parStorm.nbtSyncForClient());
        Weather.eventChannel.sendToDimension(
            PacketHelper.getNBTPacket(data, Weather.eventChannelName),
            this.getWorld().provider.dimensionId);
    }

    public void syncVolcanoRemove(VolcanoObject parStorm) {}

    public void syncWeatherVanilla() {
        this.syncWeatherVanilla(null);
    }

    private void syncWeatherVanilla(EntityPlayerMP player) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("packetCommand", "WeatherData");
        data.setInteger("dimensionId", this.dim);
        data.setString("command", "syncWeatherUpdate");
        // NTNH start: write both rain and thunder flags to root and 'data' sub-compound
        data.setBoolean("isVanillaRainActiveOnServer", this.isVanillaRainActiveOnServer);
        data.setBoolean("isVanillaThunderActiveOnServer", this.isVanillaThunderActiveOnServer);
        NBTTagCompound inner = new NBTTagCompound();
        inner.setBoolean("isVanillaRainActiveOnServer", this.isVanillaRainActiveOnServer);
        inner.setBoolean("isVanillaThunderActiveOnServer", this.isVanillaThunderActiveOnServer);
        data.setTag("data", inner);
        // NTNH end
        if (player == null) {
            Weather.eventChannel.sendToDimension(PacketHelper.getNBTPacket(data, Weather.eventChannelName), this.dim);
        } else {
            Weather.eventChannel.sendTo(PacketHelper.getNBTPacket(data, Weather.eventChannelName), player);
        }
    }
}
