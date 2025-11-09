package me.lubomirstankov.gotcraftproxychat.common.model;

import java.io.*;
import java.util.UUID;

/**
 * Represents a chat message that is transmitted between servers
 */
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String serverName;
    private final UUID playerUuid;
    private final String playerName;
    private final String rankPrefix;
    private final String message;

    public ChatMessage(String serverName, UUID playerUuid, String playerName, String rankPrefix, String message) {
        this.serverName = serverName;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rankPrefix = rankPrefix != null ? rankPrefix : "";
        this.message = message;
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

    public String getRankPrefix() {
        return rankPrefix;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Serialize the chat message to a byte array
     * @return The serialized byte array
     */
    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {

            dos.writeUTF(serverName);
            dos.writeUTF(playerUuid.toString());
            dos.writeUTF(playerName);
            dos.writeUTF(rankPrefix);
            dos.writeUTF(message);

            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize ChatMessage", e);
        }
    }

    /**
     * Deserialize a chat message from a byte array
     * @param data The byte array
     * @return The deserialized ChatMessage
     */
    public static ChatMessage deserialize(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bis)) {

            String serverName = dis.readUTF();
            UUID playerUuid = UUID.fromString(dis.readUTF());
            String playerName = dis.readUTF();
            String rankPrefix = dis.readUTF();
            String message = dis.readUTF();

            return new ChatMessage(serverName, playerUuid, playerName, rankPrefix, message);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize ChatMessage", e);
        }
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "serverName='" + serverName + '\'' +
                ", playerUuid=" + playerUuid +
                ", playerName='" + playerName + '\'' +
                ", rankPrefix='" + rankPrefix + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}

