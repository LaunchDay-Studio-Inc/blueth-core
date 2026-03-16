package online.blueth.core.promo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import online.blueth.core.scheduler.TaskScheduler;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * One-time Discord promotion shown to each player after 5 minutes of playtime.
 *
 * <p>Automatically registers its own listener and timer. Any plugin that
 * depends on Blueth Core gets this promotion by calling
 * {@link #enable(JavaPlugin, TaskScheduler)} in {@code onEnable}.
 *
 * <p>Configurable via {@code blueth.promo-enabled} in the plugin's
 * {@code config.yml} (default: {@code true}).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In your plugin's onEnable:
 * BluethPromo.enable(this, scheduler);
 * }</pre>
 */
public final class BluethPromo implements Listener {

    private static final long DELAY_TICKS = 6000L; // 5 minutes = 300 seconds = 6000 ticks
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final String PROMO_MESSAGE =
            """
            <gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>
            <gradient:gold:yellow><bold>Powered by Blueth</bold></gradient>
            <gray>Need help or want to report a bug?</gray>
            <aqua><click:open_url:'https://discord.gg/bJDGXc4DvW'><hover:show_text:'<green>Click to join!'><bold>\uD83D\uDC49 Join our Discord</bold></hover></click></aqua>
            <gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>""";

    private final JavaPlugin plugin;
    private final TaskScheduler scheduler;
    private final File dataFile;
    private final Set<UUID> shownPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Runnable> pendingTasks = new ConcurrentHashMap<>();

    private BluethPromo(JavaPlugin plugin, TaskScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.dataFile = new File(plugin.getDataFolder(), "blueth-promo.yml");
        loadShownPlayers();
    }

    /**
     * Enables the Blueth promo system. Call in your plugin's {@code onEnable}.
     * Respects {@code blueth.promo-enabled} config key (default: {@code true}).
     *
     * @param plugin    the owning plugin
     * @param scheduler the task scheduler
     */
    public static void enable(JavaPlugin plugin, TaskScheduler scheduler) {
        boolean enabled = plugin.getConfig().getBoolean("blueth.promo-enabled", true);
        if (!enabled) return;

        BluethPromo promo = new BluethPromo(plugin, scheduler);
        plugin.getServer().getPluginManager().registerEvents(promo, plugin);

        // Handle players already online (late enable / reload)
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            promo.schedulePromo(player);
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        schedulePromo(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Runnable cancel = pendingTasks.remove(uuid);
        if (cancel != null) cancel.run();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void schedulePromo(Player player) {
        UUID uuid = player.getUniqueId();
        if (shownPlayers.contains(uuid)) return;

        final boolean[] cancelled = {false};
        scheduler.runLater(() -> {
            if (cancelled[0]) return;
            if (!player.isOnline()) return;
            if (shownPlayers.contains(uuid)) return;

            // Send chat message
            Component message = MM.deserialize(PROMO_MESSAGE);
            player.sendMessage(message);

            // Send ActionBar
            player.sendActionBar(MM.deserialize("<gradient:gold:yellow>Powered by Blueth</gradient>"));

            // Play subtle sound
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.0f);

            // Record as shown
            shownPlayers.add(uuid);
            pendingTasks.remove(uuid);
            saveShownPlayersAsync();
        }, DELAY_TICKS);

        pendingTasks.put(uuid, () -> cancelled[0] = true);
    }

    private void loadShownPlayers() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : config.getStringList("shown")) {
            try {
                shownPlayers.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveShownPlayersAsync() {
        scheduler.runAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            config.set("shown", shownPlayers.stream().map(UUID::toString).toList());
            try {
                File tmp = new File(plugin.getDataFolder(), "blueth-promo.tmp");
                config.save(tmp);
                Files.move(tmp.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save promo data", e);
            }
        });
    }
}
