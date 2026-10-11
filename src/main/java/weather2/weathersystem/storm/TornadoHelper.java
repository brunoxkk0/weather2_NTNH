package weather2.weathersystem.storm;

import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import weather2.Weather;
import weather2.util.WeatherUtilEntity;
import weather2.util.WeatherUtilSound;

public class TornadoHelper {

    public StormObject storm;
    public int ripCount = 0;
    public long lastGrabTime = 0L;
    public int tickGrabCount = 0;
    public int removeCount = 0;
    public int tryRipCount = 0;
    public int tornadoBaseSize = 5;
    public int grabDist = 100;
    public boolean lastTickPlayerClose;

    public TornadoHelper(StormObject parStorm) {
        this.storm = parStorm;
    }

    public int getTornadoBaseSize() {
        byte sizeChange = 10;
        return this.storm.levelCurIntensityStage >= StormObject.STATE_STAGE5 ? sizeChange * 9
            : (this.storm.levelCurIntensityStage >= StormObject.STATE_STAGE4 ? sizeChange * 7
                : (this.storm.levelCurIntensityStage >= StormObject.STATE_STAGE3 ? sizeChange * 5
                    : (this.storm.levelCurIntensityStage >= StormObject.STATE_STAGE2 ? sizeChange * 4
                        : (this.storm.levelCurIntensityStage >= StormObject.STATE_STAGE1 ? sizeChange * 3
                            : (this.storm.levelCurIntensityStage >= StormObject.STATE_FORMING ? sizeChange * 1 : 5)))));
    }

    public void tick(World parWorld) {
        if (this.storm != null && this.storm.tornadoActive && !this.storm.tornadoEndPending) {
            this.tornadoBaseSize = this.getTornadoBaseSize();
            this.forceRotate(parWorld);
        }
    }

    public boolean forceRotate(World parWorld) {
        if (!this.storm.tornadoActive || this.storm.tornadoEndPending) {
            return false;
        }

        double dist = (double) this.grabDist;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(
            this.storm.pos.xCoord,
            (double) this.storm.currentTopYBlock,
            this.storm.pos.zCoord,
            this.storm.pos.xCoord,
            (double) this.storm.currentTopYBlock,
            this.storm.pos.zCoord);
        List list = parWorld
            .getEntitiesWithinAABB(Entity.class, aabb.expand(dist, (double) (this.storm.maxHeight * 3), dist));
        boolean foundEnt = false;
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Entity entity1 = (Entity) list.get(i);
                if (this.storm.shouldPull(entity1)
                    && this.getDistanceXZ(this.storm.posBaseFormationPos, entity1.posX, entity1.posY, entity1.posZ)
                        < dist
                    && WeatherUtilEntity
                        .isEntityOutside(entity1, !(entity1 instanceof net.minecraft.entity.player.EntityPlayer))) {
                    this.storm.spinEntity(entity1);
                    foundEnt = true;
                }
            }
        }

        return foundEnt;
    }

    public double getDistanceXZ(Vec3 parVec, double var1, double var3, double var5) {
        double var7 = parVec.xCoord - var1;
        double var11 = parVec.zCoord - var5;
        return (double) MathHelper.sqrt_double(var7 * var7 + var11 * var11);
    }

    public double getDistanceXZ(Entity ent, double var1, double var3, double var5) {
        double var7 = ent.posX - var1;
        double var11 = ent.posZ - var5;
        return (double) MathHelper.sqrt_double(var7 * var7 + var11 * var11);
    }

    @SideOnly(Side.CLIENT)
    public void soundUpdates(boolean playFarSound, boolean playNearSound) {
        Minecraft mc = FMLClientHandler.instance()
            .getClient();
        if (mc.thePlayer != null) {
            short far = 200;
            short close = 120;
            StormObject var10001 = this.storm;
            if (this.storm.stormType == StormObject.TYPE_WATER) {
                close = 200;
            }

            Vec3 plPos = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            double distToPlayer = this.storm.posGround.distanceTo(plPos);
            float volScaleFar = (float) (((double) far - distToPlayer) / (double) far);
            float volScaleClose = (float) (((double) close - distToPlayer) / (double) close);
            if (volScaleFar < 0.0F) {
                volScaleFar = 0.0F;
            }

            if (volScaleClose < 0.0F) {
                volScaleClose = 0.0F;
            }

            // NTNH start: audio balance scaling (0.75x)
            volScaleFar = weather2.compat.WeatherNTNHHooks.getAdjustedWindVolume(volScaleFar);
            volScaleClose = weather2.compat.WeatherNTNHHooks.getAdjustedWindVolume(volScaleClose);
            // NTNH end

            if (distToPlayer < (double) close) {
                if (!this.lastTickPlayerClose) {
                    ;
                }

                this.lastTickPlayerClose = true;
            } else {
                this.lastTickPlayerClose = false;
            }

            if (distToPlayer < (double) far) {
                if (playFarSound) {
                    this.tryPlaySound(WeatherUtilSound.snd_wind_far, 2, mc.thePlayer, volScaleFar, (float) far);
                }

                if (playNearSound) {
                    this.tryPlaySound(WeatherUtilSound.snd_wind_close, 1, mc.thePlayer, volScaleClose, (float) close);
                }

                var10001 = this.storm;
                if (this.storm.levelCurIntensityStage >= StormObject.STATE_FORMING) {
                    var10001 = this.storm;
                    if (this.storm.stormType == StormObject.TYPE_LAND) {
                        this.tryPlaySound(
                            WeatherUtilSound.snd_dmg_close,
                            0,
                            mc.thePlayer,
                            volScaleClose,
                            (float) close);
                    }
                }
            }

        }
    }

    public boolean tryPlaySound(String[] sound, int arrIndex, Entity source, float vol, float parCutOffRange) {
        Random rand = new Random();
        if (WeatherUtilSound.soundTimer[arrIndex] <= System.currentTimeMillis()) {
            WeatherUtilSound.playMovingSound(
                this.storm,
                Weather.modID + ":streaming." + sound[WeatherUtilSound.snd_rand[arrIndex]],
                vol,
                1.0F,
                parCutOffRange);
            int length = ((Integer) WeatherUtilSound.soundToLength.get(sound[WeatherUtilSound.snd_rand[arrIndex]]))
                .intValue();
            WeatherUtilSound.soundTimer[arrIndex] = System.currentTimeMillis() + (long) length - 500L;
            WeatherUtilSound.snd_rand[arrIndex] = rand.nextInt(3);
        }

        return false;
    }
}
