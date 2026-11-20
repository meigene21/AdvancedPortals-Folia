package com.sekwah.advancedportals.folia.connector.container;

import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.connector.containers.GameMode;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.connector.containers.ServerContainer;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.folia.AdvancedPortalsPlugin;
import com.sekwah.advancedportals.shadowed.inject.Inject;
import java.awt.*;
import java.util.Arrays;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Folia-safe player container wrapping Bukkit {@link Player} with EntityScheduler
 * dispatch and {@link Player#teleportAsync(Location)} support.
 */
public class FoliaPlayerContainer
    extends FoliaEntityContainer implements PlayerContainer {
    private static final double MAX_PARTICLE_DISTANCE_SQUARED = 180.0 * 180.0;

    @Inject
    private AdvancedPortalsCore portalsCore;

    private final Player player;

    public FoliaPlayerContainer(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public UUID getUUID() {
        return player.getUniqueId();
    }

    @Override
    public void sendMessage(String message) {
        if (message == null || message.isBlank()
            || message.equals(Lang.getPositivePrefix())
            || message.equals(Lang.getNegativePrefix())) {
            return;
        }
        player.sendMessage(message);
    }

    @Override
    public void runTask(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        this.player.getScheduler().run(
            AdvancedPortalsPlugin.getInstance(),
            task -> {
                if (this.player.isOnline()) {
                    runnable.run();
                }
            },
            null);
    }

    private void runOnEntityThread(Runnable runnable) {
        if (Bukkit.isOwnedByCurrentRegion(this.player)) {
            runnable.run();
        } else {
            runTask(runnable);
        }
    }

    @Override
    public void sendActionBar(String message) {
        if (message == null) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    TextComponent.fromLegacyText(message));
    }

    @Override
    public boolean isOp() {
        return this.player.isOp();
    }

    @Override
    public boolean teleport(PlayerLocation location) {
        return teleport(location, null);
    }

    @Override
    public boolean teleport(PlayerLocation location, Runnable onComplete) {
        if (location == null || location.getWorldName() == null) {
            return false;
        }
        World world = Bukkit.getWorld(location.getWorldName());
        if (world == null) {
            return false;
        }
        Location targetLoc = new Location(
            world, location.getPosX(), location.getPosY(), location.getPosZ(),
            location.getYaw(), location.getPitch());
        this.player.teleportAsync(targetLoc).thenAccept(success -> {
            if (Boolean.TRUE.equals(success) && onComplete != null) {
                this.player.getScheduler().run(
                    AdvancedPortalsPlugin.getInstance(),
                    task -> {
                        if (this.player.isOnline()) {
                            onComplete.run();
                        }
                    },
                    null);
            }
        });
        return true;
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.player.hasPermission(permission);
    }

    @Override
    public void giveItem(String material, String itemName,
                         String... itemDescription) {
        Material mat = Material.getMaterial(material);
        if (mat == null) {
            return;
        }
        runOnEntityThread(() -> {
            ItemStack regionSelector = new ItemStack(mat);
            ItemMeta selectorMeta = regionSelector.getItemMeta();
            if (selectorMeta != null) {
                selectorMeta.setDisplayName(itemName);
                selectorMeta.setLore(Arrays.asList(itemDescription));
                regionSelector.setItemMeta(selectorMeta);
            }
            this.player.getInventory().addItem(regionSelector);
        });
    }

    @Override
    public boolean sendPacket(String channel, byte[] bytes) {
        player.sendPluginMessage(AdvancedPortalsPlugin.getInstance(), channel,
                                 bytes);
        return true;
    }

    @Override
    public GameMode getGameMode() {
        try {
            return GameMode.valueOf(this.player.getGameMode().name());
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void playSound(String sound, float volume, float pitch) {
        runOnEntityThread(() -> this.player.playSound(
            this.player.getLocation(), sound, volume, pitch));
    }

    @Override
    public ServerContainer getServer() {
        return new FoliaServerContainer(this.player.getServer());
    }

    @Override
    public void spawnColoredDust(Vector position, double xSpread,
                                 double ySpread, double zSpread, int count,
                                 Color color) {
        runOnEntityThread(() -> {
            if (!this.player.isOnline()) {
                return;
            }
            Location playerLoc = this.player.getLocation();
            World world = playerLoc.getWorld();
            if (world == null) {
                return;
            }
            double dx = playerLoc.getX() - position.getX();
            double dy = playerLoc.getY() - position.getY();
            double dz = playerLoc.getZ() - position.getZ();
            if ((dx * dx + dy * dy + dz * dz) > MAX_PARTICLE_DISTANCE_SQUARED) {
                return;
            }

            Particle.DustOptions dustOptions = new Particle.DustOptions(
                org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(),
                                         color.getBlue()),
                1.5f);
            this.player.spawnParticle(Particle.DUST, position.getX(),
                                      position.getY(), position.getZ(), count,
                                      xSpread, ySpread, zSpread, 0,
                                      dustOptions);
        });
    }
}
