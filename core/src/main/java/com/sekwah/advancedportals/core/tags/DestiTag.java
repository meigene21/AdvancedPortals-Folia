package com.sekwah.advancedportals.core.tags;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.connector.containers.ServerContainer;
import com.sekwah.advancedportals.core.destination.Destination;
import com.sekwah.advancedportals.core.effect.WarpEffect;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.registry.WarpEffectRegistry;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.services.DestinationServices;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DestiTag implements Tag.Activation, Tag.AutoComplete, Tag.Split {
    public static final String TAG_NAME = "destination";

    @Inject
    ConfigRepository configRepository;

    @Inject
    DestinationServices destinationServices;

    @Inject
    WarpEffectRegistry warpEffectRegistry;

    @Inject
    ServerContainer serverContainer;

    @Inject
    private transient TagRegistry tagRegistry;

    private final TagType[] tagTypes = new TagType[] {TagType.PORTAL};

    @Override
    public TagType[] getTagTypes() {
        return tagTypes;
    }

    @Override
    public String getName() {
        return TAG_NAME;
    }

    @Override
    public String[] getAliases() {
        return new String[] {"desti"};
    }

    @Override
    public String description() {
        return Lang.translate("tag.desti.description");
    }

    @Override
    public boolean preActivated(TagTarget target, PlayerContainer player,
                                ActivationData activeData, String[] argData) {
        if (argData == null || argData.length == 0) {
            return false;
        }
        String selectedArg =
            argData[ThreadLocalRandom.current().nextInt(argData.length)];

        activeData.setMetadata(TAG_NAME, selectedArg);

        if (activeData.getMetadata(ProxyTag.TAG_NAME) != null) {
            return true;
        }

        Destination destination =
            destinationServices.getDestination(selectedArg);
        if (destination == null) {
            player.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "desti.error.notfound", selectedArg));
            return false;
        }

        // Check and trigger all tags on the destination
        for (DataTag destiTag : destination.getArgs()) {
            Tag.Activation activationHandler =
                tagRegistry.getActivationHandler(destiTag.NAME,
                                                 Tag.TagType.DESTINATION);
            if (activationHandler != null
                && !activationHandler.preActivated(
                    target, player, activeData, destiTag.VALUES)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void postActivated(TagTarget target, PlayerContainer player,
                              ActivationData activationData, String[] argData) {
        if (activationData.getMetadata(ProxyTag.TAG_NAME) != null) {
            return;
        }

        String selectedArg = activationData.getMetadata(TAG_NAME);
        Destination destination =
            destinationServices.getDestination(selectedArg);
        if (destination != null) {
            for (DataTag destiTag : destination.getArgs()) {
                Tag.Activation activationHandler =
                    tagRegistry.getActivationHandler(destiTag.NAME,
                                                     Tag.TagType.DESTINATION);
                if (activationHandler != null) {
                    activationHandler.postActivated(target, player,
                                                    activationData, argData);
                }
            }
            String message = activationData.getMetadata(MessageTag.TAG_NAME);
            if (message == null) {
                sendMessage(player,
                            Lang.translateInsertVariables(
                                "desti.warpdesti.warp",
                                destination.getName().replaceAll("_", " ")));
            }
        }
    }

    @Override
    public boolean activated(TagTarget target, PlayerContainer player,
                             ActivationData activationData, String[] argData) {
        if (activationData.getMetadata(ProxyTag.TAG_NAME) != null) {
            return true;
        }

        String selectedArg = activationData.getMetadata(TAG_NAME);
        Destination destination =
            destinationServices.getDestination(selectedArg);
        if (destination == null) {
            return false;
        }

        if (serverContainer.getWorld(destination.getLoc().getWorldName())
            == null) {
            player.sendMessage(Lang.getNegativePrefix()
                               + Lang.translateInsertVariables(
                                   "desti.error.invalidworld",
                                   destination.getName(),
                                   destination.getLoc().getWorldName()));
            return false;
        }

        WarpEffect.Visual warpEffectVisual =
            warpEffectRegistry.getVisualEffect(
                configRepository.getWarpVisual());
        WarpEffect.Sound warpEffectSound =
            warpEffectRegistry.getSoundEffect(
                configRepository.getWarpSound());
        boolean warpEffectEnabled = configRepository.getWarpEffectEnabled();

        if (warpEffectEnabled) {
            if (warpEffectVisual != null) {
                warpEffectVisual.onWarpVisual(player,
                                              WarpEffect.Action.ENTER);
            }
            if (warpEffectSound != null) {
                warpEffectSound.onWarpSound(player,
                                            WarpEffect.Action.ENTER);
            }
        }

        Runnable postTeleport = null;
        if (warpEffectEnabled) {
            postTeleport = () -> {
                if (warpEffectVisual != null) {
                    warpEffectVisual.onWarpVisual(player,
                                                  WarpEffect.Action.EXIT);
                }
                if (warpEffectSound != null) {
                    warpEffectSound.onWarpSound(player, WarpEffect.Action.EXIT);
                }
            };
        }

        player.teleport(destination.getLoc(), postTeleport);

        for (DataTag destiTag : destination.getArgs()) {
            Tag.Activation activationHandler =
                tagRegistry.getActivationHandler(destiTag.NAME,
                                                 Tag.TagType.DESTINATION);
            if (activationHandler != null) {
                activationHandler.activated(target, player, activationData,
                                            argData);
            }
        }

        activationData.setWarpStatus(ActivationData.WarpedStatus.WARPED);
        return true;
    }

    public void sendMessage(PlayerContainer player, String message) {
        if (this.configRepository.warpMessageOnActionBar()) {
            player.sendActionBar(Lang.convertColors(message));
        } else if (this.configRepository.warpMessageInChat()) {
            player.sendMessage(Lang.getPositivePrefix() + " "
                               + Lang.convertColors(message));
        }
    }

    @Override
    public List<String> autoComplete(String argData) {
        return destinationServices.getDestinationNames();
    }
}
