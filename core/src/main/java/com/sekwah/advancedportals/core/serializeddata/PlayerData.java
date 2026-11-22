package com.sekwah.advancedportals.core.serializeddata;

import java.util.HashMap;
import java.util.Map;

/**
 * Possibly one of the only files in this package not designed to be
 * serialised.
 *
 * <p>Any temporary data about players will be stored here and cleaned up when
 * the player leaves the server.
 *
 * <p>This is not a place to store long term data e.g. if you want to make a
 * player unable to use a portal over hours/days.
 */
public class PlayerData {
    /**
     * Portal selection position 1
     */
    private volatile BlockLocation pos1;

    /**
     * Portal selection position 2
     */
    private volatile BlockLocation pos2;

    /**
     * If to show portals near the player
     */
    private volatile boolean portalVisible = false;

    /**
     * If to show destination blocks near the player
     */
    private volatile boolean destiVisible;

    /**
     * If the player is in a portal. Stops re-triggering.
     */
    private transient volatile String inPortal = null;

    /**
     * The next time System.currentTimeMillis() a player can use a portal.
     */
    private transient volatile long joinCooldown;

    private transient volatile long portalBlockCooldown;

    // Keep HashMap type for YAML ReflectiveConstructor compatibility; guard
    // all accesses with synchronization for Folia thread safety.
    private final HashMap<String, Long> perPortalCooldowns = new HashMap<>();

    private volatile String selectedPortal;

    public BlockLocation getPos1() {
        return pos1;
    }

    public void setPos1(BlockLocation pos1) {
        this.pos1 = pos1;
    }

    public BlockLocation getPos2() {
        return pos2;
    }

    public void setPos2(BlockLocation pos2) {
        this.pos2 = pos2;
    }

    public long getJoinCooldownLeft() {
        return joinCooldown - System.currentTimeMillis();
    }

    public void setJoinCooldown(long joinCooldown) {
        this.joinCooldown = System.currentTimeMillis() + joinCooldown;
    }

    public String getSelectedPortal() {
        return selectedPortal;
    }

    public void setSelectedPortal(String selectedPortal) {
        this.selectedPortal = selectedPortal;
    }

    public boolean isPortalVisible() {
        return portalVisible;
    }

    public void setPortalVisible(boolean showPortals) {
        this.portalVisible = showPortals;
    }

    public boolean isDestiVisible() {
        return destiVisible;
    }

    public void setDestiVisible(boolean destiVisible) {
        this.destiVisible = destiVisible;
    }

    public String inPortal() {
        return this.inPortal;
    }

    public void setInPortal(String inPortal) {
        this.inPortal = inPortal;
    }

    public void setPortalBlockCooldown(long portalBlockCooldown) {
        this.portalBlockCooldown =
            System.currentTimeMillis() + portalBlockCooldown;
    }

    public boolean hasJoinCooldown() {
        return System.currentTimeMillis() < joinCooldown;
    }

    public boolean getPortalBlockCooldown() {
        return System.currentTimeMillis() < portalBlockCooldown;
    }

    public void setPortalCooldown(String portalName, long cooldown) {
        synchronized (perPortalCooldowns) {
            perPortalCooldowns.put(portalName,
                                   System.currentTimeMillis() + cooldown);
        }
    }

    public boolean hasPortalCooldown(String portalName) {
        synchronized (perPortalCooldowns) {
            Long expiry = perPortalCooldowns.get(portalName);
            return expiry != null && System.currentTimeMillis() < expiry;
        }
    }

    public double getPortalCooldownLeft(String portalName) {
        synchronized (perPortalCooldowns) {
            Long expiry = perPortalCooldowns.get(portalName);
            if (expiry == null) {
                return 0;
            }
            return expiry - System.currentTimeMillis();
        }
    }

    public void setNotInPortal() {
        this.inPortal = null;
    }

    /**
     * Merges persisted fields from an asynchronously loaded {@link PlayerData}
     * snapshot without clobbering live session state (join cooldown, inPortal,
     * or newer selections/cooldowns).
     */
    public void mergePersisted(PlayerData loaded) {
        if (loaded == null || loaded == this) {
            return;
        }
        if (this.pos1 == null && loaded.pos1 != null) {
            this.pos1 = loaded.pos1;
        }
        if (this.pos2 == null && loaded.pos2 != null) {
            this.pos2 = loaded.pos2;
        }
        if (this.selectedPortal == null && loaded.selectedPortal != null) {
            this.selectedPortal = loaded.selectedPortal;
        }
        if (!this.portalVisible && loaded.portalVisible) {
            this.portalVisible = true;
        }
        if (!this.destiVisible && loaded.destiVisible) {
            this.destiVisible = true;
        }
        long now = System.currentTimeMillis();
        synchronized (loaded.perPortalCooldowns) {
            synchronized (this.perPortalCooldowns) {
                for (Map.Entry<String, Long> entry :
                     loaded.perPortalCooldowns.entrySet()) {
                    Long loadedExpiry = entry.getValue();
                    if (loadedExpiry != null && loadedExpiry > now) {
                        this.perPortalCooldowns.merge(
                            entry.getKey(), loadedExpiry, Math::max);
                    }
                }
            }
        }
    }
}
