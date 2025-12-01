package me.lubomirstankov.gotcraftproxychat.paper.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import me.lubomirstankov.gotcraftproxychat.common.model.ChatPacket;
import me.lubomirstankov.gotcraftproxychat.paper.GotCraftPaper;
import me.lubomirstankov.gotcraftproxychat.paper.listener.ProtocolChatListener;
import me.lubomirstankov.gotcraftproxychat.paper.util.PlaceholderSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
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

            // packetData was written as: int(typeHash), UTF(senderUuid), UTF(json)
            dis.readInt();
            String senderUuidStr = dis.readUTF();

            if (senderUuidStr == null || senderUuidStr.trim().isEmpty()) {
                plugin.getLogger().warning("Skipping packet with missing sender UUID");
                return;
            }

            UUID senderUuid;
            try {
                senderUuid = UUID.fromString(senderUuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping packet with invalid sender UUID");
                return;
            }

            String jsonContent = dis.readUTF();
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                plugin.getLogger().warning("Skipping packet with empty JSON content");
                return;
            }

            // Safely deserialize JSON to an Adventure Component using Gson only.
            Component originalMessage = safeDeserializeJson(jsonContent);
            if (originalMessage == null) {
                plugin.getLogger().warning("Skipping packet - failed to deserialize JSON to Component");
                return;
            }

            // Apply placeholder support to server prefix (if present)
            String rawServerPrefix = chatPacket.getServerPrefix();
            Component finalMessage = safeCombinePrefixAndJson(rawServerPrefix, originalMessage, senderUuid);

            // Send to players using ProtocolLib. Wrap to JSON using Gson serializer only.
            safeSendToPlayers(finalMessage);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to broadcast chat packet: " + e.getMessage());
        }
    }

    // Safely deserialize JSON string into an Adventure Component using GsonComponentSerializer only.
    // Returns null on fatal failure (caller will handle skipping).
    private Component safeDeserializeJson(String json) {
        if (json == null) return null;
        try {
            return gsonSerializer.deserialize(json);
        } catch (Exception e) {
            // Malformed JSON fallback: use plain text to avoid feeding JSON into MiniMessage
            try {
                return Component.text(json);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    // Combine server prefix (MiniMessage format) with original JSON Component safely.
    // Important: NEVER feed the original JSON into MiniMessage. MiniMessage is used only for the prefix.
    private Component safeCombinePrefixAndJson(String rawServerPrefix, Component originalMessage, UUID senderUuid) {
        if (rawServerPrefix == null || rawServerPrefix.trim().isEmpty()) {
            return originalMessage;
        }

        // Apply PlaceholderAPI to the prefix if available
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(senderUuid);
        String applied = PlaceholderSupport.apply(rawServerPrefix, offline);

        Component prefixComponent;
        try {
            prefixComponent = miniMessage.deserialize(applied == null ? "" : applied);
        } catch (Exception e) {
            // If MiniMessage fails parsing the prefix, fall back to plain text prefix
            prefixComponent = Component.text(applied == null ? "" : applied);
        }

        // Append a space between prefix and message if not already present
        Component spacing = Component.text(" ");
        return prefixComponent.append(spacing).append(originalMessage);
    }

    // Safely serialize Component using Gson and send SYSTEM_CHAT packets to all online players.
    // Uses ProtocolChatListener broadcasting flag to avoid re-interception.
    private void safeSendToPlayers(Component component) {
        try {
            String modifiedJson = gsonSerializer.serialize(component);

            PacketContainer packet = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
            com.comphenix.protocol.wrappers.WrappedChatComponent wrappedComponent =
                    com.comphenix.protocol.wrappers.WrappedChatComponent.fromJson(modifiedJson);
            packet.getChatComponents().write(0, wrappedComponent);
            packet.getBooleans().write(0, false);

            // Prevent ProtocolChatListener from re-intercepting these injected packets
            ProtocolChatListener.startBroadcasting();
            try {
                for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(onlinePlayer, packet);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to send packet to " + onlinePlayer.getName() + ": " + e.getMessage());
                    }
                }
            } finally {
                ProtocolChatListener.endBroadcasting();
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send chat Component to players: " + e.getMessage());
        }
    }
}
