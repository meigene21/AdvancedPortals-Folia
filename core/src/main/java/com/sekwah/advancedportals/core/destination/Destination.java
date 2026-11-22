package com.sekwah.advancedportals.core.destination;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.registry.TagTarget;
import com.sekwah.advancedportals.core.serializeddata.DataTag;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.warphandler.ActivationData;
import com.sekwah.advancedportals.core.warphandler.Tag;
import com.sekwah.advancedportals.core.warphandler.TriggerType;
import java.util.*;

/**
 * Possibly look at adding the ability to add some tags to destinations such as
 * permissions. Would make it easier to add permissions to block access to
 * certain areas and such. Could be a different permission system or just it
 * takes the tags on the destination and automatically applies them when a
 * portal wants to warp to there. (Of course it would not work cross server
 * unless the data was communicated and checked first however that could affect
 * performance and would definitely affect speed)
 *
 * @author sekwah41
 */
public class Destination implements TagTarget {
    @Inject
    transient TagRegistry tagRegistry;

    private volatile PlayerLocation loc;

    private final HashMap<String, String[]> args = new HashMap<>();
    private transient volatile List<DataTag> destiTags =
        Collections.emptyList();

    private transient volatile boolean isSorted = false;

    public Destination() {
        this.loc = new PlayerLocation();
    }

    public Destination(PlayerLocation loc) {
        this.loc = loc;
    }

    @Override
    public String[] getArgValues(String argName) {
        synchronized (this.args) {
            return this.args.get(argName);
        }
    }

    @Override
    public void setArgValues(String argName, String[] argValue) {
        synchronized (this.args) {
            this.args.put(argName, argValue);
            this.isSorted = false;
        }
    }

    @Override
    public void addArg(String argName, String argValues) {
        synchronized (this.args) {
            this.isSorted = false;
        }
    }

    public void setArgValues(DataTag portalTag) {
        this.setArgValues(portalTag.NAME, portalTag.VALUES);
    }

    public void removeArg(String arg) {
        synchronized (this.args) {
            this.args.remove(arg);
            this.isSorted = false;
        }
    }

    private synchronized void updateDestiTagList() {
        if (this.isSorted) {
            return;
        }
        List<DataTag> sortedTags = new ArrayList<>();
        synchronized (this.args) {
            for (Map.Entry<String, String[]> entry : args.entrySet()) {
                sortedTags.add(new DataTag(entry.getKey(), entry.getValue()));
            }
        }

        sortedTags.sort(Comparator.comparingInt(o -> {
            Tag tag = tagRegistry.getTag(o.NAME);
            if (tag instanceof Tag.OrderPriority) {
                Tag.OrderPriority tagPriority = (Tag.OrderPriority) tag;
                return tagPriority.getPriority().ordinal();
            } else {
                return Tag.Priority.NORMAL.ordinal();
            }
        }));
        this.destiTags = Collections.unmodifiableList(sortedTags);
        this.isSorted = true;
    }

    public boolean activate(PlayerContainer player) {
        ActivationData data = new ActivationData(TriggerType.MANUAL);
        if (this.portalActivate(player, data)) {
            this.postActivate(player, data);
            return true;
        }
        return false;
    }

    public boolean portalActivate(PlayerContainer player, ActivationData data) {
        if (!isSorted) {
            updateDestiTagList();
        }
        List<DataTag> tagsSnapshot = this.destiTags;
        for (DataTag destiTag : tagsSnapshot) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                destiTag.NAME, Tag.TagType.DESTINATION);
            if (activationHandler != null
                && !activationHandler.preActivated(
                    this, player, data, this.getArgValues(destiTag.NAME))) {
                return false;
            }
        }
        for (DataTag destiTag : tagsSnapshot) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                destiTag.NAME, Tag.TagType.DESTINATION);
            if (activationHandler != null) {
                activationHandler.activated(this, player, data,
                                            this.getArgValues(destiTag.NAME));
            }
        }
        return true;
    }

    public void postActivate(PlayerContainer player, ActivationData data) {
        if (!isSorted) {
            updateDestiTagList();
        }
        List<DataTag> tagsSnapshot = this.destiTags;
        for (DataTag destiTag : tagsSnapshot) {
            Tag.Activation activationHandler = tagRegistry.getActivationHandler(
                destiTag.NAME, Tag.TagType.DESTINATION);
            if (activationHandler != null) {
                activationHandler.postActivated(
                    this, player, data, this.getArgValues(destiTag.NAME));
            }
        }
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

    public String getName() {
        String[] nameValues = this.getArgValues("name");
        return (nameValues != null && nameValues.length > 0) ? nameValues[0]
                                                             : "";
    }

    @Override
    public Tag.TagType targetTagType() {
        return Tag.TagType.DESTINATION;
    }

    public PlayerLocation getLoc() {
        return loc;
    }

    public void setLoc(PlayerLocation loc) {
        this.loc = loc;
    }
}
