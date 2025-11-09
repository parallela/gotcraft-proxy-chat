package me.lubomirstankov.gotcraftproxychat.common.model;

import java.io.*;
import java.util.UUID;

/**
 * Represents a serialized chat packet that is transmitted between servers
 * This preserves the original packet data without modification
 */
public class ChatPacket implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final byte VERSION = 2; // Version 2 includes serverPrefix

    private final String serverName;
    private final UUID playerUuid;
    private final String playerName;
    private final String serverPrefix;
    private final byte[] packetData;

    public ChatPacket(String serverName, UUID playerUuid, String playerName, String serverPrefix, byte[] packetData) {
        this.serverName = serverName;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.serverPrefix = serverPrefix != null ? serverPrefix : "";
        this.packetData = packetData;
    }

    public String getServerName() {
        return serverName;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getServerPrefix() {
        return serverPrefix;
    }

    public byte[] getPacketData() {
        return packetData;
    }

    /**
     * Serialize the chat packet to a byte array
     * @return The serialized byte array
     */
    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {

            dos.writeByte(VERSION); // Write version first
            dos.writeUTF(serverName);
            dos.writeUTF(playerUuid.toString());
            dos.writeUTF(playerName);
            dos.writeUTF(serverPrefix); // v2: server prefix
            dos.writeInt(packetData.length);
            dos.write(packetData);

            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize ChatPacket", e);
        }
    }

    /**
     * Deserialize a chat packet from a byte array
     * Supports both v1 (without serverPrefix) and v2 (with serverPrefix) formats
     * @param data The byte array
     * @return The deserialized ChatPacket
     */
    public static ChatPacket deserialize(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bis)) {

            // Mark the stream to allow reset if needed
            bis.mark(1);

            // Read first byte
            byte firstByte = dis.readByte();

            String serverName;
            UUID playerUuid;
            String playerName;
            String serverPrefix;
            int packetLength;
            byte[] packetData;

            // Check if this is version 2 (first byte == VERSION)
            if (firstByte == VERSION) {
                // Version 2: includes serverPrefix
                serverName = dis.readUTF();
                playerUuid = UUID.fromString(dis.readUTF());
                playerName = dis.readUTF();
                serverPrefix = dis.readUTF();
                packetLength = dis.readInt();
                packetData = new byte[packetLength];
                dis.readFully(packetData);
            } else {
                // Version 1: no version byte, no serverPrefix
                // Reset to beginning and read as v1
                bis.reset();

                serverName = dis.readUTF();
                playerUuid = UUID.fromString(dis.readUTF());
                playerName = dis.readUTF();
                serverPrefix = ""; // No prefix in v1
                packetLength = dis.readInt();
                packetData = new byte[packetLength];
                dis.readFully(packetData);
            }

            return new ChatPacket(serverName, playerUuid, playerName, serverPrefix, packetData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize ChatPacket: " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "ChatPacket{" +
                "serverName='" + serverName + '\'' +
                ", playerUuid=" + playerUuid +
                ", playerName='" + playerName + '\'' +
                ", serverPrefix='" + serverPrefix + '\'' +
                ", packetDataSize=" + packetData.length +
                '}';
    }
}

