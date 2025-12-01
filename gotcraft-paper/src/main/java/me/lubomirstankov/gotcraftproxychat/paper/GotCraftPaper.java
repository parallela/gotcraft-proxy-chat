package me.lubomirstankov.gotcraftproxychat.paper;

import me.lubomirstankov.gotcraftproxychat.common.config.ConfigManager;
import me.lubomirstankov.gotcraftproxychat.common.util.DIContainer;
import me.lubomirstankov.gotcraftproxychat.paper.command.ReloadConfigCommand;
import me.lubomirstankov.gotcraftproxychat.paper.listener.PlayerChatEventListener;
import me.lubomirstankov.gotcraftproxychat.paper.service.PaperMessengerService;
import me.lubomirstankov.gotcraftproxychat.paper.util.PlaceholderSupport;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class GotCraftPaper extends JavaPlugin {

    private ConfigManager configManager;
    private PaperMessengerService messengerService;
    private PlayerChatEventListener chatListener;

    @Override
    public void onEnable() {
        Path configPath = getDataFolder().toPath().resolve("config.yml");
        configManager = new ConfigManager(configPath);
        configManager.load(getResource("config.yml"));

        DIContainer.register(ConfigManager.class, configManager);

        messengerService = new PaperMessengerService(this);
        messengerService.initialize();
        DIContainer.register(PaperMessengerService.class, messengerService);

        chatListener = new PlayerChatEventListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        getCommand("gcreload").setExecutor(new ReloadConfigCommand(this));

        getLogger().info("GotCraftProxyChat-Paper has been enabled!");
        getLogger().info("Server: " + configManager.getString("chat.server-name", getServer().getName()));

        if (PlaceholderSupport.isAvailable()) {
            getLogger().info("PlaceholderAPI found - placeholders in server-prefix will be resolved");
        }
    }

    @Override
    public void onDisable() {
        DIContainer.clear();
        getLogger().info("GotCraftProxyChat-Paper has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PaperMessengerService getMessengerService() {
        return messengerService;
    }
}

