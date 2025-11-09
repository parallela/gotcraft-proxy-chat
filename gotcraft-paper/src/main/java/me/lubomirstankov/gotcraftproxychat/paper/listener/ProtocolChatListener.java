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

/**
 * Listens for outgoing chat packets and forwards ONLY legitimate player messages
 *
 * STRUCTURAL APPROACH (NO TEXT MATCHING):
 * 1. Uses DISGUISED_CHAT + SYSTEM_CHAT packet types to catch player chat
 * 2. CRITICAL FILTER: Only forwards packets from players marked by AsyncPlayerChatEvent
 * 3. Validates sender UUID exists and belongs to online player
 * 4. Checks overlay=false (chat area, not action bar) for SYSTEM_CHAT packets
 * 5. Ignores ALL system messages, join/leave, broadcasts, console, LuckPerms, etc. structurally
 * 6. Zero reliance on string parsing, regex, or text contains checks
 *
 * The AsyncPlayerChatEvent mark is THE definitive filter - if a packet doesn't have it,
 * it's not real player chat and gets ignored.
 */
public class ProtocolChatListener extends PacketAdapter {

    private final GotCraftPaper plugin;

    // ThreadLocal flag to prevent re-intercepting packets we're broadcasting
    private static final ThreadLocal<Boolean> BROADCASTING = ThreadLocal.withInitial(() -> false);

    // Track players who just sent a chat message (marked by AsyncPlayerChatEvent)
    private static final Set<UUID> PLAYER_CHAT_PENDING = ConcurrentHashMap.newKeySet();

    public ProtocolChatListener(GotCraftPaper plugin) {
        // Monitor all possible chat packet types to see which one handles player chat
        super(plugin, ListenerPriority.MONITOR,
            PacketType.Play.Server.SYSTEM_CHAT,
            PacketType.Play.Server.DISGUISED_CHAT);
        this.plugin = plugin;
        plugin.getLogger().info("ProtocolChatListener created - monitoring SYSTEM_CHAT and DISGUISED_CHAT");
    }

    /**
     * Mark a player as having sent a chat message
     * Called by AsyncPlayerChatEvent listener
     */
    public static void markPlayerChatSent(UUID playerUuid) {
        PLAYER_CHAT_PENDING.add(playerUuid);
    }

    /**
     * Remove a player from pending chat set
     */
    private static void clearPlayerChatMark(UUID playerUuid) {
        PLAYER_CHAT_PENDING.remove(playerUuid);
    }


    @Override
    public void onPacketSending(PacketEvent event) {
        // CRITICAL: Don't intercept packets we're broadcasting from other servers
        if (BROADCASTING.get()) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        try {
            PacketContainer packet = event.getPacket();
            PacketType type = packet.getType();

            // Log only chat-related packets to reduce spam
            String typeName = type.toString();
            if (typeName.contains("CHAT") || typeName.contains("MESSAGE")) {
                plugin.getLogger().info("=== PACKET: " + type + " ===");

                // Try to extract chat component
                try {
                    WrappedChatComponent chatComponent = packet.getChatComponents().readSafely(0);
                    if (chatComponent != null && chatComponent.getJson() != null) {
                        String json = chatComponent.getJson();
                        plugin.getLogger().info("  Has chat component: " + json.substring(0, Math.min(150, json.length())));
                    }
                } catch (Exception e) {
                    // Ignore
                }

                // Try to check overlay
                try {
                    Boolean overlay = packet.getBooleans().readSafely(0);
                    plugin.getLogger().info("  Overlay: " + overlay);
                } catch (Exception e) {
                    // Ignore
                }

                // Check if player chat is pending
                plugin.getLogger().info("  Pending player chats: " + PLAYER_CHAT_PENDING.size());
            }

        } catch (Exception e) {
            // Ignore to reduce spam
        }
    }

    /**
     * Handle DISGUISED_CHAT packets (player chat in modern Minecraft)
     */
    private void handleDisguisedChat(PacketContainer packet) {
        try {
            // DISGUISED_CHAT is exclusively for player messages, but still validate

            // VALIDATION #1: Extract chat component
            WrappedChatComponent chatComponent = packet.getChatComponents().readSafely(0);
            if (chatComponent == null || chatComponent.getJson() == null || chatComponent.getJson().trim().isEmpty()) {
                plugin.getLogger().finest("Ignoring DISGUISED_CHAT with empty component");
                return;
            }

            // VALIDATION #2: Check if this is marked as player chat by AsyncPlayerChatEvent
            // In DISGUISED_CHAT, we need to find the player who sent this
            Player sender = findSenderFromPacket(packet);
            if (sender == null) {
                plugin.getLogger().finest("Ignoring DISGUISED_CHAT without identifiable sender");
                return;
            }

            // VALIDATION #3: Verify marked by AsyncPlayerChatEvent
            if (!PLAYER_CHAT_PENDING.contains(sender.getUniqueId())) {
                plugin.getLogger().finest("Ignoring DISGUISED_CHAT not marked by AsyncPlayerChatEvent: " + sender.getName());
                return;
            }

            // Clear the mark
            clearPlayerChatMark(sender.getUniqueId());

            String json = chatComponent.getJson();
            plugin.getLogger().fine("Processing DISGUISED_CHAT from " + sender.getName());

            // Forward the packet
            forwardChatPacket(sender, packet, json);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to handle DISGUISED_CHAT: " + e.getMessage());
        }
    }

    /**
     * Handle SYSTEM_CHAT packets with strict filtering
     */
    private void handleSystemChat(PacketContainer packet) {
        try {
            // VALIDATION #1: Extract chat component
            WrappedChatComponent chatComponent = packet.getChatComponents().readSafely(0);
            if (chatComponent == null || chatComponent.getJson() == null || chatComponent.getJson().trim().isEmpty()) {
                return;
            }

            // VALIDATION #2: Check overlay=false (chat area, not action bar)
            Boolean overlay = packet.getBooleans().readSafely(0);
            if (overlay == null || overlay) {
                return; // This is an action bar message, not chat
            }

            // VALIDATION #3: Find the sender player
            Player sender = findSenderFromPacket(packet);
            if (sender == null) {
                return; // Can't identify sender, likely system message
            }

            // VALIDATION #4: CRITICAL - Must be marked by AsyncPlayerChatEvent
            if (!PLAYER_CHAT_PENDING.contains(sender.getUniqueId())) {
                plugin.getLogger().finest("Ignoring SYSTEM_CHAT not from AsyncPlayerChatEvent");
                return;
            }

            // Clear the mark
            clearPlayerChatMark(sender.getUniqueId());

            String json = chatComponent.getJson();
            plugin.getLogger().fine("Processing SYSTEM_CHAT from " + sender.getName());

            // Forward the packet
            forwardChatPacket(sender, packet, json);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to handle SYSTEM_CHAT: " + e.getMessage());
        }
    }

    /**
     * Try to find the player who sent this packet
     */
    private Player findSenderFromPacket(PacketContainer packet) {
        // Try to get UUID from packet
        UUID senderUuid = packet.getUUIDs().readSafely(0);
        if (senderUuid != null) {
            Player player = plugin.getServer().getPlayer(senderUuid);
            if (player != null && player.isOnline()) {
                return player;
            }
        }

        // For packets where we can't extract UUID, check all pending players
        // This is a fallback - the AsyncPlayerChatEvent mark is still required
        for (UUID pendingUuid : PLAYER_CHAT_PENDING) {
            Player player = plugin.getServer().getPlayer(pendingUuid);
            if (player != null && player.isOnline()) {
                return player; // Return first pending player
            }
        }

        return null;
    }

    /**
     * Forward a validated chat packet to BungeeCord
     */
    private void forwardChatPacket(Player sender, PacketContainer packet, String json) {
        try {
            // Serialize the packet
            byte[] packetData = serializePacket(packet, json);

            if (packetData.length == 0) {
                plugin.getLogger().warning("Failed to serialize packet data");
                return;
            }

            // Get server name and prefix from config
            String serverName = plugin.getConfigManager().getString("chat.server-name",
                    plugin.getServer().getName());
            String serverPrefix = plugin.getConfigManager().getString("chat.server-prefix", "");

            // Create chat packet
            ChatPacket chatPacket = new ChatPacket(
                    serverName,
                    sender.getUniqueId(),
                    sender.getName(),
                    serverPrefix,
                    packetData
            );

            // Send to BungeeCord
            plugin.getMessengerService().sendChatPacket(chatPacket);

            plugin.getLogger().info("✓ Forwarded chat from " + sender.getName() + " on " + serverName);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to forward chat packet: " + e.getMessage());
        }
    }


    /**
     * Serialize a chat PacketContainer to bytes
     * @param packet The packet to serialize
     * @param json The JSON chat component
     * @return The serialized packet data
     */
    private byte[] serializePacket(PacketContainer packet, String json) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
            // Write packet type identifier
            dos.writeInt(packet.getType().hashCode());

            // Extract and write sender UUID if available
            UUID senderUuid = packet.getUUIDs().readSafely(0);
            dos.writeUTF(senderUuid != null ? senderUuid.toString() : "");

            // Write the JSON chat component
            dos.writeUTF(json != null ? json : "");

            plugin.getLogger().finest("Serialized chat packet successfully");

        } catch (Exception e) {
            plugin.getLogger().warning("Error serializing chat packet fields: " + e.getMessage());
            // Write minimal valid data
            dos.writeUTF("");
            dos.writeUTF("");
        }

        return bos.toByteArray();
    }

    /**
     * Register this listener with ProtocolLib
     */
    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
        plugin.getLogger().info("ProtocolLib chat listener registered (DISGUISED_CHAT + SYSTEM_CHAT with AsyncPlayerChatEvent filtering)");
    }

    /**
     * Unregister this listener from ProtocolLib
     */
    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
        plugin.getLogger().info("ProtocolLib chat listener unregistered");
    }

    /**
     * Set the broadcasting flag to prevent re-interception
     * Call this BEFORE broadcasting packets from other servers
     */
    public static void startBroadcasting() {
        BROADCASTING.set(true);
    }

    /**
     * Clear the broadcasting flag after broadcasting is complete
     * Call this AFTER broadcasting packets from other servers
     */
    public static void endBroadcasting() {
        BROADCASTING.set(false);
    }
}

