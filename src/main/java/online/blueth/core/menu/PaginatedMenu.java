package online.blueth.core.menu;

import net.kyori.adventure.text.Component;
import online.blueth.core.gui.GuiBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Higher-level paginated inventory menu built on {@link GuiBuilder}.
 *
 * <p>Items are added with {@link #addItem(ItemStack, Consumer)}. The menu
 * automatically splits items across pages and renders prev/next navigation buttons.
 *
 * <h3>Layout</h3>
 * <pre>
 * Rows 0..(rows-2)  → content slots (left-to-right, top-to-bottom)
 * Last row          → [prev] at col 0 · [close] at col 4 · [next] at col 8
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PaginatedMenu menu = new PaginatedMenu(plugin, Component.text("Shop"), 4)
 *     .prevButton(prevItem)
 *     .nextButton(nextItem)
 *     .closeButton(closeItem);
 *
 * for (ShopEntry e : entries) {
 *     menu.addItem(e.icon(), p -> p.sendMessage("Purchased " + e.name()));
 * }
 *
 * menu.open(player);
 * }</pre>
 */
public class PaginatedMenu {

    private final JavaPlugin plugin;
    private final Component title;
    private final int rows;
    /** Number of content slots per page (all rows except the nav row). */
    private final int contentSlots;

    private final List<MenuItem> items = new ArrayList<>();
    private int currentPage = 0;

    private ItemStack prevButton;
    private ItemStack nextButton;
    private ItemStack closeButton;
    private Consumer<Player> closeAction;

    private GuiBuilder gui;
    private boolean initialized = false;

    /**
     * @param plugin the owning plugin (used for listener registration)
     * @param title  the inventory title displayed to players
     * @param rows   total row count; must be ≥ 2 (last row reserved for navigation)
     */
    public PaginatedMenu(JavaPlugin plugin, Component title, int rows) {
        if (rows < 2) throw new IllegalArgumentException("PaginatedMenu requires at least 2 rows");
        this.plugin       = plugin;
        this.title        = title;
        this.rows         = rows;
        this.contentSlots = (rows - 1) * 9;
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    public PaginatedMenu prevButton(ItemStack item)  { this.prevButton  = item; return this; }
    public PaginatedMenu nextButton(ItemStack item)  { this.nextButton  = item; return this; }
    public PaginatedMenu closeButton(ItemStack item) { this.closeButton = item; return this; }

    public PaginatedMenu onClose(Consumer<Player> action) {
        this.closeAction = action;
        return this;
    }

    // ── Content ───────────────────────────────────────────────────────────────

    public PaginatedMenu addItem(ItemStack item, Consumer<Player> onClick) {
        items.add(new MenuItem(item, onClick));
        return this;
    }

    public PaginatedMenu addItem(ItemStack item) {
        return addItem(item, null);
    }

    /** Clears all items from all pages. */
    public PaginatedMenu clearItems() {
        items.clear();
        currentPage = 0;
        return this;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) items.size() / contentSlots));
    }

    public int getCurrentPage() {
        return currentPage;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Opens the paginated menu for {@code player}.
     * Registers the internal {@link GuiBuilder} as a Bukkit listener on the first call.
     */
    public void open(Player player) {
        if (!initialized) {
            gui = new GuiBuilder(title, rows);
            if (closeAction != null) gui.onClose(closeAction);
            gui.register(plugin);
            initialized = true;
        }
        renderPage(player);
        player.openInventory(gui.getInventory());
    }

    private void renderPage(Player player) {
        gui.clear();

        int start = currentPage * contentSlots;
        int end   = Math.min(start + contentSlots, items.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            MenuItem mi = items.get(i);
            gui.setItem(slot, mi.item(), mi.onClick());
        }

        int navOffset = (rows - 1) * 9;

        if (currentPage > 0 && prevButton != null) {
            gui.setItem(navOffset, prevButton, p -> {
                currentPage--;
                renderPage(p);
                p.openInventory(gui.getInventory());
            });
        }

        if (closeButton != null) {
            gui.setItem(navOffset + 4, closeButton, Player::closeInventory);
        }

        if (currentPage < getTotalPages() - 1 && nextButton != null) {
            gui.setItem(navOffset + 8, nextButton, p -> {
                currentPage++;
                renderPage(p);
                p.openInventory(gui.getInventory());
            });
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private record MenuItem(ItemStack item, Consumer<Player> onClick) {}
}
