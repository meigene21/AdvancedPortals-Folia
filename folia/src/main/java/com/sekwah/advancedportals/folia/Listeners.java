package com.sekwah.advancedportals.folia;

import com.sekwah.advancedportals.core.CoreListeners;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.folia.connector.container.FoliaEntityContainer;
import com.sekwah.advancedportals.folia.connector.container.FoliaPlayerContainer;
import com.sekwah.advancedportals.folia.utils.ContainerHelpers;
import com.sekwah.advancedportals.shadowed.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Some of these will be passed to the core listener to handle the events,
 * others it's easier to just check directly.
 */
public class Listeners implements Listener {
    @Inject
    private CoreListeners coreListeners;

    @Inject
    private PortalServices portalServices;

    @Inject
    private ConfigRepository configRepository;

    // Entity and portal events
    @EventHandler
    public void onJoinEvent(PlayerJoinEvent event) {
        coreListeners.playerJoin(new FoliaPlayerContainer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        coreListeners.playerLeave(new FoliaPlayerContainer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveEvent(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getWorld() == null) {
            return;
        }
        // Skip pure head-rotation (yaw/pitch only) moves to reduce per-region load
        if (from.getWorld() == to.getWorld() && from.getX() == to.getX()
            && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        coreListeners.playerMove(new FoliaPlayerContainer(event.getPlayer()),
                                 ContainerHelpers.toPlayerLocation(to));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortalEvent(EntityPortalEvent event) {
        if (!this.coreListeners.entityPortalEvent(
                new FoliaEntityContainer(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortalEnterEvent(EntityPortalEnterEvent event) {
        if (event.getEntity() instanceof Player player) {
            Location loc = event.getLocation();
            if (loc == null || loc.getWorld() == null) {
                return;
            }
            if (!this.coreListeners.playerPortalEvent(
                    new FoliaPlayerContainer(player),
                    ContainerHelpers.toPlayerLocation(loc))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalEvent(PlayerPortalEvent event) {
        Location from = event.getFrom();
        if (from == null || from.getWorld() == null) {
            return;
        }
        if (!this.coreListeners.playerPortalEvent(
                new FoliaPlayerContainer(event.getPlayer()),
                ContainerHelpers.toPlayerLocation(from))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamEvent(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player
            && (event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause()
                    == EntityDamageEvent.DamageCause.FIRE_TICK)) {
            if (this.coreListeners.preventEntityCombust(
                    new FoliaEntityContainer(event.getEntity()))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombustEntityEvent(EntityCombustEvent event) {
        if (this.coreListeners.preventEntityCombust(
                new FoliaEntityContainer(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) {
            Location blockloc = event.getBlock().getLocation();
            ItemStack itemInHand = event.getItemInHand();

            if (blockloc == null || blockloc.getWorld() == null
                || itemInHand == null || itemInHand.getItemMeta() == null) {
                return;
            }

            if (!this.coreListeners.blockPlace(
                    new FoliaPlayerContainer(event.getPlayer()),
                    new BlockLocation(
                        blockloc.getWorld().getName(), blockloc.getBlockX(),
                        blockloc.getBlockY(), blockloc.getBlockZ()),
                    event.getBlockPlaced().getType().toString(),
                    itemInHand.getType().toString(),
                    itemInHand.getItemMeta().getDisplayName())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onWorldChangeEvent(PlayerChangedWorldEvent event) {
        coreListeners.worldChange(new FoliaPlayerContainer(event.getPlayer()));
    }

    @EventHandler
    public void onItemInteract(PlayerInteractEvent event) {
        if (!event.isCancelled()
            && (event.getAction() == Action.LEFT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            && event.getItem() != null) {
            if (event.getClickedBlock() == null)
                return;
            if (event.getItem().getItemMeta() == null)
                return;

            Location blockloc = event.getClickedBlock().getLocation();

            if (blockloc.getWorld() == null)
                return;

            boolean allowEvent = this.coreListeners.playerInteractWithBlock(
                new FoliaPlayerContainer(event.getPlayer()),
                event.getClickedBlock().getType().toString(),
                event.getMaterial().toString(),
                event.getItem().getItemMeta().getDisplayName(),
                new BlockLocation(blockloc.getWorld().getName(),
                                  blockloc.getBlockX(), blockloc.getBlockY(),
                                  blockloc.getBlockZ()),
                event.getAction() == Action.LEFT_CLICK_BLOCK);
            event.setCancelled(!allowEvent);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void spawnMobEvent(CreatureSpawnEvent event) {
        if (event.getSpawnReason()
                == CreatureSpawnEvent.SpawnReason.NETHER_PORTAL
            && portalServices.inPortalRegionProtected(
                ContainerHelpers.toPlayerLocation(event.getLocation()))) {
            event.setCancelled(true);
        }
    }

    // Block events

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!configRepository.getStopWaterFlow()) {
            return;
        }
        if (!coreListeners.blockPlace(null,
                                      ContainerHelpers.toBlockLocation(
                                          event.getBlock().getLocation()),
                                      event.getBlock().getType().toString(),
                                      null, null)
            || !coreListeners.blockPlace(null,
                                         ContainerHelpers.toBlockLocation(
                                             event.getToBlock().getLocation()),
                                         event.getBlock().getType().toString(),
                                         null, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack itemInHand =
            event.getPlayer().getInventory().getItemInMainHand();
        if (!coreListeners.blockBreak(
                new FoliaPlayerContainer(event.getPlayer()),
                ContainerHelpers.toBlockLocation(
                    event.getBlock().getLocation()),
                event.getBlock().getType().toString(),
                itemInHand == null ? null : itemInHand.getType().toString(),
                itemInHand == null || itemInHand.getItemMeta() == null
                    ? null
                    : itemInHand.getItemMeta().getDisplayName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplosion(EntityExplodeEvent event) {
        if (!configRepository.getPortalProtection())
            return;

        List<Block> blockList = event.blockList();
        for (int i = 0; i < blockList.size(); i++) {
            Block block = blockList.get(i);
            if (portalServices.inPortalRegionProtected(
                    ContainerHelpers.toBlockLocation(block.getLocation()))) {
                blockList.remove(i);
                i--;
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!configRepository.getDisableGatewayBeam()) {
            return;
        }
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        List<AdvancedPortal> checkPortals = portalServices.getPortals()
            .stream()
            .filter(portal -> Objects.equals(portal.getMinLoc().getWorldName(),
                                             worldName))
            .filter(portal -> portal.isTriggerBlock(
                        Material.END_GATEWAY.toString()))
            .filter(portal -> portal.isChunkOverlapping(chunk.getX(),
                                                        chunk.getZ()))
            .collect(Collectors.toList());

        if (checkPortals.isEmpty()) {
            return;
        }

        Arrays.stream(chunk.getTileEntities(false))
            .filter(blockState -> blockState.getType() == Material.END_GATEWAY)
            .forEach(endGatewayPortal -> {
                Location loc = endGatewayPortal.getLocation();
                if (portalServices.inPortalRegion(
                        new BlockLocation(loc.getWorld().getName(),
                                          loc.getBlockX(), loc.getBlockY(),
                                          loc.getBlockZ()),
                        2)) {
                    EndGateway tileState = (EndGateway) endGatewayPortal;
                    tileState.setAge(Long.MIN_VALUE);
                    tileState.update();
                }
            });
    }
}
