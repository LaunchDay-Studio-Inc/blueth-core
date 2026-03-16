package online.blueth.core.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number formatting, parsing, and clamping utilities.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * NumberUtil.format(1234567.89)   // "1,234,567.89"
 * NumberUtil.compact(1500000)     // "1.5M"
 * NumberUtil.compact(2300)        // "2.3K"
 * NumberUtil.parseOr("abc", 0)   // 0
 * NumberUtil.clamp(15, 0, 10)    // 10
 * NumberUtil.isNumeric("42.5")   // true
 * }</pre>
 */
public final class NumberUtil {

    private static final DecimalFormat STANDARD_FORMAT =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private static final String[] COMPACT_SUFFIXES = {"", "K", "M", "B", "T"};

    private NumberUtil() {}

    // ── Formatting ────────────────────────────────────────────────────────────

    /**
     * Formats a number with comma grouping and up to 2 decimal places.
     * Example: {@code 1234567.89} → {@code "1,234,567.89"}.
     */
    public static String format(double value) {
        synchronized (STANDARD_FORMAT) {
            return STANDARD_FORMAT.format(value);
        }
    }

    /**
     * Formats a number in compact form with a suffix.
     * Examples: {@code 1500000} → {@code "1.5M"}, {@code 2300} → {@code "2.3K"}.
     */
    public static String compact(double value) {
        if (value < 0) return "-" + compact(-value);
        int index = 0;
        double v = value;
        while (v >= 1000 && index < COMPACT_SUFFIXES.length - 1) {
            v /= 1000;
            index++;
        }
        if (v == (long) v) {
            return String.format(Locale.US, "%d%s", (long) v, COMPACT_SUFFIXES[index]);
        }
        return String.format(Locale.US, "%.1f%s", v, COMPACT_SUFFIXES[index]);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses a string to an integer, returning {@code fallback} if parsing fails.
     */
    public static int parseOr(String input, int fallback) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    /**
     * Parses a string to a double, returning {@code fallback} if parsing fails.
     */
    public static double parseOr(String input, double fallback) {
        try {
            return Double.parseDouble(input.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    /**
     * Parses a string to a long, returning {@code fallback} if parsing fails.
     */
    public static long parseOr(String input, long fallback) {
        try {
            return Long.parseLong(input.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    // ── Checks ────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the string represents a valid number
     * (integer or decimal).
     */
    public static boolean isNumeric(String input) {
        if (input == null || input.isBlank()) return false;
        try {
            Double.parseDouble(input.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── Clamping ──────────────────────────────────────────────────────────────

    /** Clamps {@code value} between {@code min} and {@code max} (inclusive). */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps {@code value} between {@code min} and {@code max} (inclusive). */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps {@code value} between {@code min} and {@code max} (inclusive). */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
