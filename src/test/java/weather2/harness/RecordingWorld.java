package weather2.harness;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import sun.misc.Unsafe;

/** Minimal real WorldServer subclass for destructive-weather regression tests. */
public final class RecordingWorld extends WorldServer {

    private static final Unsafe UNSAFE = unsafe();

    public Map<Long, Block> blocks;
    public List<Entity> spawned;
    public List<Entity> queryEntities;
    public GameRules gameRules;
    private IChunkProvider chunkProvider;
    public int blockWrites;
    public Block defaultBlock = Blocks.air;
    public Block groundBlock;
    public int groundHeight = Integer.MIN_VALUE;
    public long worldTime = 1L;
    public BiomeGenBase biome = BiomeGenBase.plains;
    public TileEntity tileEntity;
    private boolean chunksLoaded = true;

    private RecordingWorld() {
        super(null, null, "recording", 0, (WorldSettings) null, (Profiler) null);
    }

    public static RecordingWorld create() {
        try {
            RecordingWorld world = (RecordingWorld) UNSAFE.allocateInstance(RecordingWorld.class);
            WorldProvider provider = (WorldProvider) UNSAFE.allocateInstance(RecordingProvider.class);
            world.blocks = new HashMap<Long, Block>();
            world.spawned = new ArrayList<Entity>();
            world.queryEntities = new ArrayList<Entity>();
            world.chunksLoaded = true;
            world.gameRules = new GameRules();
            world.defaultBlock = Blocks.air;
            world.groundHeight = Integer.MIN_VALUE;
            world.chunkProvider = (IChunkProvider) Proxy.newProxyInstance(
                IChunkProvider.class.getClassLoader(),
                new Class<?>[] { IChunkProvider.class },
                (proxy, method, args) -> method.getName()
                    .equals("chunkExists") ? Boolean.valueOf(world.chunksLoaded)
                        : method.getReturnType() == boolean.class ? Boolean.TRUE : null);
            setField(provider, "dimensionId", Integer.valueOf(0));
            setField(world, "provider", provider);
            world.difficultySetting = EnumDifficulty.NORMAL;
            setField(world, "theProfiler", new Profiler());
            world.rand = new Random(1L);
            world.gameRules.setOrCreateGameRule("doFireTick", "true");
            return world;
        } catch (InstantiationException e) {
            throw new AssertionError(e);
        }
    }

    public void putBlock(int x, int y, int z, Block block) {
        this.blocks.put(key(x, y, z), block);
    }

    public void setChunkLoaded(boolean loaded) {
        this.chunksLoaded = loaded;
    }

    private static long key(int x, int y, int z) {
        return (((long) x & 0x3ffffffL) << 38) | (((long) z & 0x3ffffffL) << 12) | ((long) y & 0xfffL);
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        Block block = this.blocks.get(Long.valueOf(key(x, y, z)));
        if (block == null && y < this.groundHeight) return this.groundBlock;
        return block == null ? this.defaultBlock : block;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return 0;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Block block, int metadata, int flags) {
        ++this.blockWrites;
        this.blocks.put(Long.valueOf(key(x, y, z)), block);
        return true;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Block block) {
        return this.setBlock(x, y, z, block, 0, 3);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return this.tileEntity;
    }

    @Override
    public int getHeightValue(int x, int z) {
        return 64;
    }

    @Override
    public int getPrecipitationHeight(int x, int z) {
        return 64;
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return this.biome;
    }

    @Override
    public long getTotalWorldTime() {
        return this.worldTime;
    }

    @Override
    public int getSavedLightValue(net.minecraft.world.EnumSkyBlock type, int x, int y, int z) {
        return 0;
    }

    @Override
    public boolean blockExists(int x, int y, int z) {
        return true;
    }

    @Override
    public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
        return true;
    }

    @Override
    public Chunk getChunkFromChunkCoords(int x, int z) {
        return null;
    }

    @Override
    public IChunkProvider getChunkProvider() {
        return this.chunkProvider;
    }

    @Override
    public EntityPlayer getClosestPlayer(double x, double y, double z, double distance) {
        return null;
    }

    @Override
    public EntityPlayer getPlayerEntityByName(String username) {
        return null;
    }

    @Override
    public <T> List<T> getEntitiesWithinAABB(Class<T> type, AxisAlignedBB bounds) {
        List<T> matches = new ArrayList<T>();
        for (Entity entity : this.queryEntities) {
            if (type.isInstance(entity)
                && bounds.isVecInside(Vec3.createVectorHelper(entity.posX, entity.posY, entity.posZ))) {
                matches.add(type.cast(entity));
            }
        }
        return matches;
    }

    @Override
    public List getEntitiesWithinAABBExcludingEntity(Entity excluded, AxisAlignedBB bounds) {
        return Collections.emptyList();
    }

    @Override
    public boolean spawnEntityInWorld(Entity entity) {
        this.spawned.add(entity);
        return true;
    }

    @Override
    public GameRules getGameRules() {
        return this.gameRules;
    }

    @Override
    public void playSoundEffect(double x, double y, double z, String name, float volume, float pitch) {}

    @Override
    public void markBlockForUpdate(int x, int y, int z) {}

    @Override
    public void notifyBlocksOfNeighborChange(int x, int y, int z, Block block) {}

    @Override
    public void func_147453_f(int x, int y, int z, Block block) {}

    @Override
    public boolean isRaining() {
        return false;
    }

    @Override
    public boolean isThundering() {
        return false;
    }

    @Override
    public MovingObjectPosition rayTraceBlocks(Vec3 from, Vec3 to) {
        return null;
    }

    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = null;
            Class<?> owner = target.getClass();
            while (field == null && owner != null) {
                try {
                    field = owner.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    owner = owner.getSuperclass();
                }
            }
            if (field == null) throw new NoSuchFieldException(fieldName);
            long offset = UNSAFE.objectFieldOffset(field);
            if (field.getType() == int.class) {
                UNSAFE.putInt(target, offset, ((Integer) value).intValue());
            } else {
                UNSAFE.putObject(target, offset, value);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final class RecordingProvider extends WorldProvider {

        @Override
        public String getDimensionName() {
            return "Recording";
        }

        @Override
        public boolean canDoRainSnowIce(Chunk chunk) {
            return true;
        }
    }
}
