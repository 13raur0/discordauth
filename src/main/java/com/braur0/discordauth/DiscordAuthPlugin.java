package com.braur0.discordauth;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.slf4j.Logger;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "discordauth",
        name = "DiscordAuth",
        version = "1.1.0",
        description = "A plugin to link Velocity with Discord authentication", authors = {"Braur0"}
)
public class DiscordAuthPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final VerifiedStorage storage;
    private final DiscordService discordService;

    // Set to store usernames allowed by LOM
    private final Set<String> lomAllowedUsers = new ConcurrentHashMap<>().newKeySet();

    // Map to track failed login attempts per IP
    private final Map<String, Integer> failedCount = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
    private final int maxFail;
    private final long blockTimeMillis;

    @Inject
    public DiscordAuthPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;

        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create plugin data directory", e);
        }

        // Initialize storage for verified players
        this.storage = new VerifiedStorage(dataDirectory.resolve("verified.json"), logger);

        // Load configuration
        ConfigManager config = new ConfigManager(dataDirectory, logger);
        String token = config.getToken();
        long guildId = config.getGuildId();
        long roleId = config.getRoleId();
        long adminId = config.getAdminId();
        this.maxFail = config.getMaxFailures();
        this.blockTimeMillis = TimeUnit.MINUTES.toMillis(config.getBlockMinutes());


        // Initialize Discord service and pass plugin reference
        this.discordService = new DiscordService(token, server, logger, storage,
                guildId, roleId, adminId, this);

        // Load users from LOM's config file for seamless integration
        loadLomAllowedUsers(config);

        logger.info("DiscordAuthPlugin initialized!");
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        InetSocketAddress address = event.getConnection().getRemoteAddress();
        String ip = address.getAddress().getHostAddress();

        Long until = blockedUntil.get(ip);
        long now = System.currentTimeMillis();

        // Deny login if IP is currently blocked
        if (until != null && now < until) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("You are temporarily blocked due to failed Discord verification.")
            ));
        } else if (until != null && now >= until) {
            // Remove block and reset fail count if block expired
            blockedUntil.remove(ip);
            failedCount.remove(ip);
        }
    }

    private void loadLomAllowedUsers(ConfigManager config) {
        String lomPathStr = config.getLomAllowedUsersPath();

        // If the path is not configured or empty, do nothing.
        if (lomPathStr == null || lomPathStr.isBlank()) {
            logger.info("LOM integration is disabled as 'integration.lom.allowedUsersPath' is not set.");
            return;
        }

        try {
            Path lomConfigFile = Path.of(lomPathStr);
            if (Files.exists(lomConfigFile)) {
                lomAllowedUsers.clear(); // Clear before loading
                Files.readAllLines(lomConfigFile).forEach(line -> {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        lomAllowedUsers.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                });
                logger.info("Successfully loaded {} users from LOM file: {}", lomAllowedUsers.size(), lomConfigFile);
            } else {
                logger.warn("LOM integration enabled, but file not found at: {}", lomConfigFile);
            }
        } catch (Exception e) {
            logger.error("An error occurred while reading LOM's allowed-users.txt from path: {}", lomPathStr, e);
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        final String ip = player.getRemoteAddress().getAddress().getHostAddress();
        final String username = player.getUsername();

        // Integration with "Limited Offline Mode" (LOM)
        // Check if the username is in the list we loaded from LOM's config
        if (lomAllowedUsers.contains(username.toLowerCase(Locale.ROOT))) {
            logger.info("Skipping Discord auth for {} as they are in LOM's allowed-users.txt.", username);
            player.sendMessage(Component.text("✅ Discord check skipped (authenticated by LOM)."));
            return;
        }

        // If player is already verified
        if (discordService.isVerified(playerId)) {
            player.sendMessage(Component.text("✅ Discord account already verified."));
            return;
        }

        // Generate verification code and send instructions
        String code = discordService.generateVerificationCode(playerId);
        String copyText = "!verify " + code;
        player.sendMessage(
                Component.text("Discord verification required. Send the following code via DM on Discord within 1 minute:\n")
                        .append(Component.text(copyText).clickEvent(ClickEvent.copyToClipboard(copyText)))
        );

        // Schedule a task to kick the player after 1 minute if not verified
        server.getScheduler()
                .buildTask(this, () -> {
                    if (player.isActive() && !discordService.isVerified(playerId)) {
                        player.disconnect(Component.text("Verification failed. Repeated failures will result in a temporary block."));
                        logger.info("Kicked player {} due to unverified Discord account.", player.getUsername());

                        // Get the most recent IP address at the moment of kicking
                        final String currentIp = player.getRemoteAddress().getAddress().getHostAddress();

                        // Increment failed attempts for the IP
                        int count = failedCount.getOrDefault(currentIp, 0) + 1;
                        failedCount.put(currentIp, count);

                        // Block IP if max failures reached
                        if (count >= maxFail) {
                            blockedUntil.put(currentIp, System.currentTimeMillis() + blockTimeMillis);
                            failedCount.remove(currentIp);
                            logger.info("IP {} has been blocked for {} minutes due to repeated failed verifications.", currentIp, TimeUnit.MILLISECONDS.toMinutes(blockTimeMillis));
                        }

                        // Remove pending verification code
                        discordService.removePendingCode(playerId);
                    }
                })
                .delay(1, TimeUnit.MINUTES)
                .schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // プレイヤーが未認証のまま切断した場合、保留中の認証コードを削除する
        if (!discordService.isVerified(playerId)) {
            discordService.removePendingCode(playerId);
            logger.info("Player {} disconnected while unverified. Pending verification code removed.", player.getUsername());
        }
    }
}
