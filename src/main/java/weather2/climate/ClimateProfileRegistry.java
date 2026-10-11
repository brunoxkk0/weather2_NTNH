package weather2.climate;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * NTNH Typed Runtime Registry for Climate Profiles.
 * Provides thread-safe, zero-allocation O(1) profile resolution in hot-path simulation.
 * Supports layered registration:
 * 1. Safe Overworld default.
 * 2. Configuration overrides (Dimension_Weather_Profiles in Misc.cfg).
 * 3. Runtime API overrides.
 */
public class ClimateProfileRegistry {

    private static final ClimateProfile[] fastProfiles = new ClimateProfile[256];
    private static final ConcurrentHashMap<Integer, ClimateProfile> extendedProfiles = new ConcurrentHashMap<Integer, ClimateProfile>();
    private static final ConcurrentHashMap<String, ClimateProfile> namedProfiles = new ConcurrentHashMap<String, ClimateProfile>();
    private static final Logger LOGGER = Logger.getLogger("weather2");
    private static final String LEGACY_DEFAULT_PROFILES = "0, 5, false, 3600, 0.25, 25, 0, true; "
        + "duna, 9, false, 1800, 1.0, 10, 0, true; "
        + "eve, 9, true, 800, 6.0, 3, 7, true; "
        + "laythe, 9, false, 1800, 1.0, 10, 20, true; "
        + "tekto, 8, false, 1800, 2.0, 10, 0, true";

    private static volatile boolean initialized = false;
    private static boolean legacyConfigWarningLogged = false;
    private static String lastLoadedConfig = null;

    /**
     * Initializes the registry with the safe default profile if not yet initialized.
     */
    public static synchronized void ensureInitialized() {
        if (!initialized) {
            registerDefaults();
            initialized = true;
        }
    }

    /**
     * Registers the default profile for a new installation.
     */
    public static synchronized void registerDefaults() {
        register(
            ClimateProfile.builder("overworld")
                .dim(ClimateEngine.DIM_OVERWORLD)
                .weatherEnabled(true)
                .exactStage(5)
                .alwaysProgresses(false)
                .deadlyCooldown(72000)
                .lightningMultiplier(0.25F)
                .spawnOdds(30, 0)
                .build());
    }

    /**
     * Registers or overrides a climate profile in the runtime registry.
     * O(1) thread-safe registration.
     */
    public static void register(ClimateProfile profile) {
        if (profile == null) return;
        int dim = profile.dim;

        // Resolve dim if needed
        if (dim == -999 && profile.name != null) {
            dim = ClimateEngine.resolveDimensionId(profile.name);
        }

        if (dim >= 0 && dim < 256) {
            fastProfiles[dim] = profile;
        } else {
            extendedProfiles.put(dim, profile);
        }

        if (profile.name != null && !profile.name.isEmpty()) {
            namedProfiles.put(profile.name.toLowerCase(Locale.ROOT), profile);
        }

    }

    /**
     * Retrieves the climate profile for a dimension.
     * Guaranteed O(1) without memory allocations in hot path.
     */
    public static ClimateProfile get(int dim) {
        ensureInitialized();
        if (dim >= 0 && dim < 256) {
            ClimateProfile p = fastProfiles[dim];
            if (p != null) return p;
        }
        ClimateProfile p = extendedProfiles.get(dim);
        if (p != null) return p;
        return ClimateProfile.createDefault(dim);
    }

    /**
     * Retrieves profile by symbolic planet name (e.g. "eve", "duna").
     */
    public static ClimateProfile get(String name) {
        ensureInitialized();
        if (name == null) return null;
        return namedProfiles.get(
            name.trim()
                .toLowerCase(Locale.ROOT));
    }

    public static boolean has(int dim) {
        ensureInitialized();
        if (dim >= 0 && dim < 256) {
            return fastProfiles[dim] != null;
        }
        return extendedProfiles.containsKey(dim);
    }

    @Deprecated
    public static boolean isGrabAllowed(int dim) {
        return false;
    }

    public static Collection<ClimateProfile> getAll() {
        ensureInitialized();
        java.util.List<ClimateProfile> list = new java.util.ArrayList<ClimateProfile>();
        for (int i = 0; i < 256; i++) {
            if (fastProfiles[i] != null) list.add(fastProfiles[i]);
        }
        list.addAll(extendedProfiles.values());
        return Collections.unmodifiableCollection(list);
    }

    /**
     * Parses and applies configuration overrides from Misc.cfg string.
     * Retains old profile layouts while ignoring their block-grab setting.
     */
    public static synchronized void loadFromConfig(String cfg) {
        ensureInitialized();
        if (cfg == null || cfg.trim()
            .isEmpty()) {
            cfg = ClimateEngine.DEFAULT_WEATHER_PROFILES;
        }
        if (cfg.equals(lastLoadedConfig)) {
            return;
        }
        String configKey = cfg;
        if (normalize(cfg).equals(normalize(LEGACY_DEFAULT_PROFILES))) {
            cfg = ClimateEngine.DEFAULT_WEATHER_PROFILES;
            warnLegacyConfigIgnored();
        }

        String[] lines = cfg.split("[;\\r\\n]+");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split(",");
            if (parts.length < 6) continue;

            String dimToken = parts[0].trim();
            int dim = ClimateEngine.resolveDimensionId(dimToken);
            if (dim == -999) {
                ClimateProfile existing = namedProfiles.get(dimToken.toLowerCase(Locale.ROOT));
                if (existing != null) {
                    dim = existing.dim;
                }
            }
            if (dim == -999) continue;

            String stageToken = parts[1].trim();
            int rawStage = 0;
            try {
                rawStage = Integer.parseInt(stageToken);
            } catch (Throwable ignored) {}

            boolean alwaysProgresses = false;
            int deadlyCooldown = 1800;
            float lightningMul = 1.0F;
            int landSpawnOdds = 10;
            int oceanSpawnOdds = 0;
            String legacyGrabField = null;

            if (parts.length >= 8) {
                try {
                    alwaysProgresses = Boolean.parseBoolean(parts[2].trim());
                } catch (Throwable ignored) {}
                try {
                    deadlyCooldown = Integer.parseInt(parts[3].trim());
                } catch (Throwable ignored) {}
                try {
                    lightningMul = Float.parseFloat(parts[4].trim());
                } catch (Throwable ignored) {}
                try {
                    landSpawnOdds = Integer.parseInt(parts[5].trim());
                } catch (Throwable ignored) {}
                try {
                    oceanSpawnOdds = Integer.parseInt(parts[6].trim());
                } catch (Throwable ignored) {}
                try {
                    legacyGrabField = parts[7].trim();
                } catch (Throwable ignored) {}
            } else if (parts.length >= 6) {
                try {
                    deadlyCooldown = Integer.parseInt(parts[2].trim());
                } catch (Throwable ignored) {}
                try {
                    lightningMul = Float.parseFloat(parts[3].trim());
                } catch (Throwable ignored) {}
                try {
                    landSpawnOdds = Integer.parseInt(parts[4].trim());
                } catch (Throwable ignored) {}
                try {
                    legacyGrabField = parts[5].trim();
                } catch (Throwable ignored) {}
                alwaysProgresses = (rawStage >= 9 && deadlyCooldown <= 1000);
                if (dimToken.equalsIgnoreCase("laythe") || dimToken.equalsIgnoreCase("eve")) {
                    oceanSpawnOdds = landSpawnOdds * 2;
                }
            }
            if (Boolean.parseBoolean(legacyGrabField)) {
                warnLegacyConfigIgnored();
            }

            ClimateProfile.Builder b = ClimateProfile.builder(dim, dimToken)
                .weatherEnabled(true)
                .parseStage(stageToken)
                .alwaysProgresses(alwaysProgresses)
                .deadlyCooldown(deadlyCooldown)
                .lightningMultiplier(lightningMul)
                .spawnOdds(landSpawnOdds, oceanSpawnOdds);

            // Special-case legacy Overworld stage 0 in config -> migrate to localized F1 (stage 5)
            if ("0".equals(stageToken)
                && (dim == 0 || dimToken.equalsIgnoreCase("overworld") || dimToken.equals("0"))) {
                b.exactStage(5);
            }

            register(b.build());
        }

        lastLoadedConfig = configKey;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "");
    }

    private static void warnLegacyConfigIgnored() {
        if (!legacyConfigWarningLogged) {
            LOGGER.warning(
                "Weather2 ignored legacy tornado block-grab settings and migrated bundled modpack profiles to safe defaults.");
            legacyConfigWarningLogged = true;
        }
    }

    /**
     * Clears all profiles and reloads defaults.
     */
    public static synchronized void reset() {
        for (int i = 0; i < 256; i++) {
            fastProfiles[i] = null;
        }
        extendedProfiles.clear();
        namedProfiles.clear();
        lastLoadedConfig = null;
        initialized = false;
        ensureInitialized();
    }
}
