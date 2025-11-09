package me.lubomirstankov.gotcraftproxychat.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lubomirstankov.gotcraftproxychat.common.model.ChatPacket;
import me.lubomirstankov.gotcraftproxychat.paper.GotCraftPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

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
        Component renderedMessage = event.renderer().render(sender, sender.displayName(), event.message(), event.viewers().iterator().next());
        String json = gsonSerializer.serialize(renderedMessage);

        forwardChatPacket(sender, json);
    }

    private void forwardChatPacket(Player sender, String json) {
        try {
            String serverName = plugin.getConfigManager().getString("chat.server-name", plugin.getServer().getName());
            String serverPrefix = plugin.getConfigManager().getString("chat.server-prefix", "");
            byte[] packetData = serializeChat(sender.getUniqueId().toString(), json);

            ChatPacket chatPacket = new ChatPacket(serverName, sender.getUniqueId(), sender.getName(), serverPrefix, packetData);
            plugin.getMessengerService().sendChatPacket(chatPacket);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to forward chat packet: " + e.getMessage());
        }
    }

    private byte[] serializeChat(String senderUuid, String json) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {

            dos.writeInt(0);
            dos.writeUTF(senderUuid);
            dos.writeUTF(json);

            return bos.toByteArray();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to serialize chat: " + e.getMessage());
            return new byte[0];
        }
    }

    public static void startBroadcasting() {
    }

    public static void endBroadcasting() {
    }
}

