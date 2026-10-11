package weather2.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import weather2.weathersystem.storm.StormObject;

public class EntityMovingBlock extends Entity implements IEntityAdditionalSpawnData {

    public Block tile;
    public static final int falling = 0;
    public static final int grabbed = 1;
    public int mode = 1;
    public static final float slowdown = 0.98F;
    public static final float curvature = 0.05F;
    public int metadata;
    public TileEntity tileentity;
    public Material material;
    public int age = 0;
    public int type;
    public boolean noCollision;
    public boolean collideFalling = false;
    public double vecX;
    public double vecY;
    public double vecZ;
    public double lastPosX;
    public double lastPosZ;
    public int gravityDelay;

    public EntityMovingBlock(World var1) {
        super(var1);
        this.tile = null;
        this.noCollision = true;
        this.gravityDelay = 60;
        this.isDead = true;
    }

    public EntityMovingBlock(World var1, int var2, int var3, int var4, Block var5, StormObject parOwner) {
        super(var1);
        this.type = 0;
        this.noCollision = false;
        this.gravityDelay = 60;
        this.noCollision = true;
        this.tile = var5;
        this.setSize(0.9F, 0.9F);
        this.yOffset = this.height / 2.0F;
        this.setPosition((double) var2 + 0.5D, (double) var3 + 0.5D, (double) var4 + 0.5D);
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.prevPosX = (double) ((float) var2 + 0.5F);
        this.prevPosY = (double) ((float) var3 + 0.5F);
        this.prevPosZ = (double) ((float) var4 + 0.5F);
        this.material = this.tile.getMaterial();
        this.tileentity = var1.getTileEntity(var2, var3, var4);
        this.metadata = var1.getBlockMetadata(var2, var3, var4);
        this.isDead = true;

    }

    public boolean isInRangeToRenderDist(double var1) {
        return true;
    }

    public boolean canTriggerWalking() {
        return false;
    }

    public void entityInit() {}

    public boolean canBePushed() {
        return !this.isDead;
    }

    public boolean canBeCollidedWith() {
        return !this.isDead && !this.noCollision;
    }

    public void onUpdate() {
        this.isDead = true;
    }

    public boolean attackEntityFrom(Entity var1, int var2) {
        return false;
    }

    protected void writeEntityToNBT(NBTTagCompound var1) {
        var1.setString("Tile", Block.blockRegistry.getNameForObject(this.tile));
        var1.setByte("Metadata", (byte) this.metadata);
        var1.setInteger("blocktype", this.type);
        NBTTagCompound var2 = new NBTTagCompound();
        if (this.tileentity != null) {
            this.tileentity.writeToNBT(var2);
        }

        var1.setTag("TileEntity", var2);
    }

    protected void readEntityFromNBT(NBTTagCompound var1) {
        this.tile = (Block) Block.blockRegistry.getObject(var1.getString("Tile"));
        this.metadata = var1.getByte("Metadata") & 15;
        this.type = var1.getInteger("blocktype");
        this.tileentity = null;
        if (this.tile instanceof BlockContainer) {
            this.tileentity = ((BlockContainer) this.tile).createNewTileEntity(this.worldObj, this.metadata);
            NBTTagCompound var2 = var1.getCompoundTag("TileEntity");
            this.tileentity.readFromNBT(var2);
        }

        if (this.type == 0) {
            this.setDead();
        }

    }

    public float getShadowSize() {
        return 0.0F;
    }

    public boolean isInRangeToRenderVec3D(Vec3 asd) {
        return true;
    }

    public World func_22685_k() {
        return this.worldObj;
    }

    public void writeSpawnData(ByteBuf data) {
        ByteBufUtils.writeUTF8String(data, Block.blockRegistry.getNameForObject(this.tile));
        data.writeInt(this.metadata);
    }

    public void readSpawnData(ByteBuf data) {
        this.tile = (Block) Block.blockRegistry.getObject(ByteBufUtils.readUTF8String(data));
        this.metadata = data.readInt();
    }
}
