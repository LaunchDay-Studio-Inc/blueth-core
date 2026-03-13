package online.blueth.core.cooldown;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * UUID-based cooldown tracker with configurable durations.
 *
 * <p>Cooldowns are stored in memory only. Call {@link #cleanup()} periodically
 * (e.g. every 5 minutes via {@link online.blueth.core.scheduler.TaskScheduler})
 * to remove expired entries and prevent unbounded memory growth.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * CooldownManager cd = new CooldownManager();
 * cd.set(player.getUniqueId(), "kit", 5, TimeUnit.MINUTES);
 *
 * if (cd.isActive(player.getUniqueId(), "kit")) {
 *     long secs = cd.remainingSeconds(player.getUniqueId(), "kit");
 *     player.sendMessage("Wait " + secs + "s before using kit again!");
 * }
 * }</pre>
 */
public class CooldownManager {

    /** uuid → (cooldown key → expiry epoch millis) */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // ── Setting cooldowns ─────────────────────────────────────────────────────

    /**
     * Sets a cooldown for the given player and key.
     *
     * @param uuid     the player's UUID
     * @param key      a descriptive cooldown key, e.g. {@code "kit"} or {@code "teleport"}
     * @param duration how long the cooldown lasts
     * @param unit     the time unit for {@code duration}
     */
    public void set(UUID uuid, String key, long duration, TimeUnit unit) {
        cooldowns.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>())
                 .put(key, System.currentTimeMillis() + unit.toMillis(duration));
    }

    /** Convenience overload accepting milliseconds directly. */
    public void setMillis(UUID uuid, String key, long millis) {
        set(uuid, key, millis, TimeUnit.MILLISECONDS);
    }

    // ── Querying ──────────────────────────────────────────────────────────────

    /** Returns {@code true} if the cooldown exists and has not yet expired. */
    public boolean isActive(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return false;
        Long expiry = map.get(key);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /** Returns the remaining time in milliseconds, or {@code 0} if not active. */
    public long remainingMillis(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return 0;
        Long expiry = map.get(key);
        if (expiry == null) return 0;
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    /** Returns the remaining time in whole seconds, or {@code 0} if not active. */
    public long remainingSeconds(UUID uuid, String key) {
        return TimeUnit.MILLISECONDS.toSeconds(remainingMillis(uuid, key));
    }

    // ── Clearing ──────────────────────────────────────────────────────────────

    /** Removes the specified cooldown regardless of expiry. */
    public void clear(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map != null) map.remove(key);
    }

    /** Removes all cooldowns for the given player. */
    public void clearAll(UUID uuid) {
        cooldowns.remove(uuid);
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    /**
     * Removes all expired cooldown entries. Schedule this to run periodically
     * to prevent unbounded memory growth.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        cooldowns.forEach((uuid, map) -> map.entrySet().removeIf(e -> e.getValue() <= now));
        cooldowns.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
