package weather2.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Inert legacy tile entity that preserves deflector save data. */
public class TileEntityWeatherDeflector extends TileEntity {

    public long power;
    public boolean isFieldActive;
    public int blackoutCooldown;
    private byte legacyState;
    private int legacyStateTimer;

    @Override
    public void updateEntity() {}

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setLong("power", this.power);
        nbt.setByte("deflectorState", this.legacyState);
        nbt.setInteger("stateTimer", this.legacyStateTimer);
        nbt.setBoolean("isFieldActive", this.isFieldActive);
        nbt.setInteger("blackoutCooldown", this.blackoutCooldown);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.power = nbt.getLong("power");
        this.legacyState = nbt.getByte("deflectorState");
        this.legacyStateTimer = nbt.getInteger("stateTimer");
        this.isFieldActive = nbt.getBoolean("isFieldActive");
        this.blackoutCooldown = nbt.getInteger("blackoutCooldown");
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.readFromNBT(packet.func_148857_g());
    }
}
