package com.sekwah.advancedportals.core.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.repository.IPlayerDataRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public final class PlayerDataServices {
    private final Map<UUID, PlayerData> tempDataMap = new ConcurrentHashMap<>();

    @Inject
    private IPlayerDataRepository tempDataRepository;

    @Inject
    private ConfigRepository configRepository;

    public PlayerData getPlayerData(PlayerContainer player) {
        return tempDataMap.computeIfAbsent(player.getUUID(), uuid -> {
            PlayerData tempData = tempDataRepository.get(uuid.toString());
            if (tempData == null) {
                tempData = new PlayerData();
            }
            return tempData;
        });
    }

    public void setJoinCooldown(PlayerContainer player) {
        PlayerData tempData = getPlayerData(player);
        tempData.setJoinCooldown(configRepository.getPortalCooldown() * 1000L);
    }

    public void playerLeave(PlayerContainer player) {
        UUID uuid = player.getUUID();
        PlayerData data = tempDataMap.remove(uuid);
        if (!configRepository.disablePlayerDataSaving() && data != null) {
            tempDataRepository.save(uuid.toString(), data);
        }
    }

    public void playerSelectorActivate(PlayerContainer player,
                                       BlockLocation blockLoc,
                                       boolean leftClick) {
        PlayerData tempData = getPlayerData(player);
        if (leftClick) {
            tempData.setPos1(blockLoc);
        } else {
            tempData.setPos2(blockLoc);
        }
        player.sendMessage(Lang.translateInsertVariables(
            "portal.selector.poschange", leftClick ? "1" : "2",
            blockLoc.getPosX(), blockLoc.getPosY(), blockLoc.getPosZ()));
    }
}
