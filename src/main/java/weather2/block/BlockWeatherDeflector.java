package weather2.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockWeatherDeflector extends BlockContainer {

    // NTNH start: [FIX] (registry): Add no-arg constructor for Forge 1.7.10 GameRegistry auto-ID assignment
    public BlockWeatherDeflector() {
        super(Material.clay);
    }

    public BlockWeatherDeflector(int var1) {
        this();
    }
    // NTNH end

    public TileEntity createNewTileEntity(World var1, int meta) {
        return new TileEntityWeatherDeflector();
    }

    public boolean isOpaqueCube() {
        return false;
    }

    public boolean renderAsNormalBlock() {
        return false;
    }

}
