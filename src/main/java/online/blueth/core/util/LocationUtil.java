package online.blueth.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Location serialization, safety checks, and distance utilities.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * String s = LocationUtil.serialize(location);           // "world,100,64,200,90.0,0.0"
 * Location loc = LocationUtil.deserialize("world,100,64,200"); // Location
 * boolean safe = LocationUtil.isSafe(location);          // true if safe to stand
 * Location safeLoc = LocationUtil.findSafeNearby(loc, 5); // spiral search
 * double dist = LocationUtil.distance2D(a, b);           // flat XZ distance
 * boolean in = LocationUtil.isWithin(loc, center, 10);   // sphere check
 * }</pre>
 */
public final class LocationUtil {

    private LocationUtil() {}

    // ── Serialization ─────────────────────────────────────────────────────────

    /**
     * Serializes a {@link Location} to a comma-separated string:
     * {@code "world,x,y,z,yaw,pitch"}.
     */
    public static String serialize(Location location) {
        return location.getWorld().getName()
                + "," + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ()
                + "," + location.getYaw()
                + "," + location.getPitch();
    }

    /**
     * Deserializes a location from a comma-separated string.
     * Accepts both {@code "world,x,y,z"} and {@code "world,x,y,z,yaw,pitch"} forms.
     *
     * @param input the serialized string
     * @return the deserialized {@link Location}, or {@code null} if the world is not loaded
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Location deserialize(String input) {
        String[] parts = input.split(",");
        if (parts.length < 4) throw new IllegalArgumentException("Invalid location format: " + input);
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
        float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    // ── Safety checks ─────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a player can safely stand at this location:
     * solid block below, air (or passable) at feet and head level.
     */
    public static boolean isSafe(Location location) {
        Block below = location.clone().add(0, -1, 0).getBlock();
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        return below.getType().isSolid()
                && isPassable(feet)
                && isPassable(head)
                && feet.getType() != Material.LAVA
                && below.getType() != Material.LAVA;
    }

    /**
     * Searches for a safe location within {@code radius} blocks of {@code center}
     * using a spiral outward pattern. Returns {@code null} if no safe spot is found.
     *
     * @param center the center location
     * @param radius the maximum search radius
     */
    public static Location findSafeNearby(Location center, int radius) {
        if (isSafe(center)) return center;
        for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) continue;
                    for (int y = -r; y <= r; y++) {
                        Location test = center.clone().add(x, y, z);
                        if (isSafe(test)) return test.add(0.5, 0, 0.5);
                    }
                }
            }
        }
        return null;
    }

    // ── Distance ──────────────────────────────────────────────────────────────

    /**
     * Returns the 2D (XZ-plane) distance between two locations, ignoring Y.
     * Both locations must be in the same world.
     */
    public static double distance2D(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Returns {@code true} if {@code location} is within {@code radius} blocks
     * of {@code center} (3D sphere check). Both must be in the same world.
     */
    public static boolean isWithin(Location location, Location center, double radius) {
        if (!location.getWorld().equals(center.getWorld())) return false;
        return location.distanceSquared(center) <= radius * radius;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static boolean isPassable(Block block) {
        return block.isPassable() || block.getType().isAir();
    }
}
