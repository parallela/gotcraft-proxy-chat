package me.lubomirstankov.gotcraftproxychat.paper.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import me.lubomirstankov.gotcraftproxychat.common.model.ChatPacket;
import me.lubomirstankov.gotcraftproxychat.paper.GotCraftPaper;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolChatListener extends PacketAdapter {

    private final GotCraftPaper plugin;

    // ThreadLocal flag to prevent re-intercepting packets we're broadcasting
    private static final ThreadLocal<Boolean> BROADCASTING = ThreadLocal.withInitial(() -> false);

    // Track players who just sent a chat message (marked by AsyncPlayerChatEvent)
    private static final Set<UUID> PLAYER_CHAT_PENDING = ConcurrentHashMap.newKeySet();

    public ProtocolChatListener(GotCraftPaper plugin) {
        super(plugin, ListenerPriority.MONITOR,
                PacketType.Play.Server.SYSTEM_CHAT,
                PacketType.Play.Server.DISGUISED_CHAT);
        this.plugin = plugin;
        plugin.getLogger().info("ProtocolChatListener created - monitoring SYSTEM_CHAT and DISGUISED_CHAT");
    }

    /**
     * Mark a player as having sent a chat message
     */
    public static void markPlayerChatSent(UUID playerUuid) {
        PLAYER_CHAT_PENDING.add(playerUuid);
    }

    /**
     * Remove a player from pending chat set
     */
    public static void clearPlayerChatMark(UUID playerUuid) {
        PLAYER_CHAT_PENDING.remove(playerUuid);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        // Do not process packets that were injected by us
        if (BROADCASTING.get()) return;
        if (event.isCancelled()) return;

        PacketContainer packet = event.getPacket();
        PacketType type = packet.getType();

        try {
            if (type == PacketType.Play.Server.DISGUISED_CHAT) {
                handleDisguisedChat(packet);
            } else if (type == PacketType.Play.Server.SYSTEM_CHAT) {
                handleSystemChat(packet);
            }
        } catch (Exception e) {
            // Avoid throwing and potentially breaking other plugins
            plugin.getLogger().warning("ProtocolChatListener error: " + e.getMessage());
        }
    }

    private void handleDisguisedChat(PacketContainer packet) {
        WrappedChatComponent chatComponent = packet.getChatComponents().readSafely(0);
        if (chatComponent == null) return;
        String json = chatComponent.getJson();
        if (json == null || json.trim().isEmpty()) return;

        // Try to get sender UUID from packet
        UUID senderUuid = packet.getUUIDs().readSafely(0);
        Player sender = null;
        if (senderUuid != null) {
            sender = plugin.getServer().getPlayer(senderUuid);
        }

        // If sender not found, attempt to match any pending player
        if (sender == null) {
            for (UUID pending : PLAYER_CHAT_PENDING) {
                Player p = plugin.getServer().getPlayer(pending);
                if (p != null && p.isOnline()) {
                    sender = p;
                    break;
                }
            }
        }

        if (sender == null) return;
        if (!PLAYER_CHAT_PENDING.contains(sender.getUniqueId())) return;

        // Clear mark and forward
        clearPlayerChatMark(sender.getUniqueId());
        forwardChatPacket(sender, packet, json);
    }

    private void handleSystemChat(PacketContainer packet) {
        WrappedChatComponent chatComponent = packet.getChatComponents().readSafely(0);
        if (chatComponent == null) return;
        String json = chatComponent.getJson();
        if (json == null || json.trim().isEmpty()) return;

        // Overlay boolean (false for chat area)
        Boolean overlay = packet.getBooleans().readSafely(0);
        if (overlay == null || overlay) return;

        UUID senderUuid = packet.getUUIDs().readSafely(0);
        Player sender = null;
        if (senderUuid != null) {
            sender = plugin.getServer().getPlayer(senderUuid);
        }

        if (sender == null) {
            for (UUID pending : PLAYER_CHAT_PENDING) {
                Player p = plugin.getServer().getPlayer(pending);
                if (p != null && p.isOnline()) {
                    sender = p;
                    break;
                }
            }
        }

        if (sender == null) return;
        if (!PLAYER_CHAT_PENDING.contains(sender.getUniqueId())) return;

        clearPlayerChatMark(sender.getUniqueId());
        forwardChatPacket(sender, packet, json);
    }

    private void forwardChatPacket(Player sender, PacketContainer packet, String json) {
        try {
            byte[] packetData = serializePacket(packet, json);
            if (packetData == null || packetData.length == 0) return;

            String serverName = plugin.getConfigManager().getString("chat.server-name", plugin.getServer().getName());
            String serverPrefix = plugin.getConfigManager().getString("chat.server-prefix", "");

            ChatPacket chatPacket = new ChatPacket(
                    serverName,
                    sender.getUniqueId(),
                    sender.getName(),
                    serverPrefix,
                    packetData
            );

            plugin.getMessengerService().sendChatPacket(chatPacket);
            plugin.getLogger().info("✓ Forwarded chat from " + sender.getName() + " on " + serverName);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to forward chat packet: " + e.getMessage());
        }
    }

    private byte[] serializePacket(PacketContainer packet, String json) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {

            dos.writeInt(packet.getType().hashCode());

            UUID senderUuid = packet.getUUIDs().readSafely(0);
            dos.writeUTF(senderUuid != null ? senderUuid.toString() : "");

            dos.writeUTF(json != null ? json : "");

            return bos.toByteArray();
        }
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }

    public static void startBroadcasting() {
        BROADCASTING.set(true);
    }

    public static void endBroadcasting() {
        BROADCASTING.set(false);
    }
}
