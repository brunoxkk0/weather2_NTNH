package weather2.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.client.FMLClientHandler;
import weather2.weathersystem.storm.StormObject;

public class MovingSoundStreamingSource extends MovingSound {

    private StormObject storm = null;
    private float baseVolume;
    private World sourceWorld;
    public float cutOffRange = 128.0F;
    public Vec3 realSource = null;

    public MovingSoundStreamingSource(Vec3 parPos, ResourceLocation parRes, float parVolume, float parPitch,
        float parCutOffRange) {
        super(parRes);
        this.repeat = false;
        this.baseVolume = normalizeBaseVolume(parVolume);
        this.volume = this.baseVolume;
        this.field_147663_c = parPitch;
        this.cutOffRange = parCutOffRange;
        this.realSource = parPos;
        this.sourceWorld = getClientWorld();
        this.update();
    }

    public MovingSoundStreamingSource(StormObject parStorm, ResourceLocation parRes, float parVolume, float parPitch,
        float parCutOffRange) {
        super(parRes);
        this.storm = parStorm;
        this.repeat = false;
        this.baseVolume = normalizeBaseVolume(parVolume);
        this.volume = this.baseVolume;
        this.field_147663_c = parPitch;
        this.cutOffRange = parCutOffRange;
        this.sourceWorld = getClientWorld();
        this.update();
    }

    public void update() {
        Minecraft mc = FMLClientHandler.instance()
            .getClient();
        this.updateSound(mc == null ? null : mc.thePlayer, mc == null ? null : mc.theWorld);
    }

    void updateSound(EntityClientPlayerMP player, World world) {
        if (player == null || world == null
            || player.worldObj != world
            || this.sourceWorld != world
            || (this.storm != null && this.storm.isDead)
            || this.cutOffRange <= 0.0F
            || Float.isNaN(this.cutOffRange)
            || Float.isInfinite(this.cutOffRange)
            || this.baseVolume <= 0.0F) {
            this.stopSound();
            return;
        }

        if (this.storm != null) {
            if (!isFinite(this.storm.posGround)) {
                this.stopSound();
                return;
            }
            this.realSource = Vec3.createVectorHelper(
                this.storm.posGround.xCoord,
                this.storm.posGround.yCoord,
                this.storm.posGround.zCoord);
        }

        if (!isFinite(this.realSource) || !isFinite(player.posX) || !isFinite(player.posY) || !isFinite(player.posZ)) {
            this.stopSound();
            return;
        }
        this.xPosF = (float) player.posX;
        this.yPosF = (float) player.posY;
        this.zPosF = (float) player.posZ;
        double distance = MathHelper.sqrt_double(
            this.getDistanceFrom(this.realSource, Vec3.createVectorHelper(player.posX, player.posY, player.posZ)));
        this.volume = attenuatedVolume(this.baseVolume, distance, this.cutOffRange);
    }

    private void stopSound() {
        this.volume = 0.0F;
        this.donePlaying = true;
    }

    static float attenuatedVolume(float baseVolume, double distance, float range) {
        if (Float.isNaN(baseVolume) || Float.isInfinite(baseVolume)
            || baseVolume <= 0.0F
            || Double.isNaN(distance)
            || Double.isInfinite(distance)
            || distance < 0.0D
            || Float.isNaN(range)
            || Float.isInfinite(range)
            || range <= 0.0F) {
            return 0.0F;
        }
        double attenuation = Math.max(0.0D, Math.min(1.0D, 1.0D - distance / (double) range));
        return (float) ((double) baseVolume * attenuation);
    }

    private static float normalizeBaseVolume(float volume) {
        return Float.isNaN(volume) || Float.isInfinite(volume) || volume < 0.0F ? 0.0F : volume;
    }

    private static boolean isFinite(Vec3 pos) {
        return pos != null && isFinite(pos.xCoord) && isFinite(pos.yCoord) && isFinite(pos.zCoord);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static World getClientWorld() {
        Minecraft mc = FMLClientHandler.instance()
            .getClient();
        return mc == null ? null : mc.theWorld;
    }

    public double getDistanceFrom(Vec3 source, Vec3 targ) {
        double d3 = source.xCoord - targ.xCoord;
        double d4 = source.yCoord - targ.yCoord;
        double d5 = source.zCoord - targ.zCoord;
        return d3 * d3 + d4 * d4 + d5 * d5;
    }
}
