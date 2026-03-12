package online.blueth.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reusable inventory GUI builder with click handlers.
 */
public class GuiBuilder implements InventoryHolder, Listener {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    public GuiBuilder(Component title, int rows) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public GuiBuilder setItem(int slot, ItemStack item, Consumer<Player> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            actions.put(slot, onClick);
        }
        return this;
    }

    public GuiBuilder setItem(int slot, ItemStack item) {
        return setItem(slot, item, null);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            Consumer<Player> action = actions.get(event.getRawSlot());
            if (action != null) {
                action.accept(player);
            }
        }
    }
}
