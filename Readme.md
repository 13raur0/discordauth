# DiscordAuth
DiscordAuth is a Velocity plugin that links Minecraft accounts with Discord accounts for authentication.

## Installation
1. Place the `discordauth-<version>.jar` file in your Velocity `plugins` folder.

## Configuration
When you run the plugin for the first time, it will generate:

plugins/discordauth/config.properties

Edit the file and fill in the following:
discord.token : Your Discord bot token
discord.guildId : The Discord server ID for authentication
discord.roleId : The role ID required for verification
discord.adminId : The Discord ID that can execute admin commands like deleting verification

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

!verify <verification code>

3. If the user has the specified server and role in `config.properties`, their Minecraft account will be linked with their Discord account.  
4. Subsequent connections do not require verification.

### Admin Commands
!delete <DiscordID>

- Can only be executed by the user specified in `adminId`.
- If successful, the following occurs automatically:
  - The player is disconnected from Velocity.
  - The role is removed from the user in the Discord server.

## Security
- Offline-mode users skip Discord verification.  
- Always run Velocity in online mode for secure operation.  
- Verification failures are counted per IP. After 3 failures, the IP is blocked for 5 minutes.  
  Immediate unblocking requires restarting Velocity.

## Data Storage
Verified accounts are saved in:

plugins/discordauth/verified.json

This file is automatically updated when commands succeed via DM with the bot.

## License
Apache 2.0