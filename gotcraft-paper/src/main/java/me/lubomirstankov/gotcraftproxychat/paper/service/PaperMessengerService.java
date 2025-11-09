package me.lubomirstankov.gotcraftproxychat.paper.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import me.lubomirstankov.gotcraftproxychat.common.model.ChatPacket;
import me.lubomirstankov.gotcraftproxychat.paper.GotCraftPaper;
import me.lubomirstankov.gotcraftproxychat.paper.listener.PlayerChatEventListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.UUID;

public class PaperMessengerService implements PluginMessageListener {

    private static final String CHANNEL = "gotcraft:chat";
    private final GotCraftPaper plugin;
    private final MiniMessage miniMessage;
    private final GsonComponentSerializer gsonSerializer;

    public PaperMessengerService(GotCraftPaper plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.gsonSerializer = GsonComponentSerializer.gson();
    }

    public void initialize() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void sendChatPacket(ChatPacket packet) {
        Player player = plugin.getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            return;
        }

        byte[] data = packet.serialize();
        player.sendPluginMessage(plugin, CHANNEL, data);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        try {
            ChatPacket chatPacket = ChatPacket.deserialize(message);
            String ourServerName = plugin.getConfigManager().getString("chat.server-name", plugin.getServer().getName());

            if (chatPacket.getServerName().equalsIgnoreCase(ourServerName)) {
                return;
            }

            broadcastPacket(chatPacket);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to process incoming chat packet: " + e.getMessage());
        }
    }

    private void broadcastPacket(ChatPacket chatPacket) {
        try {
            byte[] packetData = chatPacket.getPacketData();
            ByteArrayInputStream bis = new ByteArrayInputStream(packetData);
            DataInputStream dis = new DataInputStream(bis);

            dis.readInt();
            String senderUuidStr = dis.readUTF();

            if (senderUuidStr.trim().isEmpty()) {
                return;
            }

            UUID senderUuid;
            try {
                senderUuid = UUID.fromString(senderUuidStr);
            } catch (IllegalArgumentException e) {
                return;
            }

            String jsonContent = dis.readUTF();
            if (jsonContent.trim().isEmpty()) {
                return;
            }

            Component originalMessage;
            try {
                originalMessage = gsonSerializer.deserialize(jsonContent);
            } catch (Exception e) {
                return;
            }

            Component finalMessage;
            String serverPrefix = chatPacket.getServerPrefix();

            if (serverPrefix != null && !serverPrefix.isEmpty()) {
                Component prefixComponent = miniMessage.deserialize(serverPrefix);
                finalMessage = prefixComponent.append(originalMessage);
            } else {
                finalMessage = originalMessage;
            }

            String modifiedJson = gsonSerializer.serialize(finalMessage);

            PacketContainer packet = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
            com.comphenix.protocol.wrappers.WrappedChatComponent wrappedComponent =
                    com.comphenix.protocol.wrappers.WrappedChatComponent.fromJson(modifiedJson);
            packet.getChatComponents().write(0, wrappedComponent);
            packet.getBooleans().write(0, false);

            PlayerChatEventListener.startBroadcasting();

            try {
                for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(onlinePlayer, packet);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to send packet to " + onlinePlayer.getName());
                    }
                }
            } finally {
                PlayerChatEventListener.endBroadcasting();
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to broadcast chat packet: " + e.getMessage());
        }
    }
}

