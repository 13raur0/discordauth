package com.braur0.discordauth;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service class to handle Discord authentication for Minecraft players.
 */
public class DiscordService extends ListenerAdapter {

    private final JDA jda;
    private final Logger logger;
    private final ProxyServer server;
    private final VerifiedStorage verifiedStorage;
    private final DiscordAuthPlugin plugin;

    // Map to track pending verification codes for each player
    private final Map<UUID, String> pendingCodes = new ConcurrentHashMap<>();
    private final Map<String, UUID> codeToPlayerId = new ConcurrentHashMap<>();

    private final long allowedGuildId;
    private final long allowedRoleId;
    private final long adminId;

    public DiscordService(String token, ProxyServer server, Logger logger,
                          VerifiedStorage verifiedStorage,
                          long allowedGuildId, long allowedRoleId, long adminId,
                          DiscordAuthPlugin plugin) {
        this.server = server;
        this.logger = logger;
        this.verifiedStorage = verifiedStorage;
        this.allowedGuildId = allowedGuildId;
        this.allowedRoleId = allowedRoleId;
        this.adminId = adminId;
        this.plugin = plugin;

        try {
            // Initialize Discord bot with required gateway intents
            jda = JDABuilder.createDefault(token,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            logger.info("Discord bot started successfully!");
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Discord bot", e);
        }
    }

    /**
     * Generate a 6-digit verification code for a Minecraft player.
     */
    public String generateVerificationCode(UUID playerId) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
        pendingCodes.put(playerId, code);
        codeToPlayerId.put(code, playerId);
        return code;
    }

    /**
     * Check if a player has already been verified.
     */
    public boolean isVerified(UUID playerId) {
        return verifiedStorage.isVerified(playerId);
    }

    /**
     * Remove a pending verification code for a player.
     */
    public void removePendingCode(UUID playerId) {
        String code = pendingCodes.remove(playerId);
        if (code != null) {
            codeToPlayerId.remove(code);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Only handle private messages (DMs)
        if (!event.isFromType(ChannelType.PRIVATE)) return;

        User user = event.getAuthor();
        if (user.isBot()) return; // Ignore bot messages

        Guild guild = jda.getGuildById(allowedGuildId);
        String content = event.getMessage().getContentRaw().trim();

        if (guild == null) {
            event.getChannel().sendMessage("❌ Target guild not found.").queue();
            return;
        }

        // Admin command to delete verification
        if (content.startsWith("!delete ")) {
            if (user.getIdLong() != adminId) {
                event.getChannel().sendMessage("❌ You do not have permission.").queue();
                return;
            }

            String discordId = content.substring(8).trim();
            UUID playerId = verifiedStorage.getPlayerIdByDiscordId(discordId);

            if (playerId != null) {
                // Remove role from Discord member
                guild.retrieveMemberById(discordId).queue(member -> {
                    Role role = guild.getRoleById(allowedRoleId);
                    guild.removeRoleFromMember(member, role).queue();
                });

                // Remove verification from storage
                verifiedStorage.remove(playerId);

                // Disconnect player if online
                server.getPlayer(playerId).ifPresent(p ->
                        p.disconnect(Component.text("Your Discord verification has been removed."))
                );

                event.getChannel().sendMessage("✅ Verification removed: " + discordId).queue();
                logger.info("Admin removed verification for DiscordID: " + discordId);

                removePendingCode(playerId);

            } else {
                event.getChannel().sendMessage("❌ Not registered: " + discordId).queue();
            }
            return;
        }

        // Only handle verification commands
        if (!content.startsWith("!verify")) return;

        String[] args = content.split(" ");
        if (args.length != 2) {
            event.getChannel().sendMessage("❌ Please enter the code correctly.").queue();
            return;
        }

        String code = args[1];

        // Find the player ID corresponding to this code
        UUID playerId = codeToPlayerId.get(code);

        if (playerId == null) {
            event.getChannel().sendMessage("❌ Invalid or expired code.").queue();
            return;
        }

        // Retrieve the member and proceed with verification
        guild.retrieveMemberById(user.getId()).queue(
                member -> verifyMember(member, playerId, event, user),
                error -> {
                    event.getChannel().sendMessage("❌ You must be a member of the Discord server.").queue();
                    logger.warn("Failed to retrieve member for user " + user.getName() + ": " + error.getMessage());
                }
        );
    }

    /**
     * Verify a Discord member and link to Minecraft player.
     */
    private void verifyMember(Member member, UUID playerId, MessageReceivedEvent event, User user) {
        boolean hasRole = member.getRoles().stream()
                .mapToLong(Role::getIdLong)
                .anyMatch(id -> id == allowedRoleId);

        if (!hasRole) {
            event.getChannel().sendMessage("❌ Required role not assigned.").queue();
            return;
        }

        // Add verified player to storage
        verifiedStorage.add(playerId, user.getId());
        removePendingCode(playerId);

        // Notify Minecraft player
        server.getPlayer(playerId).ifPresent(p ->
                p.sendMessage(Component.text("✅ Discord verification successful!"))
        );

        // Notify Discord user
        event.getChannel().sendMessage("✅ Verification successful! Linked to Minecraft account.").queue();
        logger.info("Player " + playerId + " verified via Discord user " + user.getName());
    }
}
