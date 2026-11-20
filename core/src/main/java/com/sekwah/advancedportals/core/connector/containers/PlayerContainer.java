package com.sekwah.advancedportals.core.connector.containers;

import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import java.awt.*;
import java.util.UUID;

/**
 * Just a temporary container for whenever advanced portals needs to get data
 * from a player
 */
public interface PlayerContainer extends EntityContainer, HasPermission {
    UUID getUUID();

    void sendMessage(String message);

    void sendActionBar(String message);

    void giveItem(String material, String itemName, String... itemDescription);

    /**
     * Send packets down any channel except the minecraft: one.
     * @param channel
     * @param bytes
     * @return
     */
    boolean sendPacket(String channel, byte[] bytes);

    void playSound(String sound, float volume, float pitch);

    void runTask(Runnable runnable);

    ServerContainer getServer();

    GameMode getGameMode();

    /**
     * Teleports the player and executes {@code onComplete} once the teleport
     * finishes on the player's owning thread. Implementations that teleport
     * asynchronously (such as Folia) override this to chain the callback after
     * {@code teleportAsync} succeeds.
     */
    default boolean teleport(PlayerLocation location, Runnable onComplete) {
        boolean result = teleport(location);
        if (result && onComplete != null) {
            onComplete.run();
        }
        return result;
    }

    default void drawLine(Vector start, Vector end, Color color,
                          float particleDensity) {
        Vector direction = end.subtract(start);
        double length = direction.length();
        if (length == 0) {
            return;
        }
        direction = direction.normalize();
        for (double i = 0; i <= length; i += particleDensity) {
            Vector pos = start.add(direction.multiply(i));
            this.spawnColoredDust(pos, 1, color);
        }
    }

    default void spawnColoredDust(Vector pos, int count, Color color) {
        spawnColoredDust(pos, 0, 0, 0, count, color);
    }

    void spawnColoredDust(Vector pos, double xSpread, double ySpread,
                          double zSpread, int count, Color color);
}
