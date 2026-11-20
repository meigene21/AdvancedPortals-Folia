package com.sekwah.advancedportals.core.util;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * For all delayed and repeating tasks.
 * Thread-safe for Folia where tasks may be queued from any region thread while
 * {@link #tick()} executes on the global region scheduler.
 */
@Singleton
public final class GameScheduler {
    private final Queue<DelayedGameTickEvent> newTickEvents =
        new ConcurrentLinkedQueue<>();
    private final List<DelayedGameTickEvent> delayedTickEvents =
        new ArrayList<>();

    public synchronized void tick() {
        DelayedGameTickEvent pending;
        while ((pending = this.newTickEvents.poll()) != null) {
            this.delayedTickEvents.add(pending);
        }

        Iterator<DelayedGameTickEvent> tickEventIterator =
            this.delayedTickEvents.iterator();
        while (tickEventIterator.hasNext()) {
            DelayedGameTickEvent event = tickEventIterator.next();
            event.tick();
            if (event.shouldRun()) {
                try {
                    event.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                if (!(event instanceof DelayedGameIntervalEvent)) {
                    tickEventIterator.remove();
                }
            }
        }
    }

    public void delayedTickEvent(String name, Runnable consumer,
                                 int tickDelay) {
        this.newTickEvents.add(
            new DelayedGameTickEvent(name, consumer, tickDelay));
    }

    public void intervalTickEvent(String name, Runnable consumer, int tickDelay,
                                  int interval) {
        this.newTickEvents.add(
            new DelayedGameIntervalEvent(name, consumer, tickDelay, interval));
    }

    public synchronized void clearAllEvents() {
        this.newTickEvents.clear();
        this.delayedTickEvents.clear();
    }

    public static class DelayedGameTickEvent {
        // So we can find it later and remove it if needed
        public final String name;
        public final Runnable consumer;
        public int ticks;

        public DelayedGameTickEvent(String name, Runnable consumer, int ticks) {
            this.name = name;
            this.consumer = consumer;
            this.ticks = ticks;
        }

        public void tick() {
            this.ticks--;
        }

        public boolean shouldRun() {
            return this.ticks <= 0;
        }

        public void run() {
            this.consumer.run();
        }
    }

    public static class DelayedGameIntervalEvent extends DelayedGameTickEvent {
        public final int interval;

        public DelayedGameIntervalEvent(String name, Runnable consumer,
                                        int ticks, int interval) {
            super(name, consumer, ticks);
            this.interval = interval;
        }

        @Override
        public void run() {
            this.ticks = interval;
            super.run();
        }
    }
}
