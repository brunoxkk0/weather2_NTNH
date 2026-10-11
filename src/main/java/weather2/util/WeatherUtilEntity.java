package weather2.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Items;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import CoroUtil.api.weather.IWindHandler;
import CoroUtil.api.weather.WindHandler;
import CoroUtil.entity.EntityTropicalFishHook;
import extendedrenderer.particle.entity.EntityRotFX;
import weather2.ClientTickHandler;
import weather2.entity.EntityMovingBlock;
import weather2.weathersystem.wind.WindManager;

public class WeatherUtilEntity {

    private static final String TORNADO_PULL_UNTIL = "weather2TornadoPullUntil";
    private static final String TORNADO_PULL_DIMENSION = "weather2TornadoPullDimension";
    public static int playerInAirTime = 0;

    public static void markTornadoPull(EntityPlayer player) {
        player.getEntityData()
            .setLong(TORNADO_PULL_UNTIL, player.worldObj.getTotalWorldTime() + 5L);
        player.getEntityData()
            .setInteger(TORNADO_PULL_DIMENSION, player.worldObj.provider.dimensionId);
    }

    public static boolean shouldIgnoreTornadoFlightCheck(EntityPlayer player) {
        return player != null && player.worldObj != null
            && player.worldObj.provider != null
            && player.getEntityData()
                .hasKey(TORNADO_PULL_UNTIL)
            && player.getEntityData()
                .getInteger(TORNADO_PULL_DIMENSION) == player.worldObj.provider.dimensionId
            && player.getEntityData()
                .getLong(TORNADO_PULL_UNTIL) >= player.worldObj.getTotalWorldTime();
    }

    public static float getWeight(Entity entity1) {
        return getWeight(entity1, false);
    }

    public static float getWeight(Entity entity1, boolean forTornado) {
        if (entity1 instanceof IWindHandler) {
            return ((IWindHandler) entity1).getWindWeight();
        } else if (entity1 instanceof WindHandler) {
            return ((WindHandler) entity1).getWindWeight();
        } else if (entity1 instanceof EntityMovingBlock) {
            return 1.0F + (float) ((EntityMovingBlock) entity1).age / 200.0F;
        } else if (entity1 instanceof EntityPlayer) {
            if (!entity1.onGround && !entity1.handleWaterMovement()) {
                ++playerInAirTime;
            } else {
                playerInAirTime = 0;
            }

            if (((EntityPlayer) entity1).capabilities.isCreativeMode) {
                return 1.0E8F;
            } else {
                byte var4 = 0;
                if (((EntityPlayer) entity1).inventory != null
                    && ((EntityPlayer) entity1).inventory.armorInventory[2] != null
                    && ((EntityPlayer) entity1).inventory.armorInventory[2].getItem() == Items.iron_chestplate) {
                    var4 = 2;
                }

                if (((EntityPlayer) entity1).inventory != null
                    && ((EntityPlayer) entity1).inventory.armorInventory[2] != null
                    && ((EntityPlayer) entity1).inventory.armorInventory[2].getItem() == Items.diamond_chestplate) {
                    var4 = 4;
                }

                return forTornado ? 4.5F + (float) var4 + (float) (playerInAirTime / 400)
                    : 5.0F + (float) var4 + (float) (playerInAirTime / 400);
            }
        } else {
            if (entity1.worldObj.isRemote && entity1 instanceof EntityRotFX) {
                float airTime = WeatherUtilParticle.getParticleWeight((EntityRotFX) entity1);
                if (airTime != -1.0F) {
                    return airTime;
                }
            }

            if (entity1 instanceof EntitySquid) {
                return 400.0F;
            } else if (!(entity1 instanceof EntityLivingBase)) {
                return !(entity1 instanceof EntityBoat) && !(entity1 instanceof EntityItem)
                    && !(entity1 instanceof EntityTropicalFishHook)
                    && !(entity1 instanceof EntityFishHook) ? (entity1 instanceof EntityMinecart ? 80.0F : 1.0F)
                        : 4000.0F;
            } else {
                int var3 = entity1.getEntityData()
                    .getInteger("timeInAir");
                if (!entity1.onGround && !entity1.handleWaterMovement()) {
                    ++var3;
                } else {
                    var3 = 0;
                }

                entity1.getEntityData()
                    .setInteger("timeInAir", var3);
                return forTornado ? 0.5F + (float) var3 / 800.0F
                    : 500.0F + (entity1.onGround ? 2.0F : 0.0F) + (float) (var3 / 400);
            }
        }
    }

    public static boolean canPushEntity(Entity ent) {
        WindManager windMan = ClientTickHandler.weatherManager.windMan;
        double speed = 10.0D;
        int startX = (int) (ent.posX
            - speed * (double) (-MathHelper.sin(windMan.getWindAngleForPriority() / 180.0F * 3.1415927F)
                * MathHelper.cos(0.0F)));
        int startZ = (int) (ent.posZ
            - speed * (double) (MathHelper.cos(windMan.getWindAngleForPriority() / 180.0F * 3.1415927F)
                * MathHelper.cos(0.0F)));
        if (ent instanceof EntityPlayer) {
            boolean var6 = true;
        }

        return ent.worldObj.rayTraceBlocks(
            Vec3.createVectorHelper(ent.posX, ent.posY + (double) ent.getEyeHeight(), ent.posZ),
            Vec3.createVectorHelper((double) startX, ent.posY + (double) ent.getEyeHeight(), (double) startZ)) == null;
    }

    public static boolean isEntityOutside(Entity parEnt) {
        return isEntityOutside(parEnt, false);
    }

    public static boolean isEntityOutside(Entity parEnt, boolean cheapCheck) {
        return isPosOutside(
            parEnt.worldObj,
            Vec3.createVectorHelper(parEnt.posX, parEnt.posY, parEnt.posZ),
            cheapCheck);
    }

    public static boolean isPosWindExposed(World world, Vec3 pos) {
        if (!isFinite(pos) || !isChunkLoaded(world, pos.xCoord, pos.zCoord)) {
            return false;
        }
        if (isSkyExposed(world, pos)) {
            return true;
        }
        return isPathToSky(world, pos, ForgeDirection.NORTH) && isPathToSky(world, pos, ForgeDirection.SOUTH)
            || isPathToSky(world, pos, ForgeDirection.EAST) && isPathToSky(world, pos, ForgeDirection.WEST);
    }

    public static boolean isEntityWindExposed(Entity entity, boolean skyOnly) {
        if (entity == null || entity.worldObj == null) {
            return false;
        }
        Vec3 pos = Vec3.createVectorHelper(entity.posX, entity.posY, entity.posZ);
        return skyOnly ? isSkyExposed(entity.worldObj, pos) : isPosWindExposed(entity.worldObj, pos);
    }

    private static boolean isSkyExposed(World world, Vec3 pos) {
        if (!isFinite(pos) || !isChunkLoaded(world, pos.xCoord, pos.zCoord)) {
            return false;
        }
        return (double) world.getHeightValue(MathHelper.floor_double(pos.xCoord), MathHelper.floor_double(pos.zCoord))
            < pos.yCoord + 1.0D;
    }

    private static boolean isPathToSky(World world, Vec3 pos, ForgeDirection direction) {
        double x = pos.xCoord + (double) direction.offsetX * 5.0D;
        double z = pos.zCoord + (double) direction.offsetZ * 5.0D;
        if (!isChunkLoaded(world, x, z)) {
            return false;
        }
        Vec3 start = Vec3.createVectorHelper(pos.xCoord, pos.yCoord + 1.0D, pos.zCoord);
        Vec3 end = Vec3.createVectorHelper(x, start.yCoord, z);
        if ((double) world.getHeightValue(MathHelper.floor_double(x), MathHelper.floor_double(z)) >= end.yCoord) {
            return false;
        }
        return world.rayTraceBlocks(start, end) == null;
    }

    private static boolean isChunkLoaded(World world, double x, double z) {
        if (world == null || Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(z) || Double.isInfinite(z)) {
            return false;
        }
        if (world.getChunkProvider() == null) {
            return false;
        }
        return world.getChunkProvider()
            .chunkExists(MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4);
    }

    private static boolean isFinite(Vec3 pos) {
        return pos != null && !Double.isNaN(pos.xCoord) && !Double.isInfinite(pos.xCoord)
            && !Double.isNaN(pos.yCoord) && !Double.isInfinite(pos.yCoord)
            && !Double.isNaN(pos.zCoord) && !Double.isInfinite(pos.zCoord);
    }

    public static boolean isPosOutside(World parWorld, Vec3 parPos) {
        return isPosOutside(parWorld, parPos, false);
    }

    public static boolean isPosOutside(World parWorld, Vec3 parPos, boolean cheapCheck) {
        byte rangeCheck = 5;
        byte yOffset = 1;
        if ((double) parWorld
            .getHeightValue(MathHelper.floor_double(parPos.xCoord), MathHelper.floor_double(parPos.zCoord))
            < parPos.yCoord + 1.0D) {
            return true;
        } else if (cheapCheck) {
            return false;
        } else {
            Vec3 vecTry = Vec3.createVectorHelper(
                parPos.xCoord + (double) (ForgeDirection.NORTH.offsetX * rangeCheck),
                parPos.yCoord + (double) yOffset,
                parPos.zCoord + (double) (ForgeDirection.NORTH.offsetZ * rangeCheck));
            if (checkVecOutside(parWorld, parPos, vecTry)) {
                return true;
            } else {
                vecTry = Vec3.createVectorHelper(
                    parPos.xCoord + (double) (ForgeDirection.SOUTH.offsetX * rangeCheck),
                    parPos.yCoord + (double) yOffset,
                    parPos.zCoord + (double) (ForgeDirection.SOUTH.offsetZ * rangeCheck));
                if (checkVecOutside(parWorld, parPos, vecTry)) {
                    return true;
                } else {
                    vecTry = Vec3.createVectorHelper(
                        parPos.xCoord + (double) (ForgeDirection.EAST.offsetX * rangeCheck),
                        parPos.yCoord + (double) yOffset,
                        parPos.zCoord + (double) (ForgeDirection.EAST.offsetZ * rangeCheck));
                    if (checkVecOutside(parWorld, parPos, vecTry)) {
                        return true;
                    } else {
                        vecTry = Vec3.createVectorHelper(
                            parPos.xCoord + (double) (ForgeDirection.WEST.offsetX * rangeCheck),
                            parPos.yCoord + (double) yOffset,
                            parPos.zCoord + (double) (ForgeDirection.WEST.offsetZ * rangeCheck));
                        return checkVecOutside(parWorld, parPos, vecTry);
                    }
                }
            }
        }
    }

    public static boolean checkVecOutside(World parWorld, Vec3 parPos, Vec3 parCheckPos) {
        boolean dirNorth = parWorld.rayTraceBlocks(parPos, parCheckPos) == null;
        return dirNorth && (double) parWorld
            .getHeightValue(MathHelper.floor_double(parCheckPos.xCoord), MathHelper.floor_double(parCheckPos.zCoord))
            < parCheckPos.yCoord;
    }

}
