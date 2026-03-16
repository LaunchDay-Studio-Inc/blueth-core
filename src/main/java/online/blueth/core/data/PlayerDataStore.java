package online.blueth.core.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

/**
 * Modern player data abstraction with async I/O, in-memory caching,
 * dirty-flag batch saves, and atomic file writes.
 *
 * <p>Data is stored per-player in individual YAML files. Reads serve from
 * an in-memory cache; writes mark entries dirty and are flushed periodically
 * or on demand.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PlayerDataStore store = new PlayerDataStore(plugin, "playerdata");
 *
 * // Set typed data
 * store.set(uuid, "kills", 42);
 * store.set(uuid, "rank", "Diamond");
 *
 * // Get typed data
 * int kills = store.get(uuid, "kills", Integer.class);
 * String rank = store.get(uuid, "rank", String.class);
 *
 * // Bulk query
 * Map<UUID, Integer> allKills = store.getAll("kills", Integer.class);
 *
 * // Flush dirty data to disk
 * store.saveAll();
 * }</pre>
 */
public final class PlayerDataStore {

    private final JavaPlugin plugin;
    private final File dataDir;
    private final Map<UUID, YamlConfiguration> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final ReadWriteLock ioLock = new ReentrantReadWriteLock();

    /**
     * @param plugin the owning plugin
     * @param subDir subdirectory under the plugin data folder
     */
    public PlayerDataStore(JavaPlugin plugin, String subDir) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), subDir);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    // ── Typed access ──────────────────────────────────────────────────────────

    /**
     * Retrieves a typed value from a player's data store.
     *
     * @param uuid the player UUID
     * @param key  the data key
     * @param type the expected type
     * @return the value, or {@code null} if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T get(UUID uuid, String key, Class<T> type) {
        YamlConfiguration data = getOrLoad(uuid);
        Object raw = data.get(key);
        if (raw == null) return null;
        if (type.isInstance(raw)) return (T) raw;
        // Handle numeric conversions
        if (raw instanceof Number number) {
            if (type == Integer.class || type == int.class) return (T) Integer.valueOf(number.intValue());
            if (type == Long.class || type == long.class) return (T) Long.valueOf(number.longValue());
            if (type == Double.class || type == double.class) return (T) Double.valueOf(number.doubleValue());
            if (type == Float.class || type == float.class) return (T) Float.valueOf(number.floatValue());
        }
        if (type == String.class) return (T) raw.toString();
        return null;
    }

    /**
     * Retrieves a typed value with a default fallback.
     */
    public <T> T get(UUID uuid, String key, Class<T> type, T defaultValue) {
        T result = get(uuid, key, type);
        return result != null ? result : defaultValue;
    }

    /**
     * Sets a value in the player's data and marks it dirty for batch saving.
     */
    public void set(UUID uuid, String key, Object value) {
        YamlConfiguration data = getOrLoad(uuid);
        data.set(key, value);
        dirty.add(uuid);
    }

    /**
     * Removes a key from the player's data.
     */
    public void remove(UUID uuid, String key) {
        YamlConfiguration data = getOrLoad(uuid);
        data.set(key, null);
        dirty.add(uuid);
    }

    /**
     * Returns {@code true} if the player's data contains the given key.
     */
    public boolean has(UUID uuid, String key) {
        return getOrLoad(uuid).contains(key);
    }

    // ── Bulk queries ──────────────────────────────────────────────────────────

    /**
     * Returns a map of UUID → value for every player file that contains {@code key}.
     */
    public <T> Map<UUID, T> getAll(String key, Class<T> type) {
        Map<UUID, T> result = new HashMap<>();
        // Include cached entries
        for (Map.Entry<UUID, YamlConfiguration> entry : cache.entrySet()) {
            T val = get(entry.getKey(), key, type);
            if (val != null) result.put(entry.getKey(), val);
        }
        // Scan files for uncached entries
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace(".yml", "");
                try {
                    UUID uuid = UUID.fromString(name);
                    if (!cache.containsKey(uuid)) {
                        T val = get(uuid, key, type);
                        if (val != null) result.put(uuid, val);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /**
     * Saves all dirty entries to disk synchronously using atomic writes.
     */
    public void saveAll() {
        for (UUID uuid : dirty) {
            saveSingle(uuid);
        }
        dirty.clear();
    }

    /**
     * Saves all dirty entries asynchronously.
     */
    public CompletableFuture<Void> saveAllAsync() {
        return CompletableFuture.runAsync(this::saveAll);
    }

    /**
     * Saves a single player's data to disk using atomic write (tmp → rename).
     */
    public void saveSingle(UUID uuid) {
        YamlConfiguration data = cache.get(uuid);
        if (data == null) return;
        ioLock.writeLock().lock();
        try {
            File target = fileFor(uuid);
            File tmp = new File(dataDir, uuid + ".tmp");
            data.save(tmp);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data: " + uuid, e);
        } finally {
            ioLock.writeLock().unlock();
        }
    }

    /**
     * Loads a player's data into the cache if not already loaded.
     * Call this in a {@code PlayerJoinEvent} handler to pre-warm the cache.
     */
    public void load(UUID uuid) {
        getOrLoad(uuid);
    }

    /**
     * Saves dirty data and removes a player from the in-memory cache.
     * Call in {@code PlayerQuitEvent} to free memory.
     *
     * @param uuid the player UUID
     */
    public void unload(UUID uuid) {
        if (dirty.remove(uuid)) {
            saveSingle(uuid);
        }
        cache.remove(uuid);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private YamlConfiguration getOrLoad(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            ioLock.readLock().lock();
            try {
                File f = fileFor(id);
                if (f.exists()) {
                    return YamlConfiguration.loadConfiguration(f);
                }
                return new YamlConfiguration();
            } finally {
                ioLock.readLock().unlock();
            }
        });
    }

    private File fileFor(UUID uuid) {
        return new File(dataDir, uuid + ".yml");
    }
}
