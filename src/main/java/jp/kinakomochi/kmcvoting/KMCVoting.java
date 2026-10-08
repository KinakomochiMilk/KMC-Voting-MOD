package jp.kinakomochi.kmcvoting;

import jp.kinakomochi.kmcvoting.api.KMCVotingAPI;
import jp.kinakomochi.kmcvoting.config.VotingConfig;
import jp.kinakomochi.kmcvoting.network.VotifierV2Server;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = KMCVoting.MODID, name = KMCVoting.NAME, version = KMCVoting.VERSION, serverSideOnly = true, acceptableRemoteVersions = "*")
public class KMCVoting {
    public static final String MODID = "kmcvoting";
    public static final String NAME = "KMC Voting";
    public static final String VERSION = "1.0.0";

    private static Logger logger;
    private static VotifierV2Server listener;
    private static MinecraftServer server;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        VotingConfig.load(event.getModConfigurationDirectory());
        KMCVotingAPI.init(VotingConfig.getToken(), VotingConfig.getPublicKeyPem());
        MinecraftForge.EVENT_BUS.register(this);
        logger.info("KMC Voting initialized. Token authentication is enabled.");
        logger.info("RSA public key is available at {}", VotingConfig.getPublicKeyFile());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer();
        event.registerServerCommand(new KMCVotingCommand());
        listener = new VotifierV2Server(VotingConfig.getHost(), VotingConfig.getPort(), VotingConfig.getToken(), this::onVote, logger);
        listener.start();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (listener != null) {
            listener.stop();
            listener = null;
        }
        server = null;
    }

    private void onVote(final KMCVotingAPI.Vote vote) {
        KMCVotingAPI.fireVote(vote);
        if (server == null) return;
        server.addScheduledTask(() -> {
            for (String template : VotingConfig.getCommands()) {
                String command = template.replace("{player}", vote.getUsername())
                        .replace("{service}", vote.getServiceName())
                        .replace("{address}", vote.getAddress())
                        .replace("{timestamp}", String.valueOf(vote.getTimestamp()));
                if (command.startsWith("/")) command = command.substring(1);
                server.getCommandManager().executeCommand(server, command);
            }
        });
    }

    public static Logger getLogger() { return logger; }
    public static MinecraftServer getServer() { return server; }
}
