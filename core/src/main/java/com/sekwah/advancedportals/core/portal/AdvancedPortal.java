package com.sekwah.advancedportals.core.portal;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.permissions.Permissions;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.services.PlayerDataServices;
import com.sekwah.advancedportals.core.tags.TriggerBlockTag;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.core.warphandler.TriggerType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * @author sekwah41
 */
public class AdvancedPortal implements TagTarget {
    @Inject
    private transient TagRegistry tagRegistry;

    private volatile BlockLocation maxLoc;

    private volatile BlockLocation minLoc;

    private final HashMap<String, String[]> args = new HashMap<>();

    private transient volatile List<DataTag> portalTags =
        Collections.emptyList();

    private transient volatile boolean isSorted = false;

    @Inject
    private transient PlayerDataServices playerDataServices;

    @Inject
    private transient ConfigRepository configRepository;

    public AdvancedPortal() {
        this.minLoc = new BlockLocation();
        this.maxLoc = new BlockLocation();
    }

    public AdvancedPortal(BlockLocation minLoc, BlockLocation maxLoc,
                          TagRegistry tagRegistry,
                          PlayerDataServices playerDataServices) {
        this.tagRegistry = tagRegistry;
        this.playerDataServices = playerDataServices;
        this.updateBounds(minLoc, maxLoc);
    }

    public BlockLocation getMaxLoc() {
        return this.maxLoc;
    }

    public BlockLocation getMinLoc() {
        return this.minLoc;
    }

    @Override
    public String[] getArgValues(String argName) {
        synchronized (this.args) {
            return this.args.get(argName);
        }
    }

    @Override
    public void setArgValues(String argName, String[] argValues) {
        synchronized (this.args) {
            this.args.put(argName, argValues);
            this.isSorted = false;
        }
    }

    @Override
    public void addArg(String argName, String argValues) {
        // TODO need to add the ability to add args after creation
    }

    public synchronized void updatePortalTagList() {
        if (this.isSorted) {
            return;
        }
        List<DataTag> sortedTags = new ArrayList<>();
        synchronized (this.args) {
            for (Map.Entry<String, String[]> entry : args.entrySet()) {
                sortedTags.add(new DataTag(entry.getKey(), entry.getValue()));
            }
        }

        // sort the tags by priority
        sortedTags.sort(Comparator.comparingInt(o -> {
            Tag tag = tagRegistry.getTag(o.NAME);
            if (tag instanceof Tag.OrderPriority) {
                Tag.OrderPriority tagPriority = (Tag.OrderPriority) tag;
                return tagPriority.getPriority().ordinal();
            } else {
                return Tag.Priority.NORMAL.ordinal();
            }
        }));
        this.portalTags = Collections.unmodifiableList(sortedTags);
        this.isSorted = true;
    }

    @Override
    public void removeArg(String arg) {
        synchronized (this.args) {
            this.args.remove(arg);
            this.isSorted = false;
        }
    }

    /**
     * Updates the bounds of the portal based on the provided locations.
     *
     * @param loc1 The first location.
     * @param loc2 The second location.
     */
    public void updateBounds(BlockLocation loc1, BlockLocation loc2) {
        int minX = Math.min(loc1.getPosX(), loc2.getPosX());
        int minY = Math.min(loc1.getPosY(), loc2.getPosY());
        int minZ = Math.min(loc1.getPosZ(), loc2.getPosZ());

        int maxX = Math.max(loc1.getPosX(), loc2.getPosX());
        int maxY = Math.max(loc1.getPosY(), loc2.getPosY());
        int maxZ = Math.max(loc1.getPosZ(), loc2.getPosZ());

        this.minLoc = new BlockLocation(loc1.getWorldName(), minX, minY, minZ);
        this.maxLoc = new BlockLocation(loc2.getWorldName(), maxX, maxY, maxZ);
    }

    private void runPostActivated(List<DataTag> tagsSnapshot,
                                  PlayerContainer player, ActivationData data) {
        for (DataTag portalTag : tagsSnapshot) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                portalTag.NAME, Tag.TagType.PORTAL);
            if (activationHandler != null) {
                activationHandler.postActivated(
                    this, player, data, this.getArgValues(portalTag.NAME));
            }
        }
    }

    /**
     * @param player        The player on the server attempting to use an
     *     advanced
     *                      portal
     * @param triggerType   The type of trigger that activated the portal
     * @return Whether the portal was successfully activated
     */
    public ActivationResult activate(PlayerContainer player,
                                     TriggerType triggerType) {
        if (!isSorted) {
            updatePortalTagList();
        }
        List<DataTag> tagsSnapshot = this.portalTags;

        PlayerData playerData = playerDataServices.getPlayerData(player);

        if (!Permissions.USE_PORTAL.hasPermission(player)) {
            player.sendMessage(Lang.translate("portal.error.nopermission"));
            return ActivationResult.FAILED_DO_KNOCKBACK;
        }

        if (playerData.hasJoinCooldown()) {
            int cooldown =
                (int) Math.ceil(playerData.getJoinCooldownLeft() / 1000D);
            player.sendMessage(Lang.translateInsertVariables(
                "portal.cooldown.join", cooldown,
                Lang.translate(cooldown == 1 ? "time.second"
                                             : "time.seconds")));
            if (configRepository.playFailSound()) {
                player.playSound(
                    "block.portal.travel", 0.05f,
                    ThreadLocalRandom.current().nextFloat() * 0.4F + 0.8F);
            }
            return ActivationResult.FAILED_DO_KNOCKBACK;
        }

        ActivationData data = new ActivationData(triggerType);

        for (DataTag portalTag : tagsSnapshot) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                portalTag.NAME, Tag.TagType.PORTAL);
            if (activationHandler != null) {
                boolean preActivated = activationHandler.preActivated(
                    this, player, data, this.getArgValues(portalTag.NAME));

                if (!preActivated) {
                    if (activationHandler instanceof Tag.DenyBehavior) {
                        Tag.DenyBehavior denyBehaviorHandler =
                            (Tag.DenyBehavior) activationHandler;
                        if (denyBehaviorHandler.getDenyBehavior().equals(
                                Tag.DenyBehavior.Behaviour.SILENT)) {
                            return ActivationResult.FAILED_DO_NOTHING;
                        }
                    }
                    return ActivationResult.FAILED_DO_KNOCKBACK;
                }
            }
        }
        for (DataTag portalTag : tagsSnapshot) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                portalTag.NAME, Tag.TagType.PORTAL);
            if (activationHandler != null
                && !activationHandler.activated(
                    this, player, data, this.getArgValues(portalTag.NAME))) {
                return ActivationResult.FAILED_DO_KNOCKBACK;
            }
        }

        Consumer<Consumer<Boolean>> deferredTeleport =
            data.getDeferredTeleport();
        if (deferredTeleport != null && data.hasActivated()) {
            // Mark player in-portal immediately so rapid move ticks do not
            // re-trigger activation while teleportAsync is in flight.
            playerData.setPortalBlockCooldown(1000);
            playerData.setInPortal(this.getName());
            deferredTeleport.accept(succeeded -> {
                if (Boolean.TRUE.equals(succeeded)) {
                    runPostActivated(tagsSnapshot, player, data);
                } else {
                    playerData.setNotInPortal();
                }
            });
            return ActivationResult.SUCCESS;
        }

        runPostActivated(tagsSnapshot, player, data);
        if (data.hasActivated()) {
            playerData.setPortalBlockCooldown(1000);
            playerData.setInPortal(this.getName());
            return ActivationResult.SUCCESS;
        }
        return ActivationResult.FAILED_DO_KNOCKBACK;
    }

    public boolean isLocationInPortal(BlockLocation loc) {
        return this.isLocationInPortal(loc, 0);
    }

    public boolean isLocationInPortal(PlayerLocation loc) {
        return this.isLocationInPortal(loc.toBlockPos(), 0);
    }

    public boolean isLocationInPortal(PlayerLocation loc, int additionalArea) {
        return this.isLocationInPortal(loc.toBlockPos(), additionalArea);
    }

    public boolean isLocationInPortal(BlockLocation loc, int additionalArea) {
        BlockLocation min = this.minLoc;
        BlockLocation max = this.maxLoc;
        if (loc == null || min == null || max == null) {
            return false;
        }
        double playerX = loc.getPosX();
        double playerY = loc.getPosY();
        double playerZ = loc.getPosZ();

        return Objects.equals(loc.getWorldName(), min.getWorldName())
            && playerX >= min.getPosX() - additionalArea
            && playerX < max.getPosX() + 1 + additionalArea
            && playerY >= min.getPosY() - additionalArea
            && playerY < max.getPosY() + 1 + additionalArea
            && playerZ >= min.getPosZ() - additionalArea
            && playerZ < max.getPosZ() + 1 + additionalArea;
    }

    public void setArgValues(DataTag portalTag) {
        this.setArgValues(portalTag.NAME, portalTag.VALUES);
    }

    public ArrayList<DataTag> getArgs() {
        ArrayList<DataTag> tagList = new ArrayList<>();
        synchronized (this.args) {
            for (Map.Entry<String, String[]> entry : this.args.entrySet()) {
                tagList.add(new DataTag(entry.getKey(), entry.getValue()));
            }
        }
        return tagList;
    }

    public boolean isTriggerBlock(String blockMaterial) {
        if (blockMaterial == null) {
            return false;
        }
        String[] triggerBlocks = this.getArgValues(TriggerBlockTag.TAG_NAME);
        if (triggerBlocks != null) {
            for (String triggerBlock : triggerBlocks) {
                if (blockMaterial.equalsIgnoreCase(triggerBlock)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getName() {
        String[] nameValues = getArgValues("name");
        return (nameValues != null && nameValues.length > 0) ? nameValues[0]
                                                             : "";
    }

    @Override
    public Tag.TagType targetTagType() {
        return Tag.TagType.PORTAL;
    }

    public boolean isChunkOverlapping(int chunkX, int chunkZ) {
        BlockLocation min = this.minLoc;
        BlockLocation max = this.maxLoc;
        if (min == null || max == null) {
            return false;
        }
        int chunkMinX = chunkX * 16;
        int chunkMinZ = chunkZ * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        int portalMinX = min.getPosX();
        int portalMaxX = max.getPosX();
        int portalMinZ = min.getPosZ();
        int portalMaxZ = max.getPosZ();

        return !(portalMaxX < chunkMinX || portalMinX > chunkMaxX
                 || portalMaxZ < chunkMinZ || portalMinZ > chunkMaxZ);
    }
}
