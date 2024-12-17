package dev.neuralnexus.proxytransfer;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;

import org.slf4j.Logger;

@Plugin(id = "proxytransfer", name = "ProxyTransfer", version = BuildConstants.VERSION)
public class ProxyTransfer {

    private static Logger logger;
    private final ProxyServer server;
    private final PluginContainer plugin;

    public static Logger logger() {
        return logger;
    }

    @Inject
    public ProxyTransfer(ProxyServer server, PluginContainer plugin, Logger logger) {
        this.server = server;
        this.plugin = plugin;
        ProxyTransfer.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("ProxyTransfer has been initialized!");

        EventManager eventManager = this.server.getEventManager();
        eventManager.register(plugin, new TransferListener(this.server));

        CommandManager commandManager = this.server.getCommandManager();
        BrigadierCommand command = new TransferCommand(this.server).register();
        commandManager.register(
                commandManager.metaBuilder(command)
                        .plugin(this.plugin)
                        .build(),
                command
        );
    }
}
