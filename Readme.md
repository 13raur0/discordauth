# DiscordAuth
DiscordAuth is a Velocity plugin that links Minecraft accounts with Discord accounts for authentication.

## Installation
1. Place the `discordauth-<version>.jar` file in your Velocity `plugins` folder.

## Configuration
When you run the plugin for the first time, it will generate:

`plugins/discordauth/config.properties`

Edit the file and fill in the following:
```properties
discord.token=YOUR_DISCORD_BOT_TOKEN_HERE
discord.guildId=123456789012345678
discord.roleId=987654321098765432
discord.adminId=000000000000000000
security.maxFailures=3
security.blockMinutes=5
integration.lom.allowedUsersPath=plugins/limited-offline-mode/allowed-users.txt
```
- Create a Discord bot and obtain its token (search online for instructions).  
- IDs can be obtained from Discord with Developer Mode enabled.

### Required Discord Bot Permissions
**Bot**
- Server Members Intent
- Message Content Intent

**OAuth2**
- bot
  - Send Messages
  - Manage Roles

## Usage
1. When a player connects to Velocity and is not yet verified, a 6-digit verification code will be generated in-game.
2. The player sends the following command via DM to the Discord bot:

`!verify <verification code>`

3. If the user has the specified server and role in `config.properties`, their Minecraft account will be linked with their Discord account.  
4. Subsequent connections do not require verification.

### Admin Commands
`!delete <DiscordID>`

- Can only be executed by the user specified in `adminId`.
- If successful, the following occurs automatically:
  - The player is disconnected from Velocity.
  - The role is removed from the user in the Discord server.

## Integrations

### [Limited Offline Mode](https://modrinth.com/plugin/limited-offline-mode)

This plugin is designed to integrate with the **Limited Offline Mode** plugin.

When enabled, players listed in LOM's `allowed-users.txt` can bypass Discord verification, even when the proxy is in `online-mode`. This is useful for allowing specific accounts (like camera bots or test accounts) to join without needing a Discord account, while still enforcing verification for all other online players.

To enable this, set the path to LOM's `allowed-users.txt` in your `config.properties`. If this path is left empty, the integration is disabled.

## Security
- This plugin is designed to run on a Velocity proxy set to `online-mode=true`.
- **Integration with Limited Offline Mode**: For securely allowing specific offline accounts, this plugin can integrate with `Limited Offline Mode`. See the "Integrations" section for details.
- **IP Blocking**: To prevent brute-force attacks, verification failures are counted per IP. After a configurable number of failures (default: 3), the IP is temporarily blocked (default: 5 minutes).

## Data Storage
Verified accounts are saved in:

`plugins/discordauth/verified.json`

This file is automatically updated when commands succeed via DM with the bot.

## License
MIT License
