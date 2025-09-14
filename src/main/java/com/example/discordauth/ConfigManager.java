package com.example.discordauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.io.InputStream;
import java.io.OutputStream;

public class ConfigManager {

    private final Properties properties = new Properties();

    public ConfigManager(Path dataDirectory) {
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
        return Long.parseLong(properties.getProperty("discord.guildId"));
    }

    // Returns the Discord role ID as a long
    public long getRoleId() {
        return Long.parseLong(properties.getProperty("discord.roleId"));
    }

    // Returns the Discord admin ID as a long
    public long getAdminId() {
        return Long.parseLong(properties.getProperty("discord.adminId"));
    }
}
