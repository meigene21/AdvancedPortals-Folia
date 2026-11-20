package com.sekwah.advancedportals.folia.warpeffects;

import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.effect.WarpEffect;
import com.sekwah.advancedportals.folia.AdvancedPortalsPlugin;
import com.sekwah.advancedportals.folia.connector.container.FoliaPlayerContainer;
import org.bukkit.*;
import org.bukkit.entity.Player;

public class EnderWarpEffect implements WarpEffect.Visual, WarpEffect.Sound {
    @Override
    public void onWarpSound(PlayerContainer playerContainer,
                            WarpEffect.Action action) {
        if (playerContainer instanceof FoliaPlayerContainer) {
            FoliaPlayerContainer foliaPlayerContainer =
                (FoliaPlayerContainer) playerContainer;
            Player player = foliaPlayerContainer.getPlayer();
            Runnable soundTask = () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.getWorld().playSound(player.getLocation(),
                                            "entity.enderman.teleport", 1, 1);
            };
            if (Bukkit.isOwnedByCurrentRegion(player)) {
                soundTask.run();
            } else {
                player.getScheduler().run(AdvancedPortalsPlugin.getInstance(),
                                          task -> soundTask.run(), null);
            }
        }
    }

    @Override
    public void onWarpVisual(PlayerContainer playerContainer,
                             WarpEffect.Action action) {
        if (playerContainer instanceof FoliaPlayerContainer) {
            FoliaPlayerContainer foliaPlayerContainer =
                (FoliaPlayerContainer) playerContainer;
            Player player = foliaPlayerContainer.getPlayer();
            Runnable visualTask = () -> {
                if (!player.isOnline()) {
                    return;
                }
                World world = player.getWorld();
                Location loc = player.getLocation().clone();
                for (int i = 0; i < 10; i++) {
                    world.playEffect(loc, Effect.ENDER_SIGNAL, 0);
                }
                loc.add(0D, 1D, 0D);
                for (int i = 0; i < 10; i++) {
                    world.playEffect(loc, Effect.ENDER_SIGNAL, 0);
                }
            };
            if (Bukkit.isOwnedByCurrentRegion(player)) {
                visualTask.run();
            } else {
                player.getScheduler().run(AdvancedPortalsPlugin.getInstance(),
                                          task -> visualTask.run(), null);
            }
        }
    }
}
