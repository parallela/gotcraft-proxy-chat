package me.lubomirstankov.gotcraftproxychat.bungeecord.service;

import me.lubomirstankov.gotcraftproxychat.bungeecord.GotCraftBungee;
import me.lubomirstankov.gotcraftproxychat.common.model.ChatPacket;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class BungeeMessengerService implements Listener {

    private static final String CHANNEL = "gotcraft:chat";
    private final GotCraftBungee plugin;

    public BungeeMessengerService(GotCraftBungee plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        plugin.getProxy().registerChannel(CHANNEL);
        plugin.getProxy().getPluginManager().registerListener(plugin, this);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(CHANNEL)) {
            return;
        }

        if (!(event.getSender() instanceof Server)) {
            return;
        }

        Server senderServer = (Server) event.getSender();
        String originServerName = senderServer.getInfo().getName();

        try {
            ChatPacket chatPacket = ChatPacket.deserialize(event.getData());
            forwardPacketToServers(chatPacket, originServerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to process plugin message: " + e.getMessage());
        }
    }

    private void forwardPacketToServers(ChatPacket packet, String originServerName) {
        byte[] data = packet.serialize();

        for (ServerInfo server : plugin.getProxy().getServers().values()) {
            if (server.getName().equalsIgnoreCase(originServerName)) {
                continue;
            }

            server.sendData(CHANNEL, data);
        }
    }
}

