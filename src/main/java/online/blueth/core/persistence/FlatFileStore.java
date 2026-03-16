package online.blueth.core.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Thread-safe flatfile persistence with YAML and JSON backends.
 *
 * <p>All file I/O is guarded by a per-instance {@link ReadWriteLock}: multiple
 * concurrent reads are allowed; writes are exclusive. Uses atomic writes
 * (write to {@code .tmp} then rename) to prevent corruption.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // YAML backend (default)
 * FlatFileStore store = new FlatFileStore(plugin, "data");
 *
 * // JSON backend
 * FlatFileStore jsonStore = new FlatFileStore(plugin, "data", Backend.JSON);
 *
 * YamlConfiguration data = store.load("player123");
 * data.set("coins", 500);
 * store.saveAsync("player123", data);
 *
 * // Load all entries
 * Map<String, YamlConfiguration> all = store.loadAllEntries();
 * }</pre>
 */
public class FlatFileStore {

    /** File format backend. */
    public enum Backend { YAML, JSON }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JavaPlugin plugin;
    private final File dataDir;
    private final Backend backend;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FlatFileStore(JavaPlugin plugin, String subDir) {
        this(plugin, subDir, Backend.YAML);
    }

    public FlatFileStore(JavaPlugin plugin, String subDir, Backend backend) {
        this.plugin = plugin;
        this.backend = backend;
        this.dataDir = new File(plugin.getDataFolder(), subDir);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    // ── Synchronous operations ────────────────────────────────────────────────

    /**
     * Loads data for the given key. Returns an empty config if the file does not exist.
     */
    public YamlConfiguration load(String key) {
        lock.readLock().lock();
        try {
            File f = fileFor(key);
            if (!f.exists()) return new YamlConfiguration();
            if (backend == Backend.JSON) return loadJson(f);
            return YamlConfiguration.loadConfiguration(f);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Saves data atomically: writes to a {@code .tmp} file then renames.
     */
    public void save(String key, YamlConfiguration data) {
        lock.writeLock().lock();
        try {
            File target = fileFor(key);
            File tmp = new File(dataDir, key + ".tmp");
            if (backend == Backend.JSON) {
                saveJson(tmp, data);
            } else {
                data.save(tmp);
            }
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data: " + key, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── Async operations ──────────────────────────────────────────────────────

    /**
     * Saves data asynchronously on a virtual thread with atomic file writes.
     *
     * @return a future that completes when the save finishes
     */
    public CompletableFuture<Void> saveAsync(String key, YamlConfiguration data) {
        return CompletableFuture.runAsync(() -> save(key, data));
    }

    /**
     * Loads data asynchronously on a virtual thread.
     *
     * @return a future completing with the loaded config
     */
    public CompletableFuture<YamlConfiguration> loadAsync(String key) {
        return CompletableFuture.supplyAsync(() -> load(key));
    }

    // ── Key discovery ─────────────────────────────────────────────────────────

    /**
     * Returns all stored keys (file names without the extension).
     */
    public List<String> loadAll() {
        lock.readLock().lock();
        try {
            String ext = fileExtension();
            File[] files = dataDir.listFiles((dir, name) -> name.endsWith(ext));
            if (files == null) return Collections.emptyList();
            return Arrays.stream(files)
                    .map(f -> f.getName().replace(ext, ""))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all stored entries as a map of key → loaded configuration.
     */
    public Map<String, YamlConfiguration> loadAllEntries() {
        List<String> keys = loadAll();
        Map<String, YamlConfiguration> entries = new HashMap<>();
        for (String key : keys) {
            entries.put(key, load(key));
        }
        return entries;
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

    // ── JSON backend helpers ──────────────────────────────────────────────────

    private YamlConfiguration loadJson(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, Object> map = GSON.fromJson(reader,
                    new TypeToken<Map<String, Object>>() {}.getType());
            if (map != null) map.forEach(config::set);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load JSON: " + file.getName(), e);
        }
        return config;
    }

    private void saveJson(File file, YamlConfiguration data) throws IOException {
        Map<String, Object> map = new HashMap<>();
        for (String key : data.getKeys(true)) {
            if (!data.isConfigurationSection(key)) {
                map.put(key, data.get(key));
            }
        }
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(map, writer);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private File fileFor(String key) {
        return new File(dataDir, key + fileExtension());
    }

    private String fileExtension() {
        return backend == Backend.JSON ? ".json" : ".yml";
    }
}
