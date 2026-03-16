package online.blueth.core.util;

import org.bukkit.Bukkit;

/**
 * Server version detection utilities.
 *
 * <p>All checks are performed once and cached. Methods are safe to call from any thread.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * if (VersionUtil.isAtLeast(1, 21)) {
 *     // use 1.21+ API
 * }
 * if (VersionUtil.isFolia()) {
 *     // Folia-specific logic
 * }
 * }</pre>
 */
public final class VersionUtil {

    private static final int[] MC_VERSION = parseMinecraftVersion();
    private static final boolean IS_FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    private static final boolean IS_PAPER = classExists("com.destroystokyo.paper.PaperConfig")
            || classExists("io.papermc.paper.configuration.GlobalConfiguration");

    private VersionUtil() {}

    // ── Version queries ───────────────────────────────────────────────────────

    /**
     * Returns the Minecraft version as a string, e.g. {@code "1.21.4"}.
     */
    public static String getMinecraftVersion() {
        return Bukkit.getServer().getMinecraftVersion();
    }

    /**
     * Returns {@code true} if the server version is at least {@code major.minor}.
     *
     * @param major the major version (e.g. {@code 1})
     * @param minor the minor version (e.g. {@code 21})
     */
    public static boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    /**
     * Returns {@code true} if the server version is at least {@code major.minor.patch}.
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        if (MC_VERSION[0] != major) return MC_VERSION[0] > major;
        if (MC_VERSION[1] != minor) return MC_VERSION[1] > minor;
        return MC_VERSION[2] >= patch;
    }

    // ── Platform detection ────────────────────────────────────────────────────

    /** Returns {@code true} if running on Folia. */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /** Returns {@code true} if running on Paper (or a Paper fork like Folia/Purpur). */
    public static boolean isPaper() {
        return IS_PAPER;
    }

    /** Returns {@code true} if running on Spigot but <em>not</em> Paper. */
    public static boolean isSpigot() {
        return !IS_PAPER && classExists("org.spigotmc.SpigotConfig");
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static int[] parseMinecraftVersion() {
        try {
            String version = Bukkit.getServer().getMinecraftVersion();
            String[] parts = version.split("\\.", 3);
            int[] result = new int[3];
            for (int i = 0; i < parts.length && i < 3; i++) {
                result[i] = Integer.parseInt(parts[i]);
            }
            return result;
        } catch (Exception e) {
            return new int[]{1, 21, 0};
        }
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
