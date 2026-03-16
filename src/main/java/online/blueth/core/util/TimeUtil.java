package online.blueth.core.util;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time parsing and formatting utilities.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Duration d = TimeUtil.parse("2h30m");          // 2 hours 30 minutes
 * String full = TimeUtil.format(d);               // "2 hours 30 minutes"
 * String compact = TimeUtil.formatShort(d);       // "2h 30m"
 * String relative = TimeUtil.formatRelative(past); // "3 hours ago"
 * }</pre>
 */
public final class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String[]> UNIT_NAMES = new LinkedHashMap<>();

    static {
        UNIT_NAMES.put("d", new String[]{"day", "days"});
        UNIT_NAMES.put("h", new String[]{"hour", "hours"});
        UNIT_NAMES.put("m", new String[]{"minute", "minutes"});
        UNIT_NAMES.put("s", new String[]{"second", "seconds"});
    }

    private TimeUtil() {}

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses a human-friendly duration string such as {@code "2h30m"}, {@code "1d12h"},
     * or {@code "90s"}. Supported units: {@code d} (days), {@code h} (hours),
     * {@code m} (minutes), {@code s} (seconds).
     *
     * @param input the duration string to parse
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException if no valid duration components are found
     */
    public static Duration parse(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input);
        long totalSeconds = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            totalSeconds += switch (matcher.group(2).toLowerCase()) {
                case "d" -> value * 86_400;
                case "h" -> value * 3_600;
                case "m" -> value * 60;
                case "s" -> value;
                default  -> 0;
            };
        }
        if (!found) throw new IllegalArgumentException("Invalid duration: " + input);
        return Duration.ofSeconds(totalSeconds);
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    /**
     * Formats a {@link Duration} into a long human-readable string.
     * Example: {@code "2 hours 30 minutes"}.
     */
    public static String format(Duration duration) {
        return formatInternal(duration, false);
    }

    /**
     * Formats a {@link Duration} into a short compact string.
     * Example: {@code "2h 30m"}.
     */
    public static String formatShort(Duration duration) {
        return formatInternal(duration, true);
    }

    /**
     * Formats an {@link Instant} relative to now.
     * Example: {@code "3 hours ago"} or {@code "in 5 minutes"}.
     */
    public static String formatRelative(Instant instant) {
        Duration diff = Duration.between(instant, Instant.now());
        boolean past = !diff.isNegative();
        Duration abs = diff.abs();
        String formatted = format(abs);
        if (formatted.isEmpty()) return "just now";
        return past ? formatted + " ago" : "in " + formatted;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static String formatInternal(Duration duration, boolean shortForm) {
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        long[] values = {days, hours, minutes, seconds};
        String[] shortSuffixes = {"d", "h", "m", "s"};
        String[][] longNames = {
                {"day", "days"}, {"hour", "hours"}, {"minute", "minutes"}, {"second", "seconds"}
        };

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            if (!sb.isEmpty()) sb.append(' ');
            if (shortForm) {
                sb.append(values[i]).append(shortSuffixes[i]);
            } else {
                sb.append(values[i]).append(' ').append(values[i] == 1 ? longNames[i][0] : longNames[i][1]);
            }
        }
        return sb.isEmpty() ? (shortForm ? "0s" : "0 seconds") : sb.toString();
    }
}
