package online.blueth.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Paper/Folia-aware scheduler abstraction.
 *
 * <p>Folia is detected at runtime by checking for
 * {@code io.papermc.paper.threadedregions.RegionizedServer}. When running on Folia,
 * scheduling is delegated to the appropriate region/async schedulers. On standard
 * Paper the legacy {@link org.bukkit.scheduler.BukkitScheduler} is used with an
 * automatic fallback.
 *
 * <p>All tasks scheduled through this instance are tracked and can be cancelled
 * at once with {@link #cancelAll()}.
 */
public class TaskScheduler {

    /** {@code true} when the server is running Folia (threaded-region). */
    public static final boolean FOLIA = detectFolia();

    private final JavaPlugin plugin;
    /** Cancel callbacks for every task registered through this instance. */
    private final List<Runnable> cancelCallbacks = new CopyOnWriteArrayList<>();

    public TaskScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ── Core scheduling ───────────────────────────────────────────────────────

    /** Runs {@code task} on the main thread (global region on Folia). */
    public void runSync(Runnable task) {
        if (FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
            trackScheduled(st);
        } else {
            trackBukkit(Bukkit.getScheduler().runTask(plugin, task));
        }
    }

    /** Runs {@code task} asynchronously. */
    public void runAsync(Runnable task) {
        if (FOLIA) {
            ScheduledTask st = Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
            trackScheduled(st);
        } else {
            trackBukkit(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        }
    }

    /** Runs {@code task} after {@code delayTicks} ticks (50 ms per tick). */
    public void runLater(Runnable task, long delayTicks) {
        if (FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler()
                    .runDelayed(plugin, t -> task.run(), delayTicks);
            trackScheduled(st);
        } else {
            trackBukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
        }
    }

    /** Runs {@code task} asynchronously after {@code delayTicks} ticks. */
    public void runLaterAsync(Runnable task, long delayTicks) {
        if (FOLIA) {
            ScheduledTask st = Bukkit.getAsyncScheduler()
                    .runDelayed(plugin, t -> task.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
            trackScheduled(st);
        } else {
            trackBukkit(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
        }
    }

    /**
     * Runs {@code task} on the main thread repeatedly.
     *
     * @param delayTicks  ticks before first execution
     * @param periodTicks ticks between subsequent executions
     */
    public void runTimer(Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
            trackScheduled(st);
        } else {
            trackBukkit(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
        }
    }

    /** Runs {@code task} asynchronously on a fixed schedule. */
    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            ScheduledTask st = Bukkit.getAsyncScheduler()
                    .runAtFixedRate(plugin, t -> task.run(),
                            delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
            trackScheduled(st);
        } else {
            trackBukkit(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
        }
    }

    // ── Folia-ready entity/location scheduling ────────────────────────────────

    /**
     * Runs {@code task} in the region that owns {@code entity}.
     * Falls back to the main thread on non-Folia servers.
     */
    public void runAtEntity(Entity entity, Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            runSync(task);
        }
    }

    /**
     * Runs {@code task} in the region that owns {@code location}.
     * Falls back to the main thread on non-Folia servers.
     */
    public void runAtLocation(Location location, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
        } else {
            runSync(task);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Cancels all tasks scheduled through this {@link TaskScheduler} instance.
     */
    public void cancelAll() {
        cancelCallbacks.forEach(Runnable::run);
        cancelCallbacks.clear();
    }

    // ── Internal tracking ─────────────────────────────────────────────────────

    private void trackBukkit(BukkitTask task) {
        cancelCallbacks.add(task::cancel);
    }

    private void trackScheduled(ScheduledTask task) {
        cancelCallbacks.add(task::cancel);
    }
}
