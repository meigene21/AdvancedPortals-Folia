package com.sekwah.advancedportals.core.registry;

import com.google.inject.Inject;
import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.effect.WarpEffect;
import com.sekwah.advancedportals.core.util.InfoLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author sekwah41
 */
public class WarpEffectRegistry {
    private final Map<String, WarpEffect> warpEffects =
        new ConcurrentHashMap<>();

    @Inject
    private AdvancedPortalsCore portalsCore;

    @Inject
    private InfoLogger infoLogger;

    /**
     * Register a new warp effect.
     *
     * @param name
     * @param effect
     */
    public void registerEffect(String name, WarpEffect effect) {
        if (name == null) {
            this.infoLogger.warning("Effect name cannot be null");
            return;
        }
        String key = name.toLowerCase();
        if (this.warpEffects.putIfAbsent(key, effect) != null) {
            this.infoLogger.warning("Effect with the name: " + name
                                    + " already exists");
        }
    }

    public WarpEffect.Visual getVisualEffect(String name) {
        if (name == null) {
            return null;
        }
        WarpEffect effect = this.warpEffects.get(name.toLowerCase());
        if (effect != null) {
            if (effect instanceof WarpEffect.Visual) {
                return (WarpEffect.Visual) effect;
            } else {
                this.infoLogger.warning("Effect called " + name
                                        + " is not a visual effect");
                return null;
            }
        } else {
            this.infoLogger.warning("No effect called " + name
                                    + " is registered");
            return null;
        }
    }

    public WarpEffect.Sound getSoundEffect(String name) {
        if (name == null) {
            return null;
        }
        WarpEffect effect = this.warpEffects.get(name.toLowerCase());
        if (effect != null) {
            if (effect instanceof WarpEffect.Sound) {
                return (WarpEffect.Sound) effect;
            } else {
                this.infoLogger.warning("Effect called " + name
                                        + " is not a sound effect");
                return null;
            }
        } else {
            this.infoLogger.warning("No effect called " + name
                                    + " is registered");
            return null;
        }
    }
}
