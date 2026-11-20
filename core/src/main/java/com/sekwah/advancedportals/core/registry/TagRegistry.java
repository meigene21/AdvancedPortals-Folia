package com.sekwah.advancedportals.core.registry;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Allows a portal to register a tag and add a handler. If a plugin wants to
 * add functionality to someone elses tag then they should use the events.
 *
 * @author sekwah41
 */
public class TagRegistry {
    @Inject
    AdvancedPortalsCore portalsCore;

    private final List<String> literalTags = new CopyOnWriteArrayList<>();

    private final List<Tag> tags = new CopyOnWriteArrayList<>();

    private final Map<String, Tag> tagMap = new ConcurrentHashMap<>();
    private final Map<String, Tag.Activation> activationTags =
        new ConcurrentHashMap<>();
    private final Map<String, Tag.Creation> creationTags =
        new ConcurrentHashMap<>();
    private final Map<String, Tag.TagStatus> statusTags =
        new ConcurrentHashMap<>();

    @Inject
    private AdvancedPortalsCore pluginCore;

    /**
     * Portals to trigger when a portal is activated
     *
     * @param arg
     * @return
     */
    public Tag.Activation getActivationHandler(String arg,
                                               Tag.TagType targetType) {
        if (arg == null) {
            return null;
        }
        Tag.Activation tag = this.activationTags.get(arg);
        if (tag != null
            && Arrays.asList(tag.getTagTypes()).contains(targetType)) {
            return tag;
        }
        return null;
    }

    /**
     *
     * @param arg
     * @return
     */
    public Tag.Creation getCreationHandler(String arg) {
        return arg == null ? null : this.creationTags.get(arg);
    }

    /**
     *
     * @param arg
     * @return
     */
    public Tag.TagStatus getStatusHandler(String arg) {
        return arg == null ? null : this.statusTags.get(arg);
    }

    /**
     * File must extend
     * @return if the tag has been registered or if it already exists.
     */
    public synchronized boolean registerTag(Tag tag) {
        pluginCore.getModule().getInjector().injectMembers(tag);

        String tagName = tag.getName();
        if (tagName == null) {
            this.portalsCore.getInfoLogger().warning("A tag cannot be null.");
            return false;
        }

        // Check literal tags for clashes
        if (this.literalTags.contains(tagName)) {
            this.portalsCore.getInfoLogger().warning(
                "A tag with the name " + tagName + " already exists.");
            return false;
        }

        String[] aliases = tag.getAliases();
        if (aliases != null) {
            for (String alias : aliases) {
                if (this.literalTags.contains(alias)) {
                    this.portalsCore.getInfoLogger().warning(
                        "A tag with the alias " + alias + " already exists.");
                    return false;
                }
            }
        }

        this.tags.add(tag);
        this.literalTags.add(tagName);
        if (aliases != null) {
            Collections.addAll(this.literalTags, aliases);
        }

        this.tagMap.put(tagName, tag);

        if (tag instanceof Tag.Activation) {
            Tag.Activation tagActivation = (Tag.Activation) tag;
            this.activationTags.put(tagName, tagActivation);
        }
        if (tag instanceof Tag.TagStatus) {
            Tag.TagStatus tagStatus = (Tag.TagStatus) tag;
            this.statusTags.put(tagName, tagStatus);
        }
        if (tag instanceof Tag.Creation) {
            Tag.Creation tagCreation = (Tag.Creation) tag;
            this.creationTags.put(tagName, tagCreation);
        }
        return true;
    }

    public Tag getTag(String tagName) {
        return tagName == null ? null : this.tagMap.get(tagName);
    }

    public List<Tag> getTags() {
        return new ArrayList<>(this.tags);
    }
}
