package online.blueth.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Full i18n message system with MiniMessage support, placeholder substitution,
 * and reload-safe caching.
 *
 * <p>Messages are loaded from a YAML file (default: {@code messages.yml}).
 * Defaults are bundled in the plugin jar and user overrides are read from the
 * data folder. Both {@code %placeholder%} and {@code <tag>} styles are supported.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * MessageManager messages = new MessageManager(plugin, "messages.yml");
 * messages.setPrefix("<gray>[<gold>MyPlugin</gold>]</gray> ");
 *
 * // Get a Component
 * Component msg = messages.getMessage("welcome", "player", player.getName());
 *
 * // Send directly
 * messages.sendMessage(player, "welcome", "player", player.getName());
 *
 * // Title and ActionBar
 * messages.sendTitle(player, "level-up", "level", "10");
 * messages.sendActionBar(player, "xp-gained", "amount", "50");
 * }</pre>
 */
public final class MessageManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final String fileName;
    private final File file;
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();
    private volatile String prefix = "";
    private volatile YamlConfiguration config;

    public MessageManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
        reload();
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * Sets a MiniMessage prefix that is auto-prepended to all messages
     * sent via {@link #sendMessage(Player, String, String...)}.
     */
    public void setPrefix(String miniMessagePrefix) {
        this.prefix = miniMessagePrefix;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Reloads messages from disk. Defaults from the jar resource are loaded
     * first, then user overrides are merged on top.
     */
    public void reload() {
        messageCache.clear();

        // Load defaults from jar
        YamlConfiguration defaults = new YamlConfiguration();
        InputStream resource = plugin.getResource(fileName);
        if (resource != null) {
            defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
        }

        // Save default file if not present
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        config = YamlConfiguration.loadConfiguration(file);
        config.setDefaults(defaults);

        // Cache all messages
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                messageCache.put(key, config.getString(key));
            }
        }
    }

    // ── Raw access ────────────────────────────────────────────────────────────

    /**
     * Returns the raw MiniMessage template for a message key, or the key itself
     * if no message is found.
     */
    public String getRaw(String key) {
        return messageCache.getOrDefault(key, key);
    }

    // ── Component building ────────────────────────────────────────────────────

    /**
     * Builds a {@link Component} from a message key with optional tag replacements.
     * Placeholders are provided as alternating key-value pairs:
     * {@code getMessage("key", "player", "Steve", "amount", "5")}.
     *
     * <p>Both {@code <tag>} (MiniMessage) and {@code %tag%} styles are replaced.
     *
     * @param key          the message key in the YAML file
     * @param replacements alternating key-value pairs
     */
    public Component getMessage(String key, String... replacements) {
        String raw = getRaw(key);
        return parseWithReplacements(raw, replacements);
    }

    /**
     * Builds a prefixed {@link Component} from a message key.
     */
    public Component getPrefixedMessage(String key, String... replacements) {
        String raw = prefix + getRaw(key);
        return parseWithReplacements(raw, replacements);
    }

    // ── Sending helpers ───────────────────────────────────────────────────────

    /**
     * Sends a prefixed chat message to the player.
     *
     * @param player       the recipient
     * @param key          the message key
     * @param replacements alternating key-value pairs
     */
    public void sendMessage(Player player, String key, String... replacements) {
        player.sendMessage(getPrefixedMessage(key, replacements));
    }

    /**
     * Sends a title to the player. The message key value is used as the main title.
     *
     * @param player       the recipient
     * @param key          the message key for the title text
     * @param replacements alternating key-value pairs
     */
    public void sendTitle(Player player, String key, String... replacements) {
        Component title = getMessage(key, replacements);
        player.showTitle(Title.title(title, Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
    }

    /**
     * Sends a title with a subtitle to the player.
     *
     * @param player       the recipient
     * @param titleKey     the message key for the title text
     * @param subtitleKey  the message key for the subtitle text
     * @param replacements alternating key-value pairs (applied to both)
     */
    public void sendTitle(Player player, String titleKey, String subtitleKey, String... replacements) {
        Component title = getMessage(titleKey, replacements);
        Component subtitle = getMessage(subtitleKey, replacements);
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
    }

    /**
     * Sends an ActionBar message to the player.
     *
     * @param player       the recipient
     * @param key          the message key
     * @param replacements alternating key-value pairs
     */
    public void sendActionBar(Player player, String key, String... replacements) {
        player.sendActionBar(getMessage(key, replacements));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Component parseWithReplacements(String raw, String... replacements) {
        // Apply %placeholder% style first
        String processed = raw;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            processed = processed.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }

        // Build MiniMessage tag resolvers for <tag> style
        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            resolver.resolver(Placeholder.parsed(replacements[i], replacements[i + 1]));
        }

        return MM.deserialize(processed, resolver.build());
    }
}
