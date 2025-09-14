package com.example.discordauth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "discordauth",
        name = "DiscordAuth",
        version = "1.0.0",
        description = "A plugin to link Velocity with Discord authentication",
        authors = {"YourName"}
)
public class DiscordAuthPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final VerifiedStorage storage;
    private final DiscordService discordService;

    // Map to track failed login attempts per IP
    private final Map<String, Integer> failedCount = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
    private final int MAX_FAIL = 3; // Block after 3 failed attempts
    private final long BLOCK_TIME = TimeUnit.MINUTES.toMillis(5); // Block duration: 5 minutes

    @Inject
    public DiscordAuthPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        Path dataDirectory = Path.of("plugins/discordauth");
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create plugin data directory", e);
        }

        // Initialize storage for verified players
        this.storage = new VerifiedStorage(dataDirectory.resolve("verified.json"));

        // Load configuration
        ConfigManager config = new ConfigManager(dataDirectory);
        String token = config.getToken();
        long guildId = config.getGuildId();
        long roleId = config.getRoleId();
        long adminId = config.getAdminId();

        // Initialize Discord service and pass plugin reference
        this.discordService = new DiscordService(token, server, logger, storage,
                guildId, roleId, adminId, this);

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

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Skip Discord check for offline-mode/test players
        if (!player.isOnlineMode()) {
            player.sendMessage(Component.text("⚠️ Discord check skipped for test/offline-mode player."));
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
                    if (!discordService.isVerified(playerId)) {
                        player.disconnect(Component.text("Verification failed. Repeated failures will result in temporary block."));
                        logger.info("Kicked player " + player.getUsername() + " due to unverified Discord account.");

                        // Increment failed attempts for the IP
                        int count = failedCount.getOrDefault(ip, 0) + 1;
                        failedCount.put(ip, count);

                        // Block IP if max failures reached
                        if (count >= MAX_FAIL) {
                            blockedUntil.put(ip, System.currentTimeMillis() + BLOCK_TIME);
                            failedCount.remove(ip);
                            logger.info("IP " + ip + " blocked for " + TimeUnit.MILLISECONDS.toMinutes(BLOCK_TIME) + " minutes due to repeated failed verification.");
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
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Count as failed attempt only if player is unverified
        if (!discordService.isVerified(playerId)) {
            int count = failedCount.getOrDefault(ip, 0) + 1;
            failedCount.put(ip, count);

            // Block IP if max failures reached
            if (count >= MAX_FAIL) {
                blockedUntil.put(ip, System.currentTimeMillis() + BLOCK_TIME);
                failedCount.remove(ip);
                logger.info("IP " + ip + " blocked for " + TimeUnit.MILLISECONDS.toMinutes(BLOCK_TIME) + " minutes due to repeated failed verification.");
            }

            // Remove pending verification code
            discordService.removePendingCode(playerId);

            logger.info("Player " + player.getUsername() + " disconnected while unverified. Failed count: " + count);
        }
    }
}
