package dev.neuralnexus.proxytransfer;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.neuralnexus.proxytransfer.api.TransferAPI;

import java.util.UUID;

import static dev.neuralnexus.proxytransfer.api.TransferAPI.TRANSFER_KEY;

public class TransferListener {
    // General Flow:
    // 1. Player logs in, server requests cookie
    // 2. Server receives cookie, stores the player's previous server
    // 3. Server sends the player to the previous server

    private final ProxyServer server;
    private final TransferAPI api;

    public TransferListener(ProxyServer server) {
        this.server = server;
        this.api = TransferAPI.get();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        ProxyTransfer.logger().debug("Requesting cookie for {}", event.getPlayer().getUsername());
        event.getPlayer().requestCookie(TRANSFER_KEY);
    }

    @Subscribe
    public void onCookieReceived(CookieReceiveEvent event) {
        if (!event.getOriginalKey().equals(TRANSFER_KEY)) return;
        ProxyTransfer.logger().debug("Received cookie for {}", event.getPlayer().getUsername());
        if (event.getOriginalData() == null || event.getOriginalData().length == 0) {
            ProxyTransfer.logger().debug("No cookie data for {}", event.getPlayer().getUsername());
            return;
        }

        TransferData data = TransferData.fromBytes(event.getOriginalData());
        // Check if origin is valid?
        // Decrypt the data?
        this.server.getServer(data.server()).ifPresent(server ->
                api.store(event.getPlayer().getUniqueId(), server));
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        ProxyTransfer.logger().debug("ServerPreConnectEvent for {}", event.getPlayer().getUsername());
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (api.contains(uuid)) {
            if (api.get(uuid) != null) {
                ProxyTransfer.logger().debug("Sending {} to {}", player.getUsername(), api.get(uuid).getServerInfo().getName());
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.createConnectionRequest(api.remove(uuid)).fireAndForget();
            }
            player.storeCookie(TRANSFER_KEY, new byte[0]);
        }
    }
}
