package com.sekwah.advancedportals.folia.connector.container;

import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.connector.containers.EntityContainer;
import com.sekwah.advancedportals.core.connector.containers.WorldContainer;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import com.sekwah.advancedportals.folia.AdvancedPortalsPlugin;
import com.sekwah.advancedportals.shadowed.inject.Inject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Folia-safe entity container wrapping Bukkit {@link Entity}.
 */
public class FoliaEntityContainer implements EntityContainer {
    @Inject
    private AdvancedPortalsCore portalsCore;

    private final Entity entity;

    public FoliaEntityContainer(Entity entity) {
        this.entity = entity;
    }

    @Override
    public PlayerLocation getLoc() {
        Location loc = this.entity.getLocation();
        return new PlayerLocation(loc.getWorld().getName(), loc.getX(),
                                  loc.getY(), loc.getZ(), loc.getYaw(),
                                  loc.getPitch());
    }

    @Override
    public BlockLocation getBlockLoc() {
        Location loc = this.entity.getLocation();
        return new BlockLocation(loc.getWorld().getName(), loc.getBlockX(),
                                 loc.getBlockY(), loc.getBlockZ());
    }

    @Override
    public double getHeight() {
        return this.entity.getHeight();
    }

    @Override
    public boolean teleport(PlayerLocation location) {
        if (location == null || location.getWorldName() == null) {
            return false;
        }
        World world = Bukkit.getWorld(location.getWorldName());
        if (world == null) {
            return false;
        }
        this.entity.teleportAsync(new Location(
            world, location.getPosX(), location.getPosY(), location.getPosZ(),
            location.getYaw(), location.getPitch()));
        return true;
    }

    @Override
    public WorldContainer getWorld() {
        return new FoliaWorldContainer(this.entity.getWorld());
    }

    @Override
    public String getName() {
        return this.entity.getName();
    }

    @Override
    public String getWorldName() {
        return this.entity.getWorld().getName();
    }

    @Override
    public void setVelocity(Vector vector) {
        org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(
            vector.getX(), vector.getY(), vector.getZ());
        if (Bukkit.isOwnedByCurrentRegion(this.entity)) {
            this.entity.setVelocity(velocity);
        } else {
            this.entity.getScheduler().run(
                AdvancedPortalsPlugin.getInstance(),
                task -> this.entity.setVelocity(velocity),
                null);
        }
    }
}
