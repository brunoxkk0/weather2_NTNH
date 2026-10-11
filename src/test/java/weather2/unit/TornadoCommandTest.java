package weather2.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mojang.authlib.GameProfile;

import sun.misc.Unsafe;
import weather2.CommandWeather2;
import weather2.ServerTickHandler;
import weather2.api.TornadoApi;
import weather2.api.TornadoHandle;
import weather2.api.TornadoOptions;
import weather2.api.event.TornadoFormEvent;
import weather2.api.event.TornadoOrigin;
import weather2.harness.RecordingWorld;
import weather2.util.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

public class TornadoCommandTest {

    private static final Unsafe UNSAFE = unsafe();

    private HashMap oldManagers;
    private List oldWeatherDimensions;
    private List oldStormDimensions;
    private long oldStormId;
    private Thread oldServerThread;
    private CommandWeather2 command;
    private TestSender sender;
    private TestManager manager;
    private RecordingWorld world;

    @Before
    public void setUp() throws Exception {
        this.oldManagers = ServerTickHandler.lookupDimToWeatherMan;
        this.oldWeatherDimensions = WeatherUtilConfig.listDimensionsWeather;
        this.oldStormDimensions = WeatherUtilConfig.listDimensionsStorms;
        this.oldStormId = StormObject.lastUsedStormID;
        this.oldServerThread = serverThread();
        ServerTickHandler.lookupDimToWeatherMan = new HashMap();
        WeatherUtilConfig.listDimensionsWeather = new ArrayList();
        WeatherUtilConfig.listDimensionsStorms = new ArrayList();
        WeatherUtilConfig.listDimensionsWeather.add(Integer.valueOf(0));
        WeatherUtilConfig.listDimensionsStorms.add(Integer.valueOf(0));
        StormObject.lastUsedStormID = 0L;
        setServerThread(Thread.currentThread());
        this.world = RecordingWorld.create();
        this.manager = new TestManager(this.world);
        ServerTickHandler.lookupDimToWeatherMan.put(Integer.valueOf(0), this.manager);
        this.command = new CommandWeather2() {

            @Override
            protected WorldServer getLoadedWorld(int dimension) {
                return dimension == 0 ? TornadoCommandTest.this.world : null;
            }
        };
        this.sender = new TestSender(this.world, true);
    }

    @After
    public void tearDown() throws Exception {
        ServerTickHandler.lookupDimToWeatherMan = this.oldManagers;
        WeatherUtilConfig.listDimensionsWeather = this.oldWeatherDimensions;
        WeatherUtilConfig.listDimensionsStorms = this.oldStormDimensions;
        StormObject.lastUsedStormID = this.oldStormId;
        setServerThread(this.oldServerThread);
    }

    @Test
    public void explicitSpawnWorksForConsoleOrCommandBlockSender() {
        this.command.processCommand(
            this.sender,
            new String[] { "tornado", "spawn", "0", "10.5", "-4", "F2", "600", "false", "true" });

        assertEquals(
            1,
            this.manager.getStormObjects()
                .size());
        StormObject storm = (StormObject) this.manager.getStormObjects()
            .get(0);
        assertEquals(StormObject.STATE_FORMING + 2, storm.levelCurIntensityStage);
        assertFalse(storm.pullPlayers);
        assertTrue(storm.pullLivingEntities);
        assertEquals(1, this.manager.syncNewCount);
        assertTrue(this.sender.contains("created"));
        assertEquals(TornadoOrigin.COMMAND, this.manager.lastOrigin);
    }

    @Test
    public void legacyStormCreateReportsCommandOrigin() throws Exception {
        TestPlayer player = (TestPlayer) UNSAFE.allocateInstance(TestPlayer.class);
        player.worldObj = this.world;
        player.posX = 0.0D;
        player.posZ = 0.0D;
        player.allowed = true;
        player.messages = new ArrayList<String>();

        this.command.processCommand(player, new String[] { "storm", "create", "F1" });

        assertEquals(TornadoOrigin.COMMAND, this.manager.lastOrigin);
        assertTrue(
            player.messages.get(0)
                .contains("tornado created"));
    }

    @Test
    public void listCancelAndPullUseApiOperations() {
        TornadoHandle handle = TornadoApi.spawn(this.world, 0.0D, 0.0D, TornadoOptions.visual(1, 200))
            .get();

        this.command.processCommand(this.sender, new String[] { "tornado", "list", "0" });
        this.command.processCommand(
            this.sender,
            new String[] { "tornado", "pull", "0", Long.toString(handle.getId()), "true", "false" });
        this.command
            .processCommand(this.sender, new String[] { "tornado", "cancel", "0", Long.toString(handle.getId()) });

        assertTrue(this.sender.contains("F1"));
        assertTrue(this.sender.contains("pull"));
        assertTrue(this.sender.contains("cancelled"));
        assertFalse(
            TornadoApi.get(this.world, handle.getId())
                .isPresent());
    }

    @Test
    public void uppercaseListWorksWithTurkishLocale() {
        Locale oldLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));

            this.command.processCommand(this.sender, new String[] { "tornado", "LIST", "0" });

            assertTrue(this.sender.messages.contains("No active tornadoes in dimension 0."));
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    @Test
    public void malformedArgumentsAndInvalidBooleanDoNotMutate() {
        this.command.processCommand(this.sender, new String[] { "tornado", "spawn", "0", "1", "2", "F0" });
        this.command
            .processCommand(this.sender, new String[] { "tornado", "spawn", "0", "1", "2", "F0", "30", "maybe" });
        this.command.processCommand(this.sender, new String[] { "tornado", "cancel", "0", "bad-id" });
        this.command.processCommand(this.sender, new String[] { "tornado", "cancel", "0", "999" });

        assertTrue(
            this.manager.getStormObjects()
                .isEmpty());
        assertEquals(0, this.manager.syncNewCount);
        assertTrue(this.sender.contains("Syntax:"));
        assertTrue(this.sender.contains("true or false"));
        assertTrue(this.sender.contains("No active tornado"));
    }

    @Test
    public void permissionLevelTwoAndDeniedSenderAreHandled() {
        assertEquals(2, this.command.getRequiredPermissionLevel());
        assertTrue(
            this.command.getCommandUsage(this.sender)
                .contains("tornado spawn"));
        assertTrue(
            this.command.addTabCompletionOptions(this.sender, new String[] { "tornado", "" })
                .contains("spawn"));
        this.sender.allowed = false;

        this.command.processCommand(this.sender, new String[] { "tornado", "spawn", "0", "0", "0", "F0", "30" });

        assertTrue(
            this.manager.getStormObjects()
                .isEmpty());
        assertTrue(this.sender.contains("permission"));
    }

    @Test
    public void unavailableDimensionAndVetoAreReportedWithoutCastingSender() {
        this.command.processCommand(this.sender, new String[] { "tornado", "list", "4" });
        assertTrue(this.sender.contains("not loaded"));

        this.sender.messages.clear();
        this.manager.veto = true;
        this.command.processCommand(this.sender, new String[] { "tornado", "spawn", "0", "0", "0", "F1", "30" });

        assertTrue(this.sender.contains("veto"));
        assertTrue(
            this.manager.getStormObjects()
                .isEmpty());
        assertEquals(0, this.manager.syncNewCount);
    }

    private static Thread serverThread() throws Exception {
        java.lang.reflect.Field field = ServerTickHandler.class.getDeclaredField("serverThread");
        field.setAccessible(true);
        return (Thread) field.get(null);
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void setServerThread(Thread thread) throws Exception {
        java.lang.reflect.Field field = ServerTickHandler.class.getDeclaredField("serverThread");
        field.setAccessible(true);
        field.set(null, thread);
    }

    private static final class TestSender implements ICommandSender {

        private final World world;
        private final List<String> messages = new ArrayList<String>();
        private boolean allowed;

        private TestSender(World world, boolean allowed) {
            this.world = world;
            this.allowed = allowed;
        }

        @Override
        public String getCommandSenderName() {
            return "Server/CommandBlock";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return null;
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            this.messages.add(message.getUnformattedText());
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return this.allowed && level <= 2;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 64, 0);
        }

        @Override
        public World getEntityWorld() {
            return this.world;
        }

        private boolean contains(String text) {
            for (String message : this.messages) {
                if (message.toLowerCase()
                    .contains(text.toLowerCase())) return true;
            }
            return false;
        }
    }

    private static final class TestPlayer extends EntityPlayerMP {

        private boolean allowed;
        private List<String> messages;

        private TestPlayer() {
            super((MinecraftServer) null, (WorldServer) null, (GameProfile) null, (ItemInWorldManager) null);
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            this.messages.add(message.getUnformattedText());
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return this.allowed && level <= 2;
        }
    }

    private static final class TestManager extends WeatherManagerServer {

        private final WorldServer world;
        private boolean veto;
        private int syncNewCount;
        private TornadoOrigin lastOrigin;

        private TestManager(WorldServer world) {
            super(0);
            this.world = world;
        }

        @Override
        public World getWorld() {
            return this.world;
        }

        @Override
        public void syncStormNew(StormObject storm) {
            ++this.syncNewCount;
        }

        @Override
        protected void syncStormUpdate(java.util.Set storms) {}

        @Override
        protected void postTornadoForm(TornadoFormEvent event) {
            this.lastOrigin = event.getOrigin();
            if (this.veto) event.setCanceled(true);
        }
    }
}
