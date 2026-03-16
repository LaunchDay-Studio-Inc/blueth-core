package online.blueth.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reusable single-page inventory GUI builder with click handlers.
 *
 * <p>The first call to {@link #open(Player, JavaPlugin)} registers this instance
 * as a Bukkit event listener. Subsequent calls reuse the same registration.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * GuiBuilder.create("My Menu", 3)
 *     .border(glassPane)
 *     .setItem(13, myItem, player -> player.sendMessage("clicked!"))
 *     .onClose(player -> player.sendMessage("bye!"))
 *     .open(player, plugin);
 * }</pre>
 */
public class GuiBuilder implements InventoryHolder, Listener {

    private final Inventory inventory;
    private final int rows;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private Consumer<Player> closeAction;
    private boolean registered = false;

    public GuiBuilder(Component title, int rows) {
        this.rows = rows;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static GuiBuilder create(Component title, int rows) {
        return new GuiBuilder(title, rows);
    }

    public static GuiBuilder create(String title, int rows) {
        return new GuiBuilder(Component.text(title), rows);
    }

    // ── Item placement ────────────────────────────────────────────────────────

    public GuiBuilder setItem(int slot, ItemStack item, Consumer<Player> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) actions.put(slot, onClick);
        else actions.remove(slot);
        return this;
    }

    public GuiBuilder setItem(int slot, ItemStack item) {
        return setItem(slot, item, null);
    }

    /** Fills every empty slot with {@code item} (no click action). */
    public GuiBuilder fill(ItemStack item) {
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item);
            }
        }
        return this;
    }

    /**
     * Fills the outermost ring of slots with {@code item}.
     * Works for 1–6 row inventories.
     */
    public GuiBuilder border(ItemStack item) {
        int cols   = 9;
        int maxRow = rows - 1;
        for (int slot = 0; slot < rows * cols; slot++) {
            int row = slot / cols;
            int col = slot % cols;
            if (row == 0 || row == maxRow || col == 0 || col == cols - 1) {
                inventory.setItem(slot, item);
            }
        }
        return this;
    }

    /**
     * Fills slots according to a pattern string. Each character in the pattern
     * maps to an item via the provided mappings. The pattern is read left-to-right,
     * row-by-row (9 chars per row).
     *
     * <h3>Example</h3>
     * <pre>{@code
     * builder.pattern("XOXOXOXOX", 'X', fillerItem, 'O', actionItem);
     * }</pre>
     *
     * @param pattern  the slot layout (one char per slot, row-by-row)
     * @param mappings alternating char-ItemStack pairs
     */
    public GuiBuilder pattern(String pattern, Object... mappings) {
        Map<Character, ItemStack> map = new HashMap<>();
        for (int i = 0; i + 1 < mappings.length; i += 2) {
            if (mappings[i] instanceof Character c && mappings[i + 1] instanceof ItemStack item) {
                map.put(c, item);
            }
        }
        for (int i = 0; i < pattern.length() && i < inventory.getSize(); i++) {
            ItemStack item = map.get(pattern.charAt(i));
            if (item != null) inventory.setItem(i, item);
        }
        return this;
    }

    /** Clears all items and click actions from the inventory. */
    public GuiBuilder clear() {
        inventory.clear();
        actions.clear();
        return this;
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    /** Sets the callback invoked when a player closes this inventory. */
    public GuiBuilder onClose(Consumer<Player> action) {
        this.closeAction = action;
        return this;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Registers this instance as a Bukkit listener without opening the inventory.
     * Call this before the first {@link #open(Player)} if you need manual control.
     */
    public GuiBuilder register(JavaPlugin plugin) {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        return this;
    }

    /**
     * Opens the GUI for {@code player} and registers this instance as a Bukkit
     * listener on the first call.
     */
    public void open(Player player, JavaPlugin plugin) {
        register(plugin);
        player.openInventory(inventory);
    }

    /** Opens the GUI without registering events (caller must call {@link #register(JavaPlugin)} first). */
    public void open(Player player) {
        player.openInventory(inventory);
    }

    // ── InventoryHolder ───────────────────────────────────────────────────────

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            Consumer<Player> action = actions.get(event.getRawSlot());
            if (action != null) action.accept(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (closeAction != null && event.getPlayer() instanceof Player player) {
            closeAction.accept(player);
        }
    }
}
