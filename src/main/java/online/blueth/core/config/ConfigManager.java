package online.blueth.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Thread-safe YAML config wrapper with versioned migration support.
 *
 * <p>Note on thread safety: {@link #reload()} and {@link #save()} are synchronized
 * on this instance. {@link #get()} returns the current snapshot — callers should
 * not cache the returned object across async boundaries.
 *
 * <h3>Migration example</h3>
 * <pre>{@code
 * configManager.migrate(3, (cfg, fromVersion) -> {
 *     if (fromVersion == 1) cfg.set("new-key", cfg.getString("old-key"));
 *     if (fromVersion == 2) cfg.set("renamed", cfg.getString("old-name"));
 * });
 * }</pre>
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final String fileName;
    private final File file;
    private volatile FileConfiguration config;

    public ConfigManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
        reload();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public synchronized void reload() {
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + fileName, e);
        }
    }

    /** Returns the live {@link FileConfiguration}. Do not cache across threads. */
    public FileConfiguration get() {
        return config;
    }

    // ── Versioned migration ───────────────────────────────────────────────────

    /**
     * Migrates the config from its stored version up to {@code targetVersion},
     * invoking {@code migrator} once per version step.
     *
     * <p>The migrator receives {@code (config, fromVersion)} and should apply
     * whatever changes are needed to advance from {@code fromVersion} to
     * {@code fromVersion + 1}. After all steps {@code config-version} is written
     * and the file is saved.
     *
     * @param targetVersion the version this code expects
     * @param migrator      called with (config, fromVersion) for each step
     */
    public synchronized void migrate(int targetVersion, BiConsumer<FileConfiguration, Integer> migrator) {
        int current = getConfigVersion();
        if (current >= targetVersion) return;
        for (int v = current; v < targetVersion; v++) {
            migrator.accept(config, v);
        }
        config.set("config-version", targetVersion);
        save();
        plugin.getLogger().info("[" + fileName + "] Migrated config v" + current + " → v" + targetVersion);
    }

    public int getConfigVersion() {
        return config.getInt("config-version", 1);
    }

    // ── Typed getters with defaults ───────────────────────────────────────────

    public String getString(String path, String def) {
        String val = config.getString(path);
        return val != null ? val : def;
    }

    public int getInt(String path, int def) {
        return config.isInt(path) ? config.getInt(path) : def;
    }

    public double getDouble(String path, double def) {
        return (config.isDouble(path) || config.isInt(path)) ? config.getDouble(path) : def;
    }

    public boolean getBoolean(String path, boolean def) {
        return config.isBoolean(path) ? config.getBoolean(path) : def;
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    // ── Nested path helpers ───────────────────────────────────────────────────

    /**
     * Builds a dotted YAML path from segments, e.g. {@code path("foo","bar")} → {@code "foo.bar"}.
     */
    public static String path(String... segments) {
        return String.join(".", segments);
    }

    /**
     * Sets a value at {@code path} and immediately saves the file.
     * Prefer batching changes through {@link #get()} and calling {@link #save()} once.
     */
    public synchronized void set(String path, Object value) {
        config.set(path, value);
        save();
    }

    /** Returns true if the config contains an entry at {@code path}. */
    public boolean has(String path) {
        return config.contains(path);
    }
}
