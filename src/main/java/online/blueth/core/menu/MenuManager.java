package online.blueth.core.menu;

import net.kyori.adventure.text.Component;
import online.blueth.core.gui.GuiBuilder;
import online.blueth.core.item.ItemBuilder;
import online.blueth.core.scheduler.TaskScheduler;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Centralized GUI lifecycle manager with exploit protection, confirmation
 * dialogs, sound effects, and animated menus.
 *
 * <p>Prevents common inventory exploit vectors including shift-click,
 * number-key swaps, drag events, and drops while a managed menu is open.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * MenuManager menus = new MenuManager(plugin, scheduler);
 *
 * GuiBuilder gui = GuiBuilder.create("Shop", 3);
 * menus.register("shop", gui);
 * menus.open(player, "shop");
 *
 * // Confirmation dialog
 * menus.confirm(player, "Delete this kit?",
 *     p -> deleteKit(p),
 *     p -> p.sendMessage("Cancelled"));
 * }</pre>
 */
public final class MenuManager implements Listener {

    private final JavaPlugin plugin;
    private final TaskScheduler scheduler;
    private final Map<String, GuiBuilder> menus = new ConcurrentHashMap<>();
    private final Map<UUID, String> openMenus = new ConcurrentHashMap<>();
    private Sound clickSound;
    private float clickVolume = 0.5f;
    private float clickPitch = 1.0f;

    public MenuManager(JavaPlugin plugin, TaskScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * Sets a sound effect to play on every menu click.
     *
     * @param sound  the sound to play (or {@code null} to disable)
     * @param volume volume (0.0 – 1.0)
     * @param pitch  pitch (0.5 – 2.0)
     */
    public void setClickSound(Sound sound, float volume, float pitch) {
        this.clickSound = sound;
        this.clickVolume = volume;
        this.clickPitch = pitch;
    }

    // ── Menu registration ─────────────────────────────────────────────────────

    /**
     * Registers a named menu.
     */
    public void register(String name, GuiBuilder gui) {
        menus.put(name, gui);
    }

    /**
     * Unregisters a named menu.
     */
    public void unregister(String name) {
        menus.remove(name);
    }

    /**
     * Opens a registered menu for the player.
     */
    public void open(Player player, String name) {
        GuiBuilder gui = menus.get(name);
        if (gui == null) return;
        openMenus.put(player.getUniqueId(), name);
        gui.open(player, plugin);
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────

    /**
     * Opens a confirmation dialog with Confirm / Cancel buttons.
     *
     * @param player    the player
     * @param question  the question to display as inventory title
     * @param onConfirm called when the player clicks "Confirm"
     * @param onCancel  called when the player clicks "Cancel"
     */
    public void confirm(Player player, String question, Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        final GuiBuilder[] guiHolder = new GuiBuilder[1];
        GuiBuilder gui = GuiBuilder.create(Component.text(question), 3)
                .fill(ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build())
                .setItem(11,
                        ItemBuilder.of(Material.LIME_WOOL)
                                .name(Component.text("Confirm").color(net.kyori.adventure.text.format.NamedTextColor.GREEN))
                                .build(),
                        p -> {
                            p.closeInventory();
                            onConfirm.accept(p);
                        })
                .setItem(15,
                        ItemBuilder.of(Material.RED_WOOL)
                                .name(Component.text("Cancel").color(net.kyori.adventure.text.format.NamedTextColor.RED))
                                .build(),
                        p -> {
                            p.closeInventory();
                            onCancel.accept(p);
                        })
                .onClose(p -> {
                    openMenus.remove(p.getUniqueId());
                });
        guiHolder[0] = gui;

        openMenus.put(player.getUniqueId(), "__confirm__");
        gui.open(player, plugin);
    }

    // ── Animated slots ────────────────────────────────────────────────────────

    /**
     * Starts an animated slot that updates its item every {@code periodTicks} ticks.
     * Returns a {@link Runnable} that stops the animation when called.
     *
     * @param gui         the GUI to animate
     * @param slot        the slot index
     * @param periodTicks ticks between updates
     * @param updater     provides the next item on each tick
     */
    public Runnable animateSlot(GuiBuilder gui, int slot, long periodTicks,
                                java.util.function.Supplier<org.bukkit.inventory.ItemStack> updater) {
        final boolean[] running = {true};
        scheduler.runTimer(() -> {
            if (!running[0]) return;
            gui.setItem(slot, updater.get());
        }, 0L, periodTicks);
        return () -> running[0] = false;
    }

    // ── Event handlers (exploit protection) ───────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiBuilder)) return;

        // Block all interactions with the bottom inventory while a managed GUI is open
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }

        // Play click sound
        if (clickSound != null) {
            player.playSound(player.getLocation(), clickSound, clickVolume, clickPitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiBuilder)) return;
        // Cancel drag events that affect the managed inventory
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }
}
