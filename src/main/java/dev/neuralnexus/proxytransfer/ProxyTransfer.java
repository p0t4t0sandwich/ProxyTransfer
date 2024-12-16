package dev.neuralnexus.proxytransfer;

import com.google.inject.Inject;
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
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "proxytransfer", name = "ProxyTransfer", version = BuildConstants.VERSION)
public class ProxyTransfer implements SimpleCommand {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer server;

    @Inject
    private PluginContainer plugin;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Starting ProxyTransfer");
        CommandManager commandManager = server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("transfer")
                .plugin(this)
                .build();
        commandManager.register(commandMeta, this);
    }

    private final Key transferKey = Key.key("proxytransfer", "transfer");

    ConcurrentHashMap<UUID, RegisteredServer> reconnecting = new ConcurrentHashMap<>();
    ConcurrentHashMap<UUID, Boolean> transferring = new ConcurrentHashMap<>();

    // General Flow:
    // 1. Player logs in, server requests cookie
    // 2. Server receives cookie, stores the player's previous server
    // 3. Server sends the player to the previous server

    @Subscribe
    public void onLogin(LoginEvent event) {
        event.getPlayer().requestCookie(transferKey);
        transferring.put(event.getPlayer().getUniqueId(), true);
    }

    @Subscribe
    public void onCookieReceived(CookieReceiveEvent event) {
        if (!event.getOriginalKey().equals(transferKey)) return;
        if (event.getOriginalData() == null || event.getOriginalData().length == 0) {
            transferring.remove(event.getPlayer().getUniqueId());
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(event.getOriginalData());
        InetAddress origin;
        try {
            byte[] address = new byte[4];
            buffer.get(address);
            origin = InetAddress.getByAddress(address);
        } catch (Exception e) {
            logger.error("Failed to read origin address", e);
            return;
        }
        int port = buffer.getInt();
        // Check if origin is valid?
        // Decrypt the data?
        int length = buffer.getInt();
        String playerServer = new String(buffer.array(), buffer.position(), length, StandardCharsets.UTF_8);

        this.server.getServer(playerServer).ifPresent(server ->
                reconnecting.put(event.getPlayer().getUniqueId(), server));
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (reconnecting.containsKey(uuid)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.createConnectionRequest(reconnecting.remove(uuid)).fireAndForget();
            player.storeCookie(transferKey, new byte[0]);
        }
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
        String playerServer = target.getCurrentServer().get().getServerInfo().getName();
        int length = playerServer.length();

        String host = args[1];
        int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("Invalid port.", NamedTextColor.RED));
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + length);
        InetSocketAddress origin = server.getBoundAddress();
        buffer.put(origin.getAddress().getAddress());
        buffer.putInt(origin.getPort());

        buffer.putInt(length);
        buffer.put(playerServer.getBytes(StandardCharsets.UTF_8));

        // Encrypt the data?
        target.storeCookie(transferKey, buffer.array());

        InetSocketAddress address = new InetSocketAddress(host, port);
        target.transferToHost(address);
    }
}
