package me.lubomirstankov.gotcraftproxychat.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lubomirstankov.gotcraftproxychat.paper.GotCraftPaper;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PlayerChatEventListener implements Listener {

    private final GotCraftPaper plugin;
    private final GsonComponentSerializer gsonSerializer = GsonComponentSerializer.gson();

    public PlayerChatEventListener(GotCraftPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().getBoolean("chat.enabled", true)) {
            return;
        }

        Player sender = event.getPlayer();

        // Mark player as pending so ProtocolChatListener will capture the outgoing packet
        ProtocolChatListener.markPlayerChatSent(sender.getUniqueId());

        // Schedule a safety clear after 3 seconds in case no packet is intercepted
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                ProtocolChatListener.clearPlayerChatMark(sender.getUniqueId());
            } catch (Exception ignored) {
            }
        }, 60L); // 60 ticks = 3 seconds
    }

    public static void startBroadcasting() {
    }

    public static void endBroadcasting() {
    }
}
