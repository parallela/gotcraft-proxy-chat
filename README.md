# GotCraftProxyChat

A production-ready cross-server chat plugin for BungeeCord networks that synchronizes chat messages between Paper servers with LuckPerms integration and customizable server tags.

## Features

- ✅ **Cross-Server Chat**: Messages sent on one server appear on all connected servers
- 🏷️ **Server Tags**: Customizable prefixes for each server (e.g., [SURVIVAL], [SKYBLOCK])
- 👑 **LuckPerms Integration**: Automatic rank prefix fetching and display
- 🔒 **Origin Server Exclusion**: Messages are not echoed back to the origin server
- ⚙️ **Fully Configurable**: All aspects configurable via YAML
- 🔄 **Hot Reload**: Reload configuration without restarting servers
- 🎨 **Color Support**: Full Minecraft color code support with `&` formatting
- 📡 **Plugin Messaging**: Efficient communication using BungeeCord plugin channels

## Architecture

This is a multi-module Maven project with three modules:

```
GotCraftProxyChat/
├── gotcraft-common/          # Shared code (models, utilities, config)
├── gotcraft-paper/           # Paper server plugin
└── gotcraft-bungeecord/      # BungeeCord proxy plugin
```

## Requirements

- **Java 17+**
- **BungeeCord** (or compatible proxy like Waterfall)
- **Paper 1.21.1+** (or compatible fork)
- **LuckPerms** (optional, for rank prefixes)

## Installation

1. **Build the project**:
   ```bash
   mvn clean package
   ```

2. **Install on BungeeCord**:
   - Copy `gotcraft-bungeecord/target/GotCraftProxyChat-Bungee.jar` to your BungeeCord `plugins/` folder

3. **Install on Paper servers**:
   - Copy `gotcraft-paper/target/GotCraftProxyChat-Paper.jar` to each Paper server's `plugins/` folder
   - If using LuckPerms, ensure it's installed on each Paper server

4. **Configure each Paper server**:
   - Edit `plugins/GotCraftProxyChat/config.yml` on each server
   - Set the `chat.server-name` to match your BungeeCord server name (e.g., "survival", "skyblock")

5. **Restart** BungeeCord and all Paper servers

## Configuration

### Paper Server Configuration (`config.yml`)

```yaml
chat:
  # Enable/disable cross-server chat
  enabled: true
  
  # Chat message format
  # Placeholders:
  #   {serverPrefix} - The server prefix (from server-prefixes below)
  #   {rankPrefix} - The player's LuckPerms rank prefix
  #   {player} - The player's name
  #   {message} - The chat message
  format: "{serverPrefix} {rankPrefix}{player}&f: {message}"
  
  # Server name (must match BungeeCord config)
  server-name: "survival"
  
  # Use player display name instead of username
  # Display names support nicknames, colors, and formatting
  use-display-name: true
  
  # Server prefixes
  server-prefixes:
    survival: "&a[SURVIVAL]"
    skyblock: "&b[SKYBLOCK]"
    lobby: "&e[LOBBY]"
    creative: "&d[CREATIVE]"
    minigames: "&c[MINIGAMES]"
  
  # LuckPerms integration
  luckperms:
    use-prefix: true
```

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/gcreload` | `gotcraftproxychat.reload` | Reload the configuration |

**Aliases**: `/gcrld`, `/gotcraftreload`

## Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `gotcraftproxychat.reload` | Allows reloading the plugin configuration | op |

## How It Works

1. **Player sends message** on Paper server (e.g., Survival)
2. **ChatListener** intercepts the message using `AsyncPlayerChatEvent`
3. **LuckPerms API** fetches the player's rank prefix (if enabled)
4. **ChatMessage** object is created with server name, player info, prefix, and message
5. **PaperMessengerService** serializes and sends via plugin channel `gotcraft:chat`
6. **BungeeCord** receives the message via **BungeeMessengerService**
7. **Message is forwarded** to all connected servers **except the origin**
8. **Paper servers receive** the message and **ChatService** formats and broadcasts it

## Development

### Building

```bash
mvn clean package
```

### Module Structure

**gotcraft-common**:
- `ConfigManager`: YAML configuration management
- `ChatMessage`: Message model with serialization
- `DIContainer`: Simple dependency injection

**gotcraft-paper**:
- `GotCraftPaper`: Main plugin class
- `ChatListener`: Intercepts chat events
- `ChatService`: Formats and broadcasts messages
- `PaperMessengerService`: Handles plugin messaging
- `ReloadConfigCommand`: Configuration reload command

**gotcraft-bungeecord**:
- `GotCraftBungee`: Main proxy plugin class
- `BungeeMessengerService`: Receives and forwards messages

## Example Chat Output

With the default configuration, a message from a player named "Steve" with the rank "[Admin]" on the Survival server would appear as:

```
[SURVIVAL] [Admin]Steve: Hello everyone!
```

On all other servers (Skyblock, Lobby, etc.), but not on Survival itself.

## Troubleshooting

### Messages not appearing on other servers

1. Check that the plugin is installed on **both BungeeCord and all Paper servers**
2. Verify that `chat.server-name` in each Paper server's config matches the BungeeCord server name
3. Check console logs for errors
4. Ensure at least one player is online on each server (required for plugin messaging)

### LuckPerms prefix not showing

1. Ensure LuckPerms is installed on the Paper server
2. Check that `chat.luckperms.use-prefix` is set to `true`
3. Verify the player has a prefix set in LuckPerms

### Configuration changes not applying

Run `/gcreload` or restart the server after editing `config.yml`

## License

This project is provided as-is for use in Minecraft server networks.

## Author

**lubomirstankov**

## Support

For issues, questions, or feature requests, please contact the author or check the server documentation.

