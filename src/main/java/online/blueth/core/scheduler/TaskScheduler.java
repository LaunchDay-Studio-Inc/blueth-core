package online.blueth.core.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper/Folia-aware scheduler abstraction.
 * Currently Paper-only; Folia global/regional scheduling can be added later.
 */
public class TaskScheduler {

    private final JavaPlugin plugin;

    public TaskScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public void runTimer(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }
}
