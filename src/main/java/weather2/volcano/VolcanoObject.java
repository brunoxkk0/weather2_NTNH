package weather2.volcano;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import extendedrenderer.ExtendedRenderer;
import extendedrenderer.particle.ParticleRegistry;
import extendedrenderer.particle.behavior.ParticleBehaviors;
import extendedrenderer.particle.entity.EntityRotFX;
import weather2.Weather;
import weather2.weathersystem.WeatherManagerBase;

public class VolcanoObject {

    public static long lastUsedID = 0L;
    public long ID;
    public WeatherManagerBase manager;
    @SideOnly(Side.CLIENT)
    public List listParticlesSmoke = new ArrayList();
    @SideOnly(Side.CLIENT)
    public ParticleBehaviors particleBehaviors;
    public int sizeMaxParticles = 300;
    public static int staticYPos = 200;
    public Vec3 pos;
    public int processRateDelay;
    public Block topBlockID;
    public int startYPos;
    public int curRadius;
    public int curHeight;
    public int state;
    public int size;
    public int maxSize;
    public int step;
    public int stepsBuildupMax;
    public int ticksToErupt;
    public int ticksPerformedErupt;
    public int ticksToCooldown;
    public int ticksPerformedCooldown;
    public int growthStage;

    public VolcanoObject(WeatherManagerBase parManager) {
        this.pos = Vec3.createVectorHelper(0.0D, (double) staticYPos, 0.0D);
        this.processRateDelay = 20;
        this.topBlockID = Blocks.air;
        this.startYPos = -1;
        this.curRadius = 5;
        this.curHeight = 3;
        this.state = 0;
        this.size = 0;
        this.maxSize = 20;
        this.step = 0;
        this.stepsBuildupMax = 20;
        this.ticksToErupt = 600;
        this.ticksPerformedErupt = 0;
        this.ticksToCooldown = 600;
        this.ticksPerformedCooldown = 0;
        this.growthStage = 0;
        this.manager = parManager;
    }

    public void initFirstTime() {
        this.ID = (long) (lastUsedID++);
    }

    public void initPost() {}

    public void resetEruption() {
        this.step = 0;
        this.ticksPerformedErupt = 0;
        this.ticksPerformedCooldown = 0;
        this.state = 2;
        this.ticksPerformedErupt = 0;
        this.ticksPerformedCooldown = 0;
    }

    public void readFromNBT(NBTTagCompound data) {
        this.ID = data.getLong("ID");
        this.pos = Vec3.createVectorHelper(
            (double) data.getInteger("posX"),
            (double) data.getInteger("posY"),
            (double) data.getInteger("posZ"));
        this.size = data.getInteger("size");
        this.maxSize = data.getInteger("maxSize");
        this.state = data.getInteger("state");
        this.curRadius = data.getInteger("curRadius");
        this.curHeight = data.getInteger("curHeight");
        this.topBlockID = (Block) Block.blockRegistry.getObject(data.getString("topBlockID"));
        this.startYPos = data.getInteger("startYPos");
        this.step = data.getInteger("step");
        this.ticksPerformedErupt = data.getInteger("ticksPerformedErupt");
        this.ticksPerformedCooldown = data.getInteger("ticksPerformedCooldown");
    }

    public void writeToNBT(NBTTagCompound data) {
        data.setLong("ID", this.ID);
        data.setInteger("posX", (int) this.pos.xCoord);
        data.setInteger("posY", (int) this.pos.yCoord);
        data.setInteger("posZ", (int) this.pos.zCoord);
        data.setInteger("size", this.size);
        data.setInteger("maxSize", this.maxSize);
        data.setInteger("state", this.state);
        data.setInteger("curRadius", this.curRadius);
        data.setInteger("curHeight", this.curHeight);
        data.setString("topBlockID", Block.blockRegistry.getNameForObject(this.topBlockID));
        data.setInteger("startYPos", this.startYPos);
        data.setInteger("step", this.step);
        data.setInteger("ticksPerformedErupt", this.ticksPerformedErupt);
        data.setInteger("ticksPerformedCooldown", this.ticksPerformedCooldown);
    }

    public void nbtSyncFromServer(NBTTagCompound parNBT) {
        this.ID = parNBT.getLong("ID");
        Weather.dbg("VolcanoObject " + this.ID + " receiving sync");
        this.pos = Vec3.createVectorHelper(
            (double) parNBT.getInteger("posX"),
            (double) parNBT.getInteger("posY"),
            (double) parNBT.getInteger("posZ"));
        this.size = parNBT.getInteger("size");
        this.maxSize = parNBT.getInteger("maxSize");
        this.state = parNBT.getInteger("state");
    }

    public NBTTagCompound nbtSyncForClient() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("posX", (int) this.pos.xCoord);
        data.setInteger("posY", (int) this.pos.yCoord);
        data.setInteger("posZ", (int) this.pos.zCoord);
        data.setLong("ID", this.ID);
        data.setInteger("size", this.size);
        data.setInteger("maxSize", this.maxSize);
        data.setInteger("state", this.state);
        return data;
    }

    public void tick() {}

    @SideOnly(Side.CLIENT)
    public void tickClient() {
        if (this.particleBehaviors == null) {
            this.particleBehaviors = new ParticleBehaviors(
                Vec3.createVectorHelper(this.pos.xCoord, this.pos.yCoord, this.pos.zCoord));
        } else if (!Minecraft.getMinecraft()
            .isSingleplayer() || !(Minecraft.getMinecraft().currentScreen instanceof GuiIngameMenu)) {
                this.particleBehaviors.tickUpdateList();
            }

        byte delay = 1;
        byte loopSize = 1;
        Random rand = new Random();
        int i;
        if (this.manager.getWorld()
            .getTotalWorldTime() % (long) delay == 0L) {
            for (i = 0; i < loopSize; ++i) {
                if (this.listParticlesSmoke.size() < 500) {
                    double ent = (double) (this.size / 48);
                    EntityRotFX particle = this.spawnSmokeParticle(
                        this.pos.xCoord + rand.nextDouble() * ent - rand.nextDouble() * ent,
                        this.pos.yCoord + (double) this.size + 2.0D,
                        this.pos.zCoord + rand.nextDouble() * ent - rand.nextDouble() * ent);
                    this.listParticlesSmoke.add(particle);
                }
            }
        }

        boolean var19 = true;
        boolean var20 = true;

        for (i = 0; i < this.listParticlesSmoke.size(); ++i) {
            EntityRotFX var21 = (EntityRotFX) this.listParticlesSmoke.get(i);
            if (var21.isDead) {
                this.listParticlesSmoke.remove(var21);
            } else {
                double speed = 0.4D + rand.nextDouble() * 1.0D * 0.01D;
                double distt = 300.0D;
                double curDist = var21.getDistance(this.pos.xCoord, (double) staticYPos, this.pos.zCoord);
                double vecX = var21.posX - this.pos.xCoord;
                double vecZ = var21.posZ - this.pos.zCoord;
                float angle = (float) (Math.atan2(vecZ, vecX) * 180.0D / 3.141592653589793D);
                angle += 50.0F;
                angle = (float) ((double) angle - (double) (var21.getEntityId() % 10) * 3.0D);
                angle += (float) (rand.nextInt(10) - rand.nextInt(10));
                if (curDist > distt) {
                    angle += 20.0F;
                }

                double var17 = Math.sqrt(
                    var21.motionX * var21.motionX + var21.motionY * var21.motionY + var21.motionZ * var21.motionZ);
            }
        }

    }

    @SideOnly(Side.CLIENT)
    public EntityRotFX spawnSmokeParticle(double x, double y, double z) {
        double speed = 0.0D;
        Random rand = new Random();
        EntityRotFX entityfx = this.particleBehaviors.spawnNewParticleIconFX(
            Minecraft.getMinecraft().theWorld,
            ParticleRegistry.cloud256,
            x,
            y,
            z,
            (rand.nextDouble() - rand.nextDouble()) * speed,
            0.0D,
            (rand.nextDouble() - rand.nextDouble()) * speed);
        this.particleBehaviors.initParticle(entityfx);
        ParticleBehaviors var10000 = this.particleBehaviors;
        ParticleBehaviors.setParticleRandoms(entityfx, true, true);
        var10000 = this.particleBehaviors;
        ParticleBehaviors.setParticleFire(entityfx);
        entityfx.noClip = true;
        entityfx.callUpdatePB = false;
        entityfx.setMaxAge(400 + rand.nextInt(200));
        entityfx.setScale(50.0F);
        float randFloat = rand.nextFloat() * 0.6F;
        float baseBright = 0.1F;
        float finalBright = Math.min(1.0F, baseBright + randFloat);
        entityfx.setRBGColorF(finalBright, finalBright, finalBright);
        ExtendedRenderer.rotEffRenderer.addEffect(entityfx);
        this.particleBehaviors.particles.add(entityfx);
        return entityfx;
    }

    public void reset() {
        this.setDead();
    }

    public void setDead() {
        Weather.dbg("volcano... killed? NO ONE KILLS A VOLCANO!");
    }

}
