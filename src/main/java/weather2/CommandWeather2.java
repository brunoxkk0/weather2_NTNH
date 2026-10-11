package weather2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import CoroUtil.util.CoroUtil;
import CoroUtil.util.CoroUtilEntity;
import weather2.api.TornadoApi;
import weather2.api.TornadoHandle;
import weather2.api.TornadoOptions;
import weather2.api.TornadoSnapshot;
import weather2.climate.ClimateProfile;
import weather2.config.ConfigMisc;
import weather2.integration.AdapterReport;
import weather2.integration.AdapterStatus;
import weather2.integration.IntegrationManager;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

public class CommandWeather2 extends CommandBase {

    public String getCommandName() {
        return "weather2";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    public void processCommand(ICommandSender var1, String[] var2) {
        // NTNH start: command aliases normalization (/weather2 kill, /weather2 spawn, etc.)
        var2 = weather2.compat.WeatherNTNHHooks.normalizeCommandArgs(var2);
        // NTNH end
        if (var2 != null && var2.length > 0
            && (var2[0].equalsIgnoreCase("status") || var2[0].equalsIgnoreCase("diag")
                || var2[0].equalsIgnoreCase("diagnostics"))) {
            sendDiagnostics(var1);
            return;
        }

        if (var2 != null && var2.length > 0 && var2[0].equalsIgnoreCase("volcano")) {
            var1.addChatMessage(new ChatComponentText("Volcanoes are disabled in this version."));
            return;
        }

        if (var2 != null && var2.length > 0 && var2[0].equalsIgnoreCase("tornado")) {
            this.processTornadoCommand(var1, var2);
            return;
        }

        String helpMsgStorm = "Syntax: storm create <rain/thunder/wind/spout/hail/F0..F5/C0..C5> | storm killall | tornado <spawn/list/cancel/pull>";

        try {
            if (var1 instanceof EntityPlayerMP) {
                EntityPlayerMP ex = getCommandSenderAsPlayer(var1);
                WeatherManagerServer wm;
                if (var2.length > 0 && var2[0].equalsIgnoreCase("storm")) {
                    if (var2.length > 1 && var2[1].equalsIgnoreCase("killAll")) {
                        wm = (WeatherManagerServer) ServerTickHandler.lookupDimToWeatherMan
                            .get(Integer.valueOf(ex.worldObj.provider.dimensionId));
                        if (wm == null) {
                            CoroUtil.sendPlayerMsg(ex, "Weather is unavailable in this dimension.");
                        } else {
                            List storms = new ArrayList(wm.getStormObjects());
                            for (int i = 0; i < storms.size(); ++i) {
                                wm.removeStormSafely(((StormObject) storms.get(i)).ID);
                            }
                            CoroUtil.sendPlayerMsg(ex, "cleared all storms");
                        }
                    } else if (var2.length < 2
                        || (!var2[1].equalsIgnoreCase("create") && !var2[1].equalsIgnoreCase("spawn"))) {
                            if (var2.length > 1 && var2[1].equalsIgnoreCase("help")) {
                                CoroUtil.sendPlayerMsg((EntityPlayerMP) var1, helpMsgStorm);
                            } else {
                                CoroUtil.sendPlayerMsg((EntityPlayerMP) var1, helpMsgStorm);
                            }
                        } else if (var2.length > 2) {
                            int tornadoIntensity = this.parseFujitaIntensity(var2[2]);
                            if (tornadoIntensity >= 0) {
                                try {
                                    Optional<TornadoHandle> created = TornadoApi.spawnFromCommand(
                                        (WorldServer) ex.worldObj,
                                        ex.posX,
                                        ex.posZ,
                                        TornadoOptions
                                            .visual(tornadoIntensity, ConfigMisc.Tornado_DefaultDurationTicks));
                                    CoroUtil.sendPlayerMsg(
                                        ex,
                                        created.isPresent() ? "tornado created, ID: " + created.get()
                                            .getId() : "tornado formation was vetoed by an integration");
                                } catch (IllegalArgumentException | IllegalStateException error) {
                                    CoroUtil.sendPlayerMsg(ex, error.getMessage());
                                }
                                return;
                            }

                            int requestedStage = this.legacyStormStage(var2[2]);
                            if (requestedStage < 0) {
                                CoroUtil.sendPlayerMsg(ex, helpMsgStorm);
                                return;
                            }
                            wm = (WeatherManagerServer) ServerTickHandler.lookupDimToWeatherMan
                                .get(Integer.valueOf(ex.worldObj.provider.dimensionId));
                            if (wm == null) {
                                CoroUtil.sendPlayerMsg(ex, "Weather is unavailable in this dimension.");
                                return;
                            }
                            StormObject var11 = new StormObject(wm);
                            var11.layer = 0;
                            var11.userSpawnedFor = CoroUtilEntity.getName(ex);
                            var11.naturallySpawned = false;
                            var11.levelTemperature = 0.1F;
                            var11.pos = Vec3.createVectorHelper(
                                ex.posX,
                                (double) ((Integer) StormObject.layers.get(var11.layer)).intValue(),
                                ex.posZ);
                            var11.levelWater = var11.levelWaterStartRaining * 2;
                            var11.attrib_precipitation = true;
                            if (!var2[2].equals("rain")) {
                                var11.initRealStorm((EntityPlayer) null, (StormObject) null);
                            }

                            var11.levelCurIntensityStage = requestedStage;
                            if ((var2[2].charAt(0) == 'C' || var2[2].charAt(0) == 'c')
                                || var2[2].equalsIgnoreCase("hurricane")) {
                                var11.stormType = StormObject.TYPE_WATER;
                            }
                            if (var2[2].equalsIgnoreCase("full")) {
                                var11.alwaysProgresses = true;
                            }
                            if (var2[2].equalsIgnoreCase("spout")) {
                                var11.attrib_waterSpout = true;
                            }

                            if (var2.length > 3 && (var2[3].contains("Progress") || var2[3].contains("progress"))) {
                                var11.alwaysProgresses = true;
                            }

                            var11.initFirstTime();
                            if (var11.levelCurIntensityStage >= StormObject.STATE_FORMING) {
                                if (!var11.setIntensityStage(
                                    var11.levelCurIntensityStage,
                                    weather2.api.event.TornadoOrigin.COMMAND)) {
                                    CoroUtil.sendPlayerMsg(ex, "tornado formation was vetoed by an integration");
                                    return;
                                }
                            }
                            wm.addStormObject(var11);
                            wm.syncStormNew(var11);
                            CoroUtil.sendPlayerMsg(
                                (EntityPlayerMP) var1,
                                "storm " + var2[2]
                                    + " created"
                                    + (var11.alwaysProgresses ? ", flags: alwaysProgresses" : ""));
                        } else {
                            CoroUtil.sendPlayerMsg((EntityPlayerMP) var1, helpMsgStorm);
                        }
                } else {
                    CoroUtil.sendPlayerMsg((EntityPlayerMP) var1, helpMsgStorm);
                }
            }
        } catch (Exception var9) {
            System.out.println("Exception handling Weather2 command");
            CoroUtil.sendPlayerMsg((EntityPlayerMP) var1, helpMsgStorm);
            var9.printStackTrace();
        }

    }

    private void processTornadoCommand(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(this.getRequiredPermissionLevel(), this.getCommandName())) {
            sender
                .addChatMessage(new ChatComponentText("You do not have permission to use Weather2 tornado commands."));
            return;
        }

        try {
            if (args.length < 2) throw new IllegalArgumentException("Syntax: " + this.getCommandUsage(sender));
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("spawn")) {
                if (args.length < 7 || args.length > 9) {
                    throw new IllegalArgumentException(
                        "Syntax: /weather2 tornado spawn <dim> <x> <z> <F0..F5> <durationTicks> [pullPlayers] [pullLivingEntities]");
                }
                int dimension = Integer.parseInt(args[2]);
                double x = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                int intensity = this.parseFujitaIntensity(args[5]);
                if (intensity < 0) throw new IllegalArgumentException("Intensity must be F0 through F5.");
                int duration = Integer.parseInt(args[6]);
                boolean pullPlayers = args.length > 7 && this.parseStrictBoolean(args[7]);
                boolean pullLiving = args.length > 8 && this.parseStrictBoolean(args[8]);
                WorldServer world = this.getLoadedWorld(dimension);
                if (world == null) throw new IllegalStateException("Dimension " + dimension + " is not loaded.");
                Optional<TornadoHandle> created = TornadoApi
                    .spawnFromCommand(world, x, z, new TornadoOptions(intensity, duration, pullPlayers, pullLiving));
                sender.addChatMessage(
                    new ChatComponentText(
                        created.isPresent() ? "Tornado created with ID " + created.get()
                            .getId() + "." : "Tornado formation was vetoed by an integration."));
            } else if (action.equals("list")) {
                if (args.length != 3) throw new IllegalArgumentException("Syntax: /weather2 tornado list <dim>");
                int dimension = Integer.parseInt(args[2]);
                WorldServer world = this.getLoadedWorld(dimension);
                if (world == null) throw new IllegalStateException("Dimension " + dimension + " is not loaded.");
                List<TornadoSnapshot> tornadoes = TornadoApi.list(world);
                if (tornadoes.isEmpty()) {
                    sender.addChatMessage(new ChatComponentText("No active tornadoes in dimension " + dimension + "."));
                } else {
                    for (TornadoSnapshot tornado : tornadoes) {
                        sender.addChatMessage(new ChatComponentText(this.formatTornado(tornado)));
                    }
                }
            } else if (action.equals("cancel")) {
                if (args.length != 4) throw new IllegalArgumentException("Syntax: /weather2 tornado cancel <dim> <id>");
                int dimension = Integer.parseInt(args[2]);
                long id = Long.parseLong(args[3]);
                WorldServer world = this.getLoadedWorld(dimension);
                if (world == null) throw new IllegalStateException("Dimension " + dimension + " is not loaded.");
                sender.addChatMessage(
                    new ChatComponentText(
                        TornadoApi.cancel(world, id) ? "Tornado cancelled." : "No active tornado with that ID."));
            } else if (action.equals("pull")) {
                if (args.length != 6) throw new IllegalArgumentException(
                    "Syntax: /weather2 tornado pull <dim> <id> <true|false> <true|false>");
                int dimension = Integer.parseInt(args[2]);
                long id = Long.parseLong(args[3]);
                boolean players = this.parseStrictBoolean(args[4]);
                boolean living = this.parseStrictBoolean(args[5]);
                WorldServer world = this.getLoadedWorld(dimension);
                if (world == null) throw new IllegalStateException("Dimension " + dimension + " is not loaded.");
                sender.addChatMessage(
                    new ChatComponentText(
                        TornadoApi.setPull(world, id, players, living) ? "Tornado pull updated."
                            : "No active tornado with that ID."));
            } else {
                throw new IllegalArgumentException("Syntax: /weather2 tornado <spawn|list|cancel|pull> ...");
            }
        } catch (IllegalArgumentException error) {
            sender.addChatMessage(
                new ChatComponentText(error.getMessage() == null ? "Invalid tornado command." : error.getMessage()));
        } catch (IllegalStateException error) {
            sender.addChatMessage(new ChatComponentText(error.getMessage()));
        }
    }

    protected WorldServer getLoadedWorld(int dimension) {
        return DimensionManager.getWorld(dimension);
    }

    private int parseFujitaIntensity(String value) {
        if (value == null || value.length() != 2 || (value.charAt(0) != 'F' && value.charAt(0) != 'f')) return -1;
        char level = value.charAt(1);
        return level >= '0' && level <= '5' ? level - '0' : -1;
    }

    private boolean parseStrictBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Boolean options must be true or false.");
    }

    private String formatTornado(TornadoSnapshot tornado) {
        return String.format(
            "Tornado %d: F%d at %.1f, %.1f, %.1f; %d ticks; pull players=%b, entities=%b",
            tornado.getHandle()
                .getId(),
            tornado.getIntensity(),
            tornado.getX(),
            tornado.getY(),
            tornado.getZ(),
            tornado.getRemainingTicks(),
            tornado.isPullPlayers(),
            tornado.isPullLivingEntities());
    }

    private int legacyStormStage(String type) {
        if (type.equalsIgnoreCase("rain")) return StormObject.STATE_NORMAL;
        if (type.equalsIgnoreCase("thunder") || type.equalsIgnoreCase("lightning")
            || type.equalsIgnoreCase("full")
            || type.equalsIgnoreCase("test")) return StormObject.STATE_THUNDER;
        if (type.equalsIgnoreCase("wind") || type.equalsIgnoreCase("spout")) return StormObject.STATE_HIGHWIND;
        if (type.equalsIgnoreCase("hail")) return StormObject.STATE_HAIL;
        int intensity = this.parseFujitaIntensity(type);
        if (intensity >= 0) return StormObject.STATE_FORMING + intensity;
        if (type.length() == 2 && (type.charAt(0) == 'C' || type.charAt(0) == 'c')) {
            char level = type.charAt(1);
            if (level >= '0' && level <= '5') return StormObject.STATE_FORMING + level - '0';
        }
        if (type.equalsIgnoreCase("hurricane")) return StormObject.STATE_STAGE5;
        return -1;
    }

    private void sendDiagnostics(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GOLD + "=== Weather2 / NTNH Integration Diagnostics ==="));
        for (AdapterReport report : IntegrationManager.diagnoseAll()) {
            EnumChatFormatting color = EnumChatFormatting.GREEN;
            if (report.getStatus() == AdapterStatus.DEGRADED) {
                color = EnumChatFormatting.YELLOW;
            } else if (report.getStatus() == AdapterStatus.FAILED) {
                color = EnumChatFormatting.RED;
            } else if (report.getStatus() == AdapterStatus.NOT_INSTALLED) {
                color = EnumChatFormatting.GRAY;
            }

            sender.addChatMessage(
                new ChatComponentText(
                    color + String.format("[%s] ", report.getStatus())
                        + EnumChatFormatting.WHITE
                        + report.getAdapterName()
                        + ": "
                        + report.getDetails()));
            if (report.isFallbackActive()) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.YELLOW + "  -> Fallback: " + report.getFallbackDescription()));
            }
        }

        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            int dim = player.worldObj.provider.dimensionId;
            ClimateProfile profile = weather2.compat.WeatherNTNHHooks.getProfile(dim);
            WeatherManagerServer manager = (WeatherManagerServer) ServerTickHandler.lookupDimToWeatherMan
                .get(Integer.valueOf(dim));
            int activeTornadoes = 0;
            if (manager != null) {
                for (Object object : manager.getStormObjects()) {
                    StormObject storm = (StormObject) object;
                    if (manager.getActiveTornado(storm.ID) == storm) ++activeTornadoes;
                }
            }
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + String.format(
                        "Dim %d (%s): weather=%b, naturalMaxStage=%d, cooldown=%d, activeTornadoes=%d",
                        dim,
                        profile.name,
                        WeatherUtilConfig.listDimensionsWeather.contains(Integer.valueOf(dim)),
                        profile.maxStage,
                        profile.deadlyCooldown,
                        activeTornadoes)));
        }
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GOLD + "================================================"));
    }

    public boolean canCommandSenderUseCommand(ICommandSender par1ICommandSender) {
        return par1ICommandSender.canCommandSenderUseCommand(this.getRequiredPermissionLevel(), this.getCommandName());
    }

    public String getCommandUsage(ICommandSender icommandsender) {
        return "/weather2 tornado spawn <dim> <x> <z> <F0..F5> <durationTicks> [pullPlayers] [pullLivingEntities] | list <dim> | cancel <dim> <id> | pull <dim> <id> <true|false> <true|false>";
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, new String[] { "tornado", "storm", "status", "diag" });
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tornado")) {
            return getListOfStringsMatchingLastWord(args, new String[] { "spawn", "list", "cancel", "pull" });
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("tornado") && args[1].equalsIgnoreCase("spawn")) {
            return getListOfStringsMatchingLastWord(args, new String[] { "F0", "F1", "F2", "F3", "F4", "F5" });
        }
        if ((args.length == 8 || args.length == 9) && args[0].equalsIgnoreCase("tornado")
            && args[1].equalsIgnoreCase("spawn")) {
            return getListOfStringsMatchingLastWord(args, new String[] { "true", "false" });
        }
        return null;
    }
}
