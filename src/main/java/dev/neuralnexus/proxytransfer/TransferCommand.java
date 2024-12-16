package dev.neuralnexus.proxytransfer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.velocitypowered.api.command.BrigadierCommand.literalArgumentBuilder;
import static com.velocitypowered.api.command.BrigadierCommand.requiredArgumentBuilder;

import static dev.neuralnexus.proxytransfer.ProxyTransfer.TRANSFER_KEY;

public class TransferCommand {
    public static LiteralCommandNode<CommandSource> commandNode() {
        LiteralArgumentBuilder<CommandSource> transfer = literalArgumentBuilder("transfer")
                .requires(source -> source.hasPermission("proxytransfer.transfer"));
        RequiredArgumentBuilder<CommandSource, String> player = requiredArgumentBuilder("player", StringArgumentType.word());
        RequiredArgumentBuilder<CommandSource, String> host = requiredArgumentBuilder("host", StringArgumentType.word());
        RequiredArgumentBuilder<CommandSource, Integer> port = requiredArgumentBuilder("port", IntegerArgumentType.integer(0, 0xFFFF));
        Function<CommandContext<CommandSource>, Integer> run = context -> {
            String playerArg = getString(context, "player");
            Player target = ProxyTransfer.server().getPlayer(playerArg).orElse(null);
            if (target == null) {
                context.getSource().sendMessage(Component.text("Player not found", NamedTextColor.RED));
                return SINGLE_SUCCESS;
            }
            if (target.getCurrentServer().isEmpty()) {
                context.getSource().sendMessage(Component.text("Player is not connected to a server", NamedTextColor.RED));
                return SINGLE_SUCCESS;
            }
            String hostArg = getString(context, "host");
            int portArg = getInteger(context, "port");

            InetSocketAddress origin = ProxyTransfer.server().getBoundAddress();
            String playerServer = target.getCurrentServer().get().getServerInfo().getName();

            TransferData data = new TransferData.Builder()
                    .origin(origin.getAddress().getHostAddress())
                    .port(origin.getPort())
                    .server(playerServer)
                    .build();

            // Encrypt the data?
            target.storeCookie(TRANSFER_KEY, data.toBytes());
            target.transferToHost(new InetSocketAddress(hostArg, portArg));

            context.getSource().sendMessage(Component.text("Transferring " + player + " to " + host + ":" + port, NamedTextColor.GREEN));
            return SINGLE_SUCCESS;
        };

        return transfer.then(
                player.then(
                    host.then(
                        port.executes(run::apply)
                ))).build();
    }
}
