package dev.neuralnexus.proxytransfer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import dev.neuralnexus.proxytransfer.api.TransferAPI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TransferCommand {
    private final ProxyServer server;
    private static final String PLAYER_ARG = "player";
    private static final String HOST_ARG = "host";
    private static final String PORT_ARG = "port";

    public TransferCommand(ProxyServer server) {
        this.server = server;
    }

    public BrigadierCommand register() {
        final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
                .literalArgumentBuilder("transfer")
                .requires(this::checkPermission);
        final RequiredArgumentBuilder<CommandSource, String> playerNode = BrigadierCommand
                .requiredArgumentBuilder(PLAYER_ARG, StringArgumentType.word())
                .suggests(this::getPlayerSuggestions);
        final RequiredArgumentBuilder<CommandSource, String> hostNode = BrigadierCommand
                .requiredArgumentBuilder(HOST_ARG, StringArgumentType.word());
        final RequiredArgumentBuilder<CommandSource, Integer> portNode = BrigadierCommand
                .requiredArgumentBuilder(PORT_ARG, IntegerArgumentType.integer(0, 0xFFFF))
                .executes(this::run);

        hostNode.then(portNode.build());
        playerNode.then(hostNode.build());
        rootNode.then(playerNode.build());

        return new BrigadierCommand(rootNode);
    }

    private boolean checkPermission(CommandSource source) {
        return source.getPermissionValue("proxytransfer.transfer") == Tristate.TRUE;
    }

    private CompletableFuture<Suggestions> getPlayerSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder builder)  {
        final String argument = context.getArguments().containsKey(PLAYER_ARG)
                ? context.getArgument(PLAYER_ARG, String.class)
                : "";
        for (final Player p : this.server.getAllPlayers()) {
            final String playerName = p.getUsername();
            if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
                builder.suggest(playerName);
            }
            if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
                builder.suggest("all");
            }
        }
        return builder.buildFuture();
    }

    private Component transfer(Player player, String host, int port) {
        InetSocketAddress origin = this.server.getBoundAddress();
        String playerServer;
        if (player.getCurrentServer().isPresent()) {
            playerServer = player.getCurrentServer().get().getServerInfo().getName();
        } else {
            playerServer = "none";
        }

        TransferData data = new TransferData.Builder()
                .origin(origin.getAddress().getHostAddress())
                .port(origin.getPort())
                .server(playerServer)
                .build();

        // Encrypt the data?
        player.storeCookie(TransferAPI.TRANSFER_KEY, data.toBytes());
        player.transferToHost(new InetSocketAddress(host, port));

        return Component.text("Transferred " + player.getUsername() +
                        " to " + host + ":" + port + "/" + playerServer, NamedTextColor.GREEN);
    }

    private int run(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        List<Player> players = new ArrayList<>();
        String playerStr = StringArgumentType.getString(context, PLAYER_ARG);
        if (playerStr.equalsIgnoreCase("all")) {
            players.addAll(server.getAllPlayers());
        } else {
            Player player = server.getPlayer(playerStr).orElse(null);
            if (player == null) {
                source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
                return 0;
            }
            players.add(player);
        }
        String host = StringArgumentType.getString(context, HOST_ARG);
        int port = IntegerArgumentType.getInteger(context, PORT_ARG);

        source.sendMessage(Component.text("Transferring players...", NamedTextColor.GREEN));
        for (Player player : players) {
            source.sendMessage(this.transfer(player, host, port));
        }
        return Command.SINGLE_SUCCESS;
    }
}
