package weather2.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

import weather2.ServerTickHandler;
import weather2.api.event.TornadoOrigin;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

/** Server-thread API for controlling soft tornadoes. */
public final class TornadoApi {

    private TornadoApi() {}

    public static Optional<TornadoHandle> spawn(WorldServer world, double x, double z, TornadoOptions options) {
        return spawn(world, x, z, options, TornadoOrigin.API);
    }

    public static Optional<TornadoHandle> spawnFromCommand(WorldServer world, double x, double z,
        TornadoOptions options) {
        return spawn(world, x, z, options, TornadoOrigin.COMMAND);
    }

    private static Optional<TornadoHandle> spawn(WorldServer world, double x, double z, TornadoOptions options,
        TornadoOrigin origin) {
        if (world == null || options == null) {
            throw new IllegalArgumentException("World and tornado options are required");
        }
        if (!isFinite(x) || !isFinite(z)
            || x < -30_000_000.0D
            || x >= 30_000_000.0D
            || z < -30_000_000.0D
            || z >= 30_000_000.0D) {
            throw new IllegalArgumentException("Tornado coordinates must be finite and inside world bounds");
        }

        WeatherManagerServer manager = requireManager(world);
        requireMutationAllowed(manager);
        if (!world.getChunkProvider()
            .chunkExists(MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4)) {
            throw new IllegalStateException("Tornado creation chunk is not loaded");
        }
        if (!StormObject.hasAvailableStormID()
            || manager.lookupStormObjectsByID.containsKey(Long.valueOf(StormObject.lastUsedStormID))) {
            throw new IllegalStateException("Weather2 storm ID space is exhausted or inconsistent");
        }

        StormObject storm = manager.spawnControlledTornado(x, z, options, origin);
        return storm == null ? Optional.<TornadoHandle>empty() : Optional.of(new TornadoHandle(manager.dim, storm.ID));
    }

    public static boolean cancel(WorldServer world, long tornadoId) {
        WeatherManagerServer manager = requireManager(world);
        requireMutationAllowed(manager);
        return manager.endTornado(tornadoId, weather2.api.event.TornadoEndReason.CANCELLED);
    }

    public static boolean cancel(WorldServer world, TornadoHandle handle) {
        return cancel(world, requireHandleId(world, handle));
    }

    public static boolean setPull(WorldServer world, long tornadoId, boolean players, boolean livingEntities) {
        WeatherManagerServer manager = requireManager(world);
        requireMutationAllowed(manager);
        return manager.setTornadoPull(tornadoId, players, livingEntities);
    }

    public static boolean setPull(WorldServer world, TornadoHandle handle, boolean players, boolean livingEntities) {
        return setPull(world, requireHandleId(world, handle), players, livingEntities);
    }

    public static Optional<TornadoSnapshot> get(WorldServer world, long tornadoId) {
        WeatherManagerServer manager = requireManager(world);
        StormObject storm = manager.getActiveTornado(tornadoId);
        return storm == null ? Optional.<TornadoSnapshot>empty() : Optional.of(new TornadoSnapshot(manager.dim, storm));
    }

    public static Optional<TornadoSnapshot> get(WorldServer world, TornadoHandle handle) {
        return get(world, requireHandleId(world, handle));
    }

    public static List<TornadoSnapshot> list(WorldServer world) {
        WeatherManagerServer manager = requireManager(world);
        List<TornadoSnapshot> snapshots = new ArrayList<TornadoSnapshot>();
        for (Object object : manager.getStormObjects()) {
            StormObject storm = (StormObject) object;
            if (manager.getActiveTornado(storm.ID) == storm) {
                snapshots.add(new TornadoSnapshot(manager.dim, storm));
            }
        }
        return Collections.unmodifiableList(snapshots);
    }

    private static WeatherManagerServer requireManager(WorldServer world) {
        if (world == null || world.provider == null) {
            throw new IllegalArgumentException("A loaded server world is required");
        }
        if (!ServerTickHandler.isOnServerThread()) {
            throw new IllegalStateException("Tornado API must be called on the server thread");
        }

        int dimension = world.provider.dimensionId;
        if (!WeatherUtilConfig.listDimensionsWeather.contains(Integer.valueOf(dimension))
            || !WeatherUtilConfig.listDimensionsStorms.contains(Integer.valueOf(dimension))) {
            throw new IllegalStateException("Weather and storms are disabled in dimension " + dimension);
        }

        Object registeredManager = ServerTickHandler.lookupDimToWeatherMan.get(Integer.valueOf(dimension));
        if (!(registeredManager instanceof WeatherManagerServer)) {
            throw new IllegalStateException("Weather2 has no registered manager for this loaded world");
        }
        WeatherManagerServer manager = (WeatherManagerServer) registeredManager;
        if (manager.getWorld() != world) {
            throw new IllegalStateException("Weather2 has no registered manager for this loaded world");
        }
        return manager;
    }

    private static void requireMutationAllowed(WeatherManagerServer manager) {
        if (manager.isDispatchingTornadoEvent()) {
            throw new IllegalStateException("Tornado API mutations are not allowed during tornado events");
        }
    }

    private static long requireHandleId(WorldServer world, TornadoHandle handle) {
        if (world == null || world.provider == null || handle == null) {
            throw new IllegalArgumentException("A world and tornado handle are required");
        }
        if (handle.getDimensionId() != world.provider.dimensionId) {
            throw new IllegalArgumentException("Tornado handle belongs to dimension " + handle.getDimensionId());
        }
        return handle.getId();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
