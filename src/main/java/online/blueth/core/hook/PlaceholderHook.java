package online.blueth.core.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PlaceholderAPI integration helper. Extend this class to create a custom
 * PAPI expansion for your plugin.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public class MyExpansion extends PlaceholderHook {
 *     public MyExpansion(JavaPlugin plugin) { super(plugin); }
 *
 *     {@literal @}Override
 *     public @NotNull String getIdentifier() { return "myplugin"; }
 *
 *     {@literal @}Override
 *     public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
 *         return switch (params) {
 *             case "name" -> plugin.getName();
 *             default     -> null;
 *         };
 *     }
 * }
 *
 * // In onEnable:
 * new MyExpansion(this).tryRegister();
 * }</pre>
 */
public abstract class PlaceholderHook extends PlaceholderExpansion {

    protected final JavaPlugin plugin;

    protected PlaceholderHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── PlaceholderExpansion contract ─────────────────────────────────────────

    @Override
    public @NotNull String getAuthor() {
        List<String> authors = plugin.getPluginMeta().getAuthors();
        return authors.isEmpty() ? "Unknown" : String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * Subclasses must return the placeholder identifier, e.g. {@code "myplugin"}.
     * Placeholders will be accessed as {@code %myplugin_<params>%}.
     */
    @Override
    public abstract @NotNull String getIdentifier();

    /**
     * Returns {@code true} so this expansion survives {@code /papi reload}.
     */
    @Override
    public boolean persist() {
        return true;
    }

    // ── Availability ──────────────────────────────────────────────────────────

    /** Returns {@code true} if PlaceholderAPI is installed on this server. */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers this expansion with PlaceholderAPI if it is available.
     *
     * @return {@code true} if registration succeeded; {@code false} if PAPI is absent
     */
    public boolean tryRegister() {
        if (!isAvailable()) return false;
        return register();
    }

    /**
     * Attempts to unregister this expansion from PlaceholderAPI.
     */
    public void tryUnregister() {
        if (isRegistered()) {
            unregister();
        }
    }
}
