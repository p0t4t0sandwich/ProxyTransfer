package dev.neuralnexus.proxytransfer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.net.InetSocketAddress;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.velocitypowered.api.command.BrigadierCommand.literalArgumentBuilder;
import static com.velocitypowered.api.command.BrigadierCommand.requiredArgumentBuilder;

import static dev.neuralnexus.proxytransfer.ProxyTransfer.TRANSFER_KEY;

public class TransferCommand {
    public static Command register() {
        LiteralArgumentBuilder<CommandSource> transfer = literalArgumentBuilder("transfer")
                .requires(source -> source.hasPermission("proxytransfer.transfer"))
                .then(requiredArgumentBuilder("player", StringArgumentType.string())
                .then(requiredArgumentBuilder("host", StringArgumentType.string())
                .then(requiredArgumentBuilder("port", IntegerArgumentType.integer())
                .executes(context -> {
                    String player = getString(context, "player");
                    Player target = ProxyTransfer.server().getPlayer(player).orElse(null);
                    if (target == null) {
                        context.getSource().sendMessage(Component.text("Player not found", NamedTextColor.RED));
                        return SINGLE_SUCCESS;
                    }
                    if (target.getCurrentServer().isEmpty()) {
                        context.getSource().sendMessage(Component.text("Player is not connected to a server", NamedTextColor.RED));
                        return SINGLE_SUCCESS;
                    }
                    String host = getString(context, "host");
                    int port = getInteger(context, "port");

                    InetSocketAddress origin = ProxyTransfer.server().getBoundAddress();
                    String playerServer = target.getCurrentServer().get().getServerInfo().getName();

                    TransferData data = new TransferData.Builder()
                            .origin(origin.getAddress().getHostAddress())
                            .port(origin.getPort())
                            .server(playerServer)
                            .build();

                    // Encrypt the data?
                    target.storeCookie(TRANSFER_KEY, data.toBytes());
                    target.transferToHost(new InetSocketAddress(host, port));

                    context.getSource().sendMessage(Component.text("Transferring " + player + " to " + host + ":" + port, NamedTextColor.GREEN));
                    return SINGLE_SUCCESS;
                }))));

        return new BrigadierCommand(transfer);
    }
}
