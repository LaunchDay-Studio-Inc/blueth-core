package online.blueth.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Lightweight internal event bus for inter-module communication.
 *
 * <p>Listeners are ordered by {@link Priority} and invoked synchronously by
 * default. Use {@link #onAsync(Class, Consumer)} for async dispatch.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * EventBus bus = new EventBus();
 * bus.on(ContractCompleteEvent.class, event -> {
 *     player.sendMessage("Contract done: " + event.getContract());
 * });
 * bus.fire(new ContractCompleteEvent(player, "delivery"));
 * }</pre>
 */
public final class EventBus {

    private final Map<Class<?>, List<RegisteredListener<?>>> listeners = new ConcurrentHashMap<>();
    private final Executor asyncExecutor;

    /**
     * Creates an event bus using virtual threads for async listeners.
     */
    public EventBus() {
        this(Runnable::run);
    }

    /**
     * Creates an event bus with a custom async executor.
     */
    public EventBus(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers a listener for events of type {@code eventClass} at {@link Priority#NORMAL}.
     *
     * @return a {@link Subscription} that can be used to unregister
     */
    public <T extends BluethEvent> Subscription on(Class<T> eventClass, Consumer<T> listener) {
        return on(eventClass, Priority.NORMAL, false, false, listener);
    }

    /**
     * Registers a listener with a specific priority.
     */
    public <T extends BluethEvent> Subscription on(Class<T> eventClass, Priority priority, Consumer<T> listener) {
        return on(eventClass, priority, false, false, listener);
    }

    /**
     * Registers an async listener at {@link Priority#NORMAL}.
     */
    public <T extends BluethEvent> Subscription onAsync(Class<T> eventClass, Consumer<T> listener) {
        return on(eventClass, Priority.NORMAL, true, false, listener);
    }

    /**
     * Registers a one-shot listener that unregisters itself after the first invocation.
     */
    public <T extends BluethEvent> Subscription once(Class<T> eventClass, Consumer<T> listener) {
        return on(eventClass, Priority.NORMAL, false, true, listener);
    }

    @SuppressWarnings("unchecked")
    private <T extends BluethEvent> Subscription on(
            Class<T> eventClass, Priority priority, boolean async, boolean once, Consumer<T> listener
    ) {
        RegisteredListener<T> reg = new RegisteredListener<>((Class<T>) eventClass, listener, priority, async, once);
        List<RegisteredListener<?>> list =
                listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>());
        list.add(reg);
        list.sort((a, b) -> Integer.compare(a.priority.ordinal(), b.priority.ordinal()));
        return () -> list.remove(reg);
    }

    // ── Firing ────────────────────────────────────────────────────────────────

    /**
     * Fires an event, invoking all registered listeners in priority order.
     * Cancelled events still reach all listeners (listeners should check
     * {@link BluethEvent#isCancelled()} if they care).
     *
     * @param event the event to fire
     * @return the event (for chaining / inspection)
     */
    @SuppressWarnings("unchecked")
    public <T extends BluethEvent> T fire(T event) {
        List<RegisteredListener<?>> list = listeners.get(event.getClass());
        if (list == null) return event;

        for (RegisteredListener<?> raw : list) {
            RegisteredListener<T> reg = (RegisteredListener<T>) raw;
            if (reg.async) {
                asyncExecutor.execute(() -> reg.listener.accept(event));
            } else {
                reg.listener.accept(event);
            }
            if (reg.once) {
                list.remove(reg);
            }
        }
        return event;
    }

    /**
     * Removes all listeners for the specified event class.
     */
    public void clearListeners(Class<? extends BluethEvent> eventClass) {
        listeners.remove(eventClass);
    }

    /**
     * Removes all registered listeners.
     */
    public void clearAll() {
        listeners.clear();
    }

    // ── Priority ──────────────────────────────────────────────────────────────

    /** Listener priority — lower ordinal fires first. */
    public enum Priority {
        LOW, NORMAL, HIGH
    }

    /** Handle returned from {@link #on} methods. Call {@link #unregister()} to remove. */
    @FunctionalInterface
    public interface Subscription {
        /** Unregisters this listener. */
        void unregister();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private record RegisteredListener<T extends BluethEvent>(
            Class<T> eventClass,
            Consumer<T> listener,
            Priority priority,
            boolean async,
            boolean once
    ) {}
}
