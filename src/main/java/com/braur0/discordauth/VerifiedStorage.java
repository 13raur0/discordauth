package com.braur0.discordauth;

import org.slf4j.Logger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Storage class to manage verified Minecraft players linked with Discord accounts.
 */
public class VerifiedStorage {

    private final Path file;
    private final Gson gson = new Gson();
    private final Logger logger;

    // Map to store verified players: UUID -> Discord ID
    private final Map<UUID, String> verifiedMap = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordIdToUuidMap = new ConcurrentHashMap<>();

    public VerifiedStorage(Path file, Logger logger) {
        this.logger = logger;
        this.file = file;
        load();
    }

    /**
     * Load verified players from JSON file.
     */
    private void load() {
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file);
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> loaded = gson.fromJson(json, type);

                Map<UUID, String> tempMap = new ConcurrentHashMap<>();
                loaded.forEach((k, v) -> tempMap.put(UUID.fromString(k), v));

                verifiedMap.clear();
                verifiedMap.putAll(tempMap);
                // Populate the reverse map
                discordIdToUuidMap.clear();
                verifiedMap.forEach((uuid, discordId) -> discordIdToUuidMap.put(discordId, uuid));
            }
        } catch (IOException e) {
            logger.error("Failed to load verified players from {}", file, e);
        }
    }

    /**
     * Save the current verified players to the JSON file.
     */
    public void save() {
        try {
            Map<String, String> toSave = new HashMap<>();
            verifiedMap.forEach((k, v) -> toSave.put(k.toString(), v));
            Files.writeString(file, gson.toJson(toSave));
        } catch (IOException e) {
            logger.error("Failed to save verified players to {}", file, e);
        }
    }

    /**
     * Add a verified player and save the storage.
     */
    public void add(UUID uuid, String discordId) {
        verifiedMap.put(uuid, discordId);
        discordIdToUuidMap.put(discordId, uuid);
        save();
    }

    /**
     * Check if a player is verified.
     */
    public boolean isVerified(UUID uuid) {
        return verifiedMap.containsKey(uuid);
    }

    /**
     * Remove a verified player and save the storage.
     */
    public void remove(UUID uuid) {
        String discordId = verifiedMap.remove(uuid);
        if (discordId != null) {
            discordIdToUuidMap.remove(discordId);
        }
        save();
    }

    /**
     * Get the Discord ID linked to a player.
     */
    public String getDiscordId(UUID uuid) {
        return verifiedMap.get(uuid);
    }

    /**
     * Get the Minecraft player UUID by Discord ID.
     * Returns null if not found.
     */
    public UUID getPlayerIdByDiscordId(String discordId) {
        return discordIdToUuidMap.get(discordId);
    }
}
