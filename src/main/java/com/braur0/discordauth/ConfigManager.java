package com.braur0.discordauth;

import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.io.InputStream;
import java.io.OutputStream;

public class ConfigManager {

    private final Properties properties = new Properties();
    private final Logger logger;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.logger = logger;
        try {
            // Create the data directory if it doesn't exist
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.properties");

            // Create default config file if it doesn't exist
            if (!Files.exists(configFile)) {
                properties.setProperty("discord.token", "YOUR_DISCORD_BOT_TOKEN_HERE"); // Default bot token
                properties.setProperty("discord.guildId", "123456789012345678");        // Default guild ID
                properties.setProperty("discord.roleId", "987654321098765432");         // Default role ID
                properties.setProperty("discord.adminId", "000000000000000000");        // Default admin ID
                properties.setProperty("security.maxFailures", "3");                     // Default max failures
                properties.setProperty("security.blockMinutes", "5");                    // Default block time in minutes
                properties.setProperty("integration.lom.allowedUsersPath", "plugins/limited-offline-mode/allowed-users.txt"); // Default path to LOM's user file
                try (OutputStream out = Files.newOutputStream(configFile)) {
                    properties.store(out, "DiscordAuth Config");
                }
            }

            // Load properties from the config file
            try (InputStream in = Files.newInputStream(configFile)) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config file", e);
        }
    }

    // Returns the Discord bot token
    public String getToken() {
        return properties.getProperty("discord.token");
    }

    // Returns the Discord guild ID as a long
    public long getGuildId() {
        try {
            return Long.parseLong(properties.getProperty("discord.guildId"));
        } catch (NumberFormatException e) {
            logger.error("Invalid Guild ID in config.properties. It must be a valid number.");
            throw new RuntimeException("Invalid Guild ID in configuration", e);
        }
    }

    // Returns the Discord role ID as a long
    public long getRoleId() {
        try {
            return Long.parseLong(properties.getProperty("discord.roleId"));
        } catch (NumberFormatException e) {
            logger.error("Invalid Role ID in config.properties. It must be a valid number.");
            throw new RuntimeException("Invalid Role ID in configuration", e);
        }
    }

    // Returns the Discord admin ID as a long
    public long getAdminId() {
        try {
            // Admin ID can be optional, so we handle it more gracefully.
            return Long.parseLong(properties.getProperty("discord.adminId", "0"));
        } catch (NumberFormatException e) {
            logger.warn("Invalid value for 'discord.adminId' in config.properties. Using default value (0).");
            return 0L;
        }
    }

    // Returns the max failed attempts
    public int getMaxFailures() {
        try {
            return Integer.parseInt(properties.getProperty("security.maxFailures", "3"));
        } catch (NumberFormatException e) {
            logger.warn("Invalid value for 'security.maxFailures' in config.properties. Using default value (3).");
            return 3;
        }
    }

    // Returns the block time in minutes
    public long getBlockMinutes() {
        try {
            return Long.parseLong(properties.getProperty("security.blockMinutes", "5"));
        } catch (NumberFormatException e) {
            logger.warn("Invalid value for 'security.blockMinutes' in config.properties. Using default value (5).");
            return 5;
        }
    }

    // Returns the path to LOM's allowed users file. Can be empty.
    public String getLomAllowedUsersPath() {
        return properties.getProperty("integration.lom.allowedUsersPath", "");
    }
}
