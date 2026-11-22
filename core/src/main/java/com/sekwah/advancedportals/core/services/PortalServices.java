package com.sekwah.advancedportals.core.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sekwah.advancedportals.core.connector.containers.GameMode;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.connector.containers.WorldContainer;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.repository.IPortalRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.tags.NameTag;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.util.PlayerUtils;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.core.warphandler.TriggerType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

@Singleton
public class PortalServices {
    @Inject
    private IPortalRepository portalRepository;

    @Inject
    private transient PlayerDataServices playerDataServices;

    @Inject
    private ConfigRepository configRepository;

    private final Map<String, AdvancedPortal> portalCache =
        new ConcurrentHashMap<>();

    private volatile Map<String, List<AdvancedPortal>> portalsByWorld =
        Collections.emptyMap();

    @Inject
    TagRegistry tagRegistry;

    private synchronized void rebuildWorldIndex() {
        Map<String, List<AdvancedPortal>> byWorld = new HashMap<>();
        for (AdvancedPortal portal : portalCache.values()) {
            BlockLocation min = portal.getMinLoc();
            if (min != null && min.getWorldName() != null) {
                byWorld
                    .computeIfAbsent(min.getWorldName(), k -> new ArrayList<>())
                    .add(portal);
            }
        }
        Map<String, List<AdvancedPortal>> immutable = new HashMap<>();
        for (Map.Entry<String, List<AdvancedPortal>> entry :
             byWorld.entrySet()) {
            immutable.put(entry.getKey(),
                          Collections.unmodifiableList(entry.getValue()));
        }
        this.portalsByWorld = Collections.unmodifiableMap(immutable);
    }

    private List<AdvancedPortal> getPortalsInWorld(String worldName) {
        if (worldName == null) {
            return Collections.emptyList();
        }
        List<AdvancedPortal> list = this.portalsByWorld.get(worldName);
        return list != null ? list : Collections.emptyList();
    }

    public void loadPortals() {
        List<String> portalNames = portalRepository.getAllNames();
        Map<String, AdvancedPortal> loaded = new HashMap<>();
        for (String name : portalNames) {
            AdvancedPortal portal = portalRepository.get(name);
            if (portal == null) {
                continue;
            }
            portal.updateBounds(portal.getMinLoc(), portal.getMaxLoc());
            loaded.put(name, portal);
        }
        portalCache.keySet().retainAll(loaded.keySet());
        portalCache.putAll(loaded);
        rebuildWorldIndex();
    }

    public boolean inPortalRegionProtected(BlockLocation loc) {
        if (loc == null) {
            return false;
        }
        int radius = configRepository.getProtectionRadius();
        for (AdvancedPortal portal : getPortalsInWorld(loc.getWorldName())) {
            if (portal.isLocationInPortal(loc, radius)) {
                return true;
            }
        }
        return false;
    }

    public boolean inPortalRegionProtected(PlayerLocation loc) {
        if (loc == null) {
            return false;
        }
        int radius = configRepository.getProtectionRadius();
        for (AdvancedPortal portal : getPortalsInWorld(loc.getWorldName())) {
            if (portal.isLocationInPortal(loc, radius)) {
                return true;
            }
        }
        return false;
    }

    public boolean inPortalRegion(BlockLocation loc, int extraBlocks) {
        return inPortalRegionGetName(loc, extraBlocks) != null;
    }

    public String inPortalRegionGetName(PlayerLocation loc, int extraBlocks) {
        return inPortalRegionGetName(loc.toBlockPos(), extraBlocks);
    }

    public String inPortalRegionGetName(BlockLocation loc) {
        return inPortalRegionGetName(loc, 0);
    }

    public String inPortalRegionGetName(BlockLocation loc, int extraBlocks) {
        if (loc == null) {
            return null;
        }
        for (AdvancedPortal portal : getPortalsInWorld(loc.getWorldName())) {
            if (portal.isLocationInPortal(loc, extraBlocks)) {
                return portal.getName();
            }
        }
        return null;
    }

    public AdvancedPortal getPortal(String portalName) {
        return portalCache.get(portalName);
    }

    public boolean movePortal(String portalName, BlockLocation pos1,
                              BlockLocation pos2) {
        AdvancedPortal portal = portalCache.get(portalName);
        if (portal == null) {
            return false;
        }
        portal.updateBounds(pos1, pos2);
        boolean saved = this.portalRepository.save(portalName, portal);
        rebuildWorldIndex();
        return saved;
    }

    public enum PortalActivationResult {
        NOT_IN_PORTAL,
        PORTAL_TELEPORTED,
        PORTAL_ACTIVATED,
        PORTAL_DENIED
    }

    public PortalActivationResult checkPortalActivation(
        PlayerContainer player, PlayerLocation toLoc, TriggerType triggerType) {
        if (toLoc == null) {
            return PortalActivationResult.NOT_IN_PORTAL;
        }
        List<AdvancedPortal> worldPortals =
            getPortalsInWorld(toLoc.getWorldName());
        if (worldPortals.isEmpty()) {
            PlayerData playerData = playerDataServices.getPlayerData(player);
            if (playerData.inPortal() != null) {
                playerData.setNotInPortal();
            }
            return PortalActivationResult.NOT_IN_PORTAL;
        }
        if (configRepository.blockSpectatorMode()
            && player.getGameMode() == GameMode.SPECTATOR) {
            return PortalActivationResult.NOT_IN_PORTAL;
        }

        BlockLocation blockLoc = toLoc.toBlockPos();
        BlockLocation blockEntityTopLoc = blockLoc.addY(player.getHeight());
        PlayerData playerData = playerDataServices.getPlayerData(player);

        // Lazily resolve world blocks only when the player is actually inside
        // a portal's bounding box. Avoids 4 world lookups per move event and
        // prevents off-region block reads on Folia when moving near borders.
        WorldContainer world = null;
        String blockMaterial = null;
        boolean blockMaterialWaterlogged = false;
        boolean loadedBottomBlock = false;
        String blockEntityTopMaterial = null;
        boolean blockEntityTopMaterialWaterlogged = false;
        boolean loadedTopBlock = false;

        for (AdvancedPortal portal : worldPortals) {
            boolean inBottom = portal.isLocationInPortal(toLoc);
            boolean inTop =
                !inBottom && portal.isLocationInPortal(blockEntityTopLoc);
            boolean checkTop =
                inTop
                || (inBottom && portal.isLocationInPortal(blockEntityTopLoc));

            if (!inBottom && !inTop) {
                continue;
            }

            if (world == null) {
                world = player.getWorld();
            }

            boolean checkWaterLogged = portal.isTriggerBlock("water");
            boolean triggered = false;

            if (inBottom) {
                if (!loadedBottomBlock) {
                    blockMaterial = world.getBlock(blockLoc);
                    blockMaterialWaterlogged = world.isWaterlogged(blockLoc);
                    loadedBottomBlock = true;
                }
                triggered = (checkWaterLogged && blockMaterialWaterlogged)
                    || portal.isTriggerBlock(blockMaterial);
            }

            if (!triggered && checkTop) {
                if (!loadedTopBlock) {
                    blockEntityTopMaterial = world.getBlock(blockEntityTopLoc);
                    blockEntityTopMaterialWaterlogged =
                        world.isWaterlogged(blockEntityTopLoc);
                    loadedTopBlock = true;
                }
                triggered =
                    (checkWaterLogged && blockEntityTopMaterialWaterlogged)
                    || portal.isTriggerBlock(blockEntityTopMaterial);
            }

            if (triggered) {
                String portalName = portal.getName();
                if (Objects.equals(playerData.inPortal(), portalName)) {
                    return PortalActivationResult.PORTAL_DENIED;
                }
                switch (portal.activate(player, triggerType)) {
                    case SUCCESS:
                        return PortalActivationResult.PORTAL_ACTIVATED;
                    case FAILED_DO_KNOCKBACK:
                        playerData.setInPortal(portal.getName());
                        double strength =
                            configRepository.getThrowbackStrength();
                        PlayerUtils.throwPlayerBack(player, strength);
                        return PortalActivationResult.PORTAL_DENIED;
                    default:
                        return PortalActivationResult.PORTAL_DENIED;
                }
            }
        }
        if (playerData.inPortal() != null) {
            playerData.setNotInPortal();
        }
        return PortalActivationResult.NOT_IN_PORTAL;
    }

    public List<String> getPortalNames() {
        return portalRepository.getAllNames();
    }

    public List<AdvancedPortal> getPortals() {
        return new ArrayList<>(portalCache.values());
    }

    public boolean removePortal(String name, PlayerContainer player) {
        try {
            this.portalRepository.delete(name);
            this.portalCache.remove(name);
            rebuildWorldIndex();
            return true;
        } catch (IllegalArgumentException e) {
            if (player != null) {
                player.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.error.invalidname"));
            }
            return false;
        }
    }

    public AdvancedPortal createPortal(PlayerContainer player,
                                       ArrayList<DataTag> tags) {
        PlayerData tempData = playerDataServices.getPlayerData(player);

        if (tempData.getPos1() == null || tempData.getPos2() == null) {
            player.sendMessage(
                Lang.getNegativePrefix()
                + Lang.translate("portal.error.selection.missing"));
            return null;
        }

        if (!tempData.getPos1().getWorldName().equals(
                tempData.getPos2().getWorldName())) {
            player.sendMessage(
                Lang.getNegativePrefix()
                + Lang.translate("portal.error.selection.differentworlds"));
            return null;
        }

        return createPortal(player, tempData.getPos1(), tempData.getPos2(),
                            tags);
    }

    /**
     * @param player if null it is assumed an automation is creating the portal
     *     so checks for permissions are not needed
     * @param pos1
     * @param pos2
     * @param tags
     * @return
     */
    public AdvancedPortal createPortal(@Nullable PlayerContainer player,
                                       BlockLocation pos1, BlockLocation pos2,
                                       List<DataTag> tags) {
        try {
            // Find the tag with the "name" NAME
            DataTag nameTag =
                tags.stream()
                    .filter(tag -> tag.NAME.equals(NameTag.TAG_NAME))
                    .findFirst()
                    .orElse(null);

            String name = nameTag == null ? null : nameTag.VALUES[0];
            if (nameTag == null || name == null || name.isEmpty()) {
                if (player != null)
                    player.sendMessage(
                        Lang.getNegativePrefix()
                        + Lang.translate("command.error.noname"));
                return null;
            } else if (this.portalRepository.containsKey(name)) {
                if (player != null)
                    player.sendMessage(Lang.getNegativePrefix()
                                       + Lang.translateInsertVariables(
                                           "command.error.nametaken", name));
                return null;
            }

            AdvancedPortal portal =
                new AdvancedPortal(pos1, pos2, tagRegistry, playerDataServices);

            for (DataTag portalTag : tags) {
                portal.setArgValues(portalTag);
            }

            for (DataTag portalTag : tags) {
                Tag.Creation creation =
                    tagRegistry.getCreationHandler(portalTag.NAME);
                if (creation != null) {
                    if (!creation.created(portal, player, portalTag.VALUES)) {
                        return null;
                    }
                }
            }

            if (this.portalRepository.save(name, portal)) {
                this.portalCache.put(name, portal);
                rebuildWorldIndex();
            } else {
                return null;
            }

            return portal;
        } catch (IllegalArgumentException e) {
            if (player != null) {
                player.sendMessage(
                    Lang.getNegativePrefix()
                    + Lang.translate("command.error.invalidname"));
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            if (player != null)
                player.sendMessage(Lang.getNegativePrefix()
                                   + Lang.translate("portal.error.save"));
            return null;
        }
    }

    public boolean removePlayerSelection(PlayerContainer player) {
        return false;
    }
}
