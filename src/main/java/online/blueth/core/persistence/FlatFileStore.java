package online.blueth.core.persistence;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Thread-safe flatfile persistence using YAML files.
 *
 * <p>All file I/O is guarded by a per-instance {@link ReadWriteLock}: multiple
 * concurrent reads are allowed; writes are exclusive. Use
 * {@link #saveAsync(String, YamlConfiguration)} to avoid blocking the server thread.
 */
public class FlatFileStore {

    private final JavaPlugin plugin;
    private final File dataDir;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FlatFileStore(JavaPlugin plugin, String subDir) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), subDir);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    // ── Synchronous operations ────────────────────────────────────────────────

    public YamlConfiguration load(String key) {
        lock.readLock().lock();
        try {
            return YamlConfiguration.loadConfiguration(fileFor(key));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void save(String key, YamlConfiguration data) {
        lock.writeLock().lock();
        try {
            data.save(fileFor(key));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data: " + key, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── Async operations ──────────────────────────────────────────────────────

    /**
     * Saves {@code data} to disk asynchronously on a virtual thread.
     *
     * @return a future that completes when the save finishes (exceptionally on I/O error)
     */
    public CompletableFuture<Void> saveAsync(String key, YamlConfiguration data) {
        return CompletableFuture.runAsync(() -> save(key, data));
    }

    // ── Key discovery ─────────────────────────────────────────────────────────

    /**
     * Returns all stored keys (file names without the {@code .yml} extension).
     */
    public List<String> loadAll() {
        lock.readLock().lock();
        try {
            File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return Collections.emptyList();
            return Arrays.stream(files)
                    .map(f -> f.getName().replace(".yml", ""))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    // ── Existence & deletion ──────────────────────────────────────────────────

    public boolean exists(String key) {
        return fileFor(key).exists();
    }

    public void delete(String key) {
        lock.writeLock().lock();
        try {
            File f = fileFor(key);
            if (f.exists()) f.delete();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private File fileFor(String key) {
        return new File(dataDir, key + ".yml");
    }
}
