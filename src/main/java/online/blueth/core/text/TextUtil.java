package online.blueth.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/**
 * MiniMessage helper utilities for text parsing, stripping, and formatting.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // Parse MiniMessage
 * Component title = TextUtil.parse("<gold><bold>Welcome!</bold></gold>");
 *
 * // Named tag substitution
 * Component msg = TextUtil.parse("<red>Hello, <player>!</red>",
 *         Map.of("player", player.getName()));
 *
 * // %key% placeholder replacement
 * Component line = TextUtil.format("<green>Balance: %amount%g",
 *         Map.of("amount", String.valueOf(balance)));
 *
 * // Strip all tags
 * String plain = TextUtil.stripTags("<red>Hello</red>"); // → "Hello"
 * }</pre>
 */
public final class TextUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    private TextUtil() {}

    // ── Parsing ───────────────────────────────────────────────────────────────

    /** Parses a MiniMessage string into an Adventure {@link Component}. */
    public static Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /**
     * Parses a MiniMessage string, replacing {@code <key>} tags with the
     * values from {@code replacements}.
     *
     * @param miniMessage  the input string (may contain {@code <key>} tags)
     * @param replacements map of tag name → replacement string value
     */
    public static Component parse(String miniMessage, Map<String, String> replacements) {
        TagResolver.Builder resolver = TagResolver.builder();
        replacements.forEach((key, val) -> resolver.resolver(Placeholder.parsed(key, val)));
        return MM.deserialize(miniMessage, resolver.build());
    }

    /**
     * Replaces {@code %key%} style placeholders in a raw string, then parses
     * the result as MiniMessage.
     *
     * @param template     the template string, e.g. {@code "<red>Hello, %player%!</red>"}
     * @param replacements map of placeholder key (without {@code %}) → value
     */
    public static Component format(String template, Map<String, String> replacements) {
        String result = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return MM.deserialize(result);
    }

    // ── Stripping ─────────────────────────────────────────────────────────────

    /** Strips all MiniMessage tags from {@code input}, returning plain text. */
    public static String stripTags(String input) {
        return MM.stripTags(input);
    }

    // ── Legacy color codes ────────────────────────────────────────────────────

    /**
     * Converts a legacy ampersand-formatted string (e.g. {@code "&aHello"})
     * to an Adventure {@link Component}.
     */
    public static Component colorize(String legacyText) {
        return LEGACY_AMPERSAND.deserialize(legacyText);
    }

    /**
     * Converts a {@link Component} to a legacy {@code §}-prefixed string.
     * Prefer keeping Adventure {@link Component} throughout; only use this for
     * APIs that don't yet accept {@link Component}.
     */
    public static String toLegacy(Component component) {
        return LEGACY_SECTION.serialize(component);
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    /** Serializes a {@link Component} back to its MiniMessage representation. */
    public static String serialize(Component component) {
        return MM.serialize(component);
    }
}
