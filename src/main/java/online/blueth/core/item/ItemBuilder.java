package online.blueth.core.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent {@link ItemStack} builder.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
 *     .name(Component.text("Legendary Blade").color(NamedTextColor.AQUA))
 *     .lore(Component.text("A very sharp blade."))
 *     .enchant(Enchantment.SHARPNESS, 5)
 *     .unbreakable(true)
 *     .hideFlags(ItemFlag.HIDE_ENCHANTS)
 *     .build();
 * }</pre>
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final List<Component> lore = new ArrayList<>();

    private ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    /** Copies an existing {@link ItemStack} to use as a base. */
    public static ItemBuilder copy(ItemStack source) {
        ItemBuilder builder = new ItemBuilder(source.getType(), source.getAmount());
        ItemMeta sourceMeta = source.getItemMeta();
        if (sourceMeta != null) {
            builder.item.setItemMeta(sourceMeta.clone());
            List<Component> sourceLore = sourceMeta.lore();
            if (sourceLore != null) {
                builder.lore.addAll(sourceLore);
            }
        }
        return builder;
    }

    // ── Properties ────────────────────────────────────────────────────────────

    public ItemBuilder name(Component name) {
        editMeta(meta -> meta.displayName(name));
        return this;
    }

    public ItemBuilder name(String plainName) {
        return name(Component.text(plainName));
    }

    public ItemBuilder lore(List<Component> lines) {
        this.lore.clear();
        this.lore.addAll(lines);
        return this;
    }

    public ItemBuilder lore(Component... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder addLore(Component line) {
        this.lore.add(line);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        editMeta(meta -> meta.setUnbreakable(unbreakable));
        return this;
    }

    public ItemBuilder customModelData(int data) {
        editMeta(meta -> meta.setCustomModelData(data));
        return this;
    }

    public ItemBuilder hideFlags(ItemFlag... flags) {
        editMeta(meta -> meta.addItemFlags(flags));
        return this;
    }

    public ItemBuilder hideAllFlags() {
        return hideFlags(ItemFlag.values());
    }

    /** Applies an arbitrary mutation to the item's {@link ItemMeta}. */
    public ItemBuilder meta(Consumer<ItemMeta> consumer) {
        editMeta(consumer);
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /** Builds and returns a clone of the configured {@link ItemStack}. */
    public ItemStack build() {
        editMeta(meta -> {
            if (!lore.isEmpty()) meta.lore(List.copyOf(lore));
        });
        return item.clone();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void editMeta(Consumer<ItemMeta> consumer) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            item.setItemMeta(meta);
        }
    }
}
