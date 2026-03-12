package online.blueth.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Safe YAML config wrapper with versioned migration support.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final String fileName;
    private final File file;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + fileName, e);
        }
    }

    public FileConfiguration get() {
        return config;
    }

    public int getConfigVersion() {
        return config.getInt("config-version", 1);
    }
}
