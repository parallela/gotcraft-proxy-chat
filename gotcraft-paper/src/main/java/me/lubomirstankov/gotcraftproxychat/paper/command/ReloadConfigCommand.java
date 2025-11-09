package me.lubomirstankov.gotcraftproxychat.paper.command;

import me.lubomirstankov.gotcraftproxychat.paper.GotCraftPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Command to reload the plugin configuration
 */
public class ReloadConfigCommand implements CommandExecutor {

    private final GotCraftPaper plugin;

    public ReloadConfigCommand(GotCraftPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gotcraftproxychat.reload")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        try {
            plugin.getConfigManager().reload();
            sender.sendMessage(Component.text("Configuration reloaded successfully!")
                    .color(NamedTextColor.GREEN));
            plugin.getLogger().info(sender.getName() + " reloaded the configuration");
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage())
                    .color(NamedTextColor.RED));
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}

