package dev.neuralnexus.proxytransfer.api;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.key.Key;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransferAPI {
    private static final TransferAPI INSTANCE = new TransferAPI();

    public static TransferAPI get() {
        return INSTANCE;
    }

    public static final Key TRANSFER_KEY = Key.key("proxytransfer", "transfer");

    private final ConcurrentHashMap<UUID, RegisteredServer> reconnectMap = new ConcurrentHashMap<>();

    public void store(UUID player, RegisteredServer server) {
        reconnectMap.put(player, server);
    }

    public RegisteredServer get(UUID player) {
        return reconnectMap.get(player);
    }

    public RegisteredServer remove(UUID player) {
        return reconnectMap.remove(player);
    }

    public boolean contains(UUID player) {
        return reconnectMap.containsKey(player);
    }
}
