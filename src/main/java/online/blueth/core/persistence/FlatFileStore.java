package online.blueth.core.persistence;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Simple flatfile persistence for player/global data.
 * SQL backend can be added later.
 */
public class FlatFileStore {

    private final JavaPlugin plugin;
    private final File dataDir;

    public FlatFileStore(JavaPlugin plugin, String subDir) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), subDir);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    public YamlConfiguration load(String key) {
        File file = new File(dataDir, key + ".yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    public void save(String key, YamlConfiguration data) {
        File file = new File(dataDir, key + ".yml");
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data: " + key, e);
        }
    }

    public boolean exists(String key) {
        return new File(dataDir, key + ".yml").exists();
    }

    public void delete(String key) {
        File file = new File(dataDir, key + ".yml");
        if (file.exists()) file.delete();
    }
}
