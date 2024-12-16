package dev.neuralnexus.proxytransfer;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import dev.neuralnexus.proxytransfer.api.TransferAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.UUID;

import static dev.neuralnexus.proxytransfer.api.TransferAPI.TRANSFER_KEY;

@Plugin(id = "proxytransfer", name = "ProxyTransfer", version = BuildConstants.VERSION)
public class ProxyTransfer implements SimpleCommand {

    private static Logger logger;

    public static Logger logger() {
        return logger;
    }

    private static ProxyServer server;

    public static ProxyServer server() {
        return server;
    }

    private static PluginContainer plugin;

    public static PluginContainer plugin() {
        return plugin;
    }

    @Inject
    public ProxyTransfer(ProxyServer server, PluginContainer plugin, Logger logger) {
        ProxyTransfer.server = server;
        ProxyTransfer.plugin = plugin;
        ProxyTransfer.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        new TransferCommand(server, plugin).register();
    }

    // General Flow:
    // 1. Player logs in, server requests cookie
    // 2. Server receives cookie, stores the player's previous server
    // 3. Server sends the player to the previous server

    @Subscribe
    public void onLogin(LoginEvent event) {
        event.getPlayer().requestCookie(TRANSFER_KEY);
    }

    @Subscribe
    public void onCookieReceived(CookieReceiveEvent event) {
        if (!event.getOriginalKey().equals(TRANSFER_KEY)) return;
        if (event.getOriginalData() == null || event.getOriginalData().length == 0) {
            return;
        }

        TransferData data = TransferData.fromBytes(event.getOriginalData());
        // Check if origin is valid?
        // Decrypt the data?
        server.getServer(data.server()).ifPresent(server ->
                TransferAPI.get().update(event.getPlayer().getUniqueId(), server));
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (TransferAPI.get().contains(uuid)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.createConnectionRequest(TransferAPI.get().remove(uuid)).fireAndForget();
        }
        player.storeCookie(TRANSFER_KEY, new byte[0]);
    }

    @Override
    public void execute(final Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length != 3) {
            source.sendMessage(Component.text("Usage: /transfer <player> <host> <port>", NamedTextColor.RED));
            return;
        }
        String targetName = args[0];
        Player target = server.getPlayer(targetName).orElse(null);
        if (target == null) {
            source.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        if (target.getCurrentServer().isEmpty()) {
            source.sendMessage(Component.text("Player is not connected to a server.", NamedTextColor.RED));
            return;
        }

        String host = args[1];
        int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("Invalid port.", NamedTextColor.RED));
            return;
        }

        InetSocketAddress origin = server.getBoundAddress();
        String playerServer = target.getCurrentServer().get().getServerInfo().getName();

        TransferData data = new TransferData.Builder()
                .origin(origin.getAddress().getHostAddress())
                .port(origin.getPort())
                .server(playerServer)
                .build();

        // Encrypt the data?
        target.storeCookie(TRANSFER_KEY, data.toBytes());

        InetSocketAddress address = new InetSocketAddress(host, port);
        target.transferToHost(address);
    }
}
