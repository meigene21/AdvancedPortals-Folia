package com.sekwah.advancedportals.folia.connector.container;

import com.sekwah.advancedportals.core.connector.containers.WorldContainer;
import com.sekwah.advancedportals.core.data.BlockAxis;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.folia.AdvancedPortalsPlugin;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Waterlogged;

public class FoliaWorldContainer implements WorldContainer {
    private final World world;

    // Should only be false for 1.13 and 1.13.2, though just to avoid possible
    // crashes
    private static boolean endGatewaySetAgeExists;

    static {
        try {
            endGatewaySetAgeExists =
                EndGateway.class.getMethod("setAge", long.class) != null;
        } catch (NoSuchMethodException e) {
            endGatewaySetAgeExists = false;
        }
    }

    public FoliaWorldContainer(World world) {
        this.world = world;
    }

    private void runAtChunk(int chunkX, int chunkZ, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(this.world, chunkX, chunkZ)) {
            task.run();
        } else {
            Bukkit.getRegionScheduler().execute(
                AdvancedPortalsPlugin.getInstance(), this.world, chunkX, chunkZ,
                task);
        }
    }

    @Override
    public void setBlock(BlockLocation location, String material) {
        Material mat = Material.getMaterial(material, false);
        if (mat == null || location == null) {
            return;
        }
        int x = location.getPosX();
        int y = location.getPosY();
        int z = location.getPosZ();
        runAtChunk(x >> 4, z >> 4,
                   () -> this.world.getBlockAt(x, y, z).setType(mat));
    }

    @Override
    public String getBlock(BlockLocation location) {
        if (location == null) {
            return Material.AIR.toString();
        }
        int x = location.getPosX();
        int y = location.getPosY();
        int z = location.getPosZ();
        if (!Bukkit.isOwnedByCurrentRegion(this.world, x >> 4, z >> 4)) {
            return Material.AIR.toString();
        }
        return this.world.getBlockAt(x, y, z).getType().toString();
    }

    @Override
    public boolean isWaterlogged(BlockLocation location) {
        if (location == null) {
            return false;
        }
        int x = location.getPosX();
        int y = location.getPosY();
        int z = location.getPosZ();
        if (!Bukkit.isOwnedByCurrentRegion(this.world, x >> 4, z >> 4)) {
            return false;
        }
        Block block = world.getBlockAt(x, y, z);
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Waterlogged) {
            return ((Waterlogged) blockData).isWaterlogged();
        }
        return false;
    }

    @Override
    public BlockAxis getBlockAxis(BlockLocation location) {
        if (location == null) {
            return null;
        }
        int x = location.getPosX();
        int y = location.getPosY();
        int z = location.getPosZ();
        if (!Bukkit.isOwnedByCurrentRegion(this.world, x >> 4, z >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(x, y, z);
        BlockData matData = block.getBlockData();
        if (matData instanceof Orientable) {
            Orientable rotatable = (Orientable) matData;
            try {
                return BlockAxis.valueOf(rotatable.getAxis().toString());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void setBlockAxis(BlockLocation location, BlockAxis axis) {
        if (location == null || axis == null) {
            return;
        }
        int x = location.getPosX();
        int y = location.getPosY();
        int z = location.getPosZ();
        runAtChunk(x >> 4, z >> 4, () -> {
            Block block = world.getBlockAt(x, y, z);
            BlockData matData = block.getBlockData();
            if (matData instanceof Orientable) {
                Orientable rotatable = (Orientable) matData;
                rotatable.setAxis(Axis.valueOf(axis.toString()));
                block.setBlockData(rotatable);
            }
        });
    }

    @Override
    public void disableBeacon(BlockLocation location) {
        if (!endGatewaySetAgeExists || location == null) {
            return;
        }
        int x = location.getPosX();
        int y = location.getPosY();
        int z = location.getPosZ();
        runAtChunk(x >> 4, z >> 4, () -> {
            Block block = this.world.getBlockAt(x, y, z);
            if (block.getType() == Material.END_GATEWAY
                && block.getState() instanceof EndGateway) {
                EndGateway endGateway = (EndGateway) block.getState();
                endGateway.setAge(Long.MIN_VALUE);
                endGateway.update();
            }
        });
    }

    @Override
    public void disableBeacon(AdvancedPortal portal) {
        if (!endGatewaySetAgeExists || portal == null) {
            return;
        }
        BlockLocation maxLoc = portal.getMaxLoc();
        BlockLocation minLoc = portal.getMinLoc();
        if (minLoc == null || maxLoc == null) {
            return;
        }

        int minX = minLoc.getPosX();
        int minY = minLoc.getPosY();
        int minZ = minLoc.getPosZ();
        int maxX = maxLoc.getPosX();
        int maxY = maxLoc.getPosY();
        int maxZ = maxLoc.getPosZ();

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        // Dispatch one RegionScheduler task per overlapping chunk so portals
        // spanning multiple Folia regions (or triggered from console/another
        // region) never violate region ownership.
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                final int startX = Math.max(minX, cx << 4);
                final int endX = Math.min(maxX, (cx << 4) + 15);
                final int startZ = Math.max(minZ, cz << 4);
                final int endZ = Math.min(maxZ, (cz << 4) + 15);
                runAtChunk(cx, cz, () -> {
                    for (int x = startX; x <= endX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = startZ; z <= endZ; z++) {
                                Block block = world.getBlockAt(x, y, z);
                                if (block.getType() == Material.END_GATEWAY
                                    && block.getState() instanceof EndGateway) {
                                    EndGateway tileState =
                                        (EndGateway) block.getState();
                                    tileState.setAge(Long.MIN_VALUE);
                                    tileState.update();
                                }
                            }
                        }
                    }
                });
            }
        }
    }
}
