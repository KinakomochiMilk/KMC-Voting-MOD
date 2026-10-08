package jp.kinakomochi.kmcvoting;

import jp.kinakomochi.kmcvoting.api.KMCVotingAPI;
import jp.kinakomochi.kmcvoting.config.VotingConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class KMCVotingCommand extends CommandBase {

    @Override
    public String getName() {
        return "kmcvoting";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/kmcvoting <status|key|token|test>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "status";

        if ("status".equals(sub)) {

            sender.sendMessage(new TextComponentString(
                    "KMC Voting: port=" + VotingConfig.getPort()
                            + ", token configured, RSA public key="
                            + VotingConfig.getPublicKeyFile().getPath()
            ));

        } else if ("key".equals(sub)) {

            sender.sendMessage(new TextComponentString(
                    VotingConfig.getPublicKeyPem()
            ));

        } else if ("token".equals(sub)) {

            sender.sendMessage(new TextComponentString(
                    "KMC Voting token: " + VotingConfig.getToken()
            ));

        } else if ("test".equals(sub)) {

            if (!(sender instanceof net.minecraft.entity.player.EntityPlayer)) {
                sender.sendMessage(new TextComponentString(
                        "このコマンドはゲーム内のプレイヤーから実行してください。"
                ));
                return;
            }

            net.minecraft.entity.player.EntityPlayer player =
                    (net.minecraft.entity.player.EntityPlayer) sender;

            KMCVotingAPI.fireVote(
                    new KMCVotingAPI.Vote(
                            player.getName(),
                            "127.0.0.1",
                            System.currentTimeMillis(),
                            "KMCVoting-Test"
                    )
            );

            sender.sendMessage(new TextComponentString(
                    "KMC Voting test vote sent."
            ));

        } else {

            sender.sendMessage(new TextComponentString(
                    getUsage(sender)
            ));
        }
    }
}