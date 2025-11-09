package me.lubomirstankov.gotcraftproxychat.bungeecord;

import me.lubomirstankov.gotcraftproxychat.bungeecord.service.BungeeMessengerService;
import me.lubomirstankov.gotcraftproxychat.common.util.DIContainer;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Main plugin class for BungeeCord proxy
 */
public final class GotCraftBungee extends Plugin {

    private BungeeMessengerService messengerService;

    @Override
    public void onEnable() {
        // Initialize messenger service
        messengerService = new BungeeMessengerService(this);
        messengerService.initialize();
        DIContainer.register(BungeeMessengerService.class, messengerService);

        getLogger().info("GotCraftProxyChat-BungeeCord has been enabled!");
        getLogger().info("Listening on channel: gotcraft:chat");
    }

    @Override
    public void onDisable() {
        // Clear DI container
        DIContainer.clear();

        getLogger().info("GotCraftProxyChat-BungeeCord has been disabled!");
    }

    /**
     * Get the messenger service
     * @return The messenger service
     */
    public BungeeMessengerService getMessengerService() {
        return messengerService;
    }
}

