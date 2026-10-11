package jp.kinakomochi.kmcvoting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import jp.kinakomochi.kmcvoting.api.KMCVotingAPI;
import jp.kinakomochi.kmcvoting.config.VotingConfig;
import jp.kinakomochi.kmcvoting.network.VotifierServer;

@Mod(modid = KMCVoting.MOD_ID, name = KMCVoting.MOD_NAME, version = KMCVoting.VERSION)
public final class KMCVoting {

    public static final String MOD_ID = "kmcvoting";
    public static final String MOD_NAME = "KMC Voting";
    public static final String VERSION = "1.0.0";

    private static final Logger LOGGER = Logger.getLogger("KMCVoting");

    private final ConcurrentLinkedQueue<KMCVotingAPI.Vote> voteQueue = new ConcurrentLinkedQueue<KMCVotingAPI.Vote>();

    private VotingConfig config;
    private VotifierServer votifierServer;

    private final KMCVotingAPI.VoteListener commandVoteListener = new KMCVotingAPI.VoteListener() {

        @Override
        public void onVote(KMCVotingAPI.Vote vote) {
            voteQueue.offer(vote);
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configDirectory = event.getModConfigurationDirectory();
        config = new VotingConfig(configDirectory);

        try {
            config.load();

            String publicKeyPem = new String(
                Files.readAllBytes(
                    config.getPublicKeyFile()
                        .toPath()),
                StandardCharsets.US_ASCII);

            KMCVotingAPI.init(config.getToken(), publicKeyPem);
            LOGGER.info("KMCVoting configuration loaded.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load KMCVoting configuration.", e);
            throw new RuntimeException("KMCVoting initialization failed.", e);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);

        KMCVotingAPI.registerListener(commandVoteListener);
        LOGGER.info("KMCVoting command listener registered.");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (config == null) {
            LOGGER.severe("KMCVoting configuration is unavailable.");
            return;
        }

        try {
            votifierServer = new VotifierServer(
                config.getHost(),
                config.getPort(),
                config.getToken(),
                config.getPrivateKeyFile(),
                new VotifierServer.VoteReceiver() {

                    @Override
                    public void onVote(String username, String address, long timestamp, String serviceName) {
                        KMCVotingAPI.fireVote(KMCVotingAPI.createVote(username, address, timestamp, serviceName));
                    }
                });

            votifierServer.start();
            LOGGER.info("KMCVoting V1/V2 server started.");
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Could not start the Votifier server.", e);
            votifierServer = null;
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        stopVotifierServer();
        voteQueue.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();

        if (server == null) {
            return;
        }

        KMCVotingAPI.Vote vote;
        while ((vote = voteQueue.poll()) != null) {
            executeVoteCommands(server, vote);
        }
    }

    private void executeVoteCommands(MinecraftServer server, KMCVotingAPI.Vote vote) {
        Map<String, String> commands = config.getCommands();

        String defaultCommand = commands.get("default");
        if (defaultCommand != null && !defaultCommand.trim()
            .isEmpty()) {
            executeCommand(server, defaultCommand, vote);
        }

        String serviceName = vote.getServiceName();
        if (serviceName != null && !serviceName.equals("default")) {
            String serviceCommand = commands.get(serviceName);

            if (serviceCommand != null && !serviceCommand.trim()
                .isEmpty()) {
                executeCommand(server, serviceCommand, vote);
            }
        }
    }

    private void executeCommand(MinecraftServer server, String template, KMCVotingAPI.Vote vote) {
        String command = template.replace("{username}", safeValue(vote.getUsername()))
            .replace("{serviceName}", safeValue(vote.getServiceName()))
            .replace("{address}", safeValue(vote.getAddress()))
            .replace("{timestamp}", Long.toString(vote.getTimestamp()));

        command = command.trim();

        if (command.isEmpty()) {
            return;
        }

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        try {
            int result = server.getCommandManager()
                .executeCommand(server, command);
            LOGGER.info("Vote command executed. Result: " + result);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to execute vote command.", e);
        }
    }

    private String safeValue(String value) {
        if (value == null) {
            return "";
        }

        // Prevent multiline input from becoming multiple commands.
        return value.replace("\r", "")
            .replace("\n", "");
    }

    private void stopVotifierServer() {
        if (votifierServer != null) {
            votifierServer.stop();
            votifierServer = null;
            LOGGER.info("KMCVoting server stopped.");
        }
    }
}
