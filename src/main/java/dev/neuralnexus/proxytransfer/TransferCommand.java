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
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import dev.neuralnexus.proxytransfer.api.TransferAPI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public class TransferCommand {
    private final ProxyServer server;
    private final PluginContainer plugin;
    private static final String PLAYER_ARG = "player";
    private static final String HOST_ARG = "host";
    private static final String PORT_ARG = "port";

    public TransferCommand(ProxyServer server, PluginContainer plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    public void register() {
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

        final BrigadierCommand command = new BrigadierCommand(rootNode);
        CommandManager commandManager = this.server.getCommandManager();
        commandManager.register(
                commandManager.metaBuilder(command)
                        .plugin(this.plugin)
                        .build(),
                command
        );
    }

    private boolean checkPermission(CommandSource source) {
        return source.getPermissionValue("proxytransfer.transfer") == Tristate.TRUE;
    }

    private CompletableFuture<Suggestions> getPlayerSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder builder)  {
        final String argument = context.getArguments().containsKey(PLAYER_ARG)
                ? context.getArgument(PLAYER_ARG, String.class)
                : "";
        for (final Player p : ProxyTransfer.server().getAllPlayers()) {
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

    private int run(CommandContext<CommandSource> context) {
        String playerArg = StringArgumentType.getString(context, "player");
        Player target = ProxyTransfer.server().getPlayer(playerArg).orElse(null);
        if (target == null) {
            context.getSource().sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return 0;
        }
        if (target.getCurrentServer().isEmpty()) {
            context.getSource().sendMessage(Component.text("Player is not connected to a server", NamedTextColor.RED));
            return 0;
        }
        String hostArg = StringArgumentType.getString(context, "host");
        int portArg = IntegerArgumentType.getInteger(context, "port");

        InetSocketAddress origin = this.server.getBoundAddress();
        String playerServer = target.getCurrentServer().get().getServerInfo().getName();

        TransferData data = new TransferData.Builder()
                .origin(origin.getAddress().getHostAddress())
                .port(origin.getPort())
                .server(playerServer)
                .build();

        // Encrypt the data?
        target.storeCookie(TransferAPI.TRANSFER_KEY, data.toBytes());
        target.transferToHost(new InetSocketAddress(hostArg, portArg));

        context.getSource().sendMessage(Component.text("Transferring " + playerArg + " to " + hostArg + ":" + portArg, NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
