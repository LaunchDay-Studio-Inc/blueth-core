package online.blueth.core.promo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time Discord promotion shown to each player after a configurable delay.
 *
 * <p>Automatically registers its own listener and timer. Call
 * {@link #init(JavaPlugin, String, int)} from the main plugin's {@code onEnable}.
 *
 * <p>Players with the {@code blueth.promo.bypass} permission are skipped.
 * Shown players are tracked in memory only and reset on server restart.
 */
public final class BluethPromo implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final String discordUrl;
    private final long delayTicks;
    private final Set<UUID> shownPlayers = ConcurrentHashMap.newKeySet();

    private BluethPromo(JavaPlugin plugin, String discordUrl, int delayMinutes) {
        this.plugin = plugin;
        this.discordUrl = discordUrl;
        this.delayTicks = delayMinutes * 60L * 20L;
    }

    /**
     * Initialises the Blueth promo system.
     *
     * @param plugin       the owning plugin
     * @param discordUrl   the Discord invite URL to display
     * @param delayMinutes minutes after join before showing the promo
     */
    public static void init(JavaPlugin plugin, String discordUrl, int delayMinutes) {
        BluethPromo promo = new BluethPromo(plugin, discordUrl, delayMinutes);
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

    // ── Internal ──────────────────────────────────────────────────────────────

    private void schedulePromo(Player player) {
        UUID uuid = player.getUniqueId();
        if (shownPlayers.contains(uuid)) return;
        if (player.hasPermission("blueth.promo.bypass")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (shownPlayers.contains(uuid)) return;
            if (player.hasPermission("blueth.promo.bypass")) return;

            String promoMessage = """
                    <gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>
                    <gradient:gold:yellow><bold>Powered by Blueth</bold></gradient>
                    <gray>Need help or want to report a bug?</gray>
                    <aqua><click:open_url:'%s'><hover:show_text:'<green>Click to join!'><bold>\uD83D\uDC49 Join our Discord</bold></hover></click></aqua>
                    <gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>""".formatted(discordUrl);

            Component message = MM.deserialize(promoMessage);
            player.sendMessage(message);

            player.sendActionBar(MM.deserialize("<gradient:gold:yellow>Powered by Blueth</gradient>"));

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.0f);

            shownPlayers.add(uuid);
        }, delayTicks);
    }
}
