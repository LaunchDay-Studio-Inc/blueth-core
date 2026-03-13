package online.blueth.core.update;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Async version checker that fetches a remote version string and compares it
 * against the current plugin version using numeric semver comparison.
 *
 * <p>The remote endpoint must return either a plain-text version string (e.g.
 * {@code "1.2.3"}) or any JSON/text body containing a {@code major.minor.patch}
 * triplet — the first such triplet found is used.
 *
 * <h3>Modrinth example</h3>
 * <pre>{@code
 * new UpdateChecker(plugin, "https://api.modrinth.com/v2/project/MY_ID/version?limit=1")
 *     .check()
 *     .thenAccept(result -> {
 *         if (result.updateAvailable()) {
 *             plugin.getLogger().info("Update available: " + result.latestVersion());
 *         }
 *     });
 * }</pre>
 */
public class UpdateChecker {

    private static final Pattern SEMVER = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");

    private final JavaPlugin plugin;
    private final String currentVersion;
    private final String checkUrl;

    public UpdateChecker(JavaPlugin plugin, String checkUrl) {
        this.plugin         = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.checkUrl       = checkUrl;
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    /**
     * Performs the version check asynchronously on a virtual thread.
     *
     * @return a future completing with an {@link UpdateResult}
     */
    public CompletableFuture<UpdateResult> check() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = fetch(checkUrl);
                String latest   = extractVersion(response);
                if (latest == null) {
                    return new UpdateResult(false, currentVersion, currentVersion, "Could not parse version from response");
                }
                boolean updateAvailable = isNewer(latest, currentVersion);
                return new UpdateResult(updateAvailable, latest, currentVersion, null);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "UpdateChecker failed: " + e.getMessage());
                return new UpdateResult(false, currentVersion, currentVersion, e.getMessage());
            }
        });
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String fetch(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Blueth-Core-UpdateChecker/" + currentVersion);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(10_000);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Extracts the first {@code major.minor.patch} string from {@code response}.
     * Returns {@code null} if no such string is found.
     */
    static String extractVersion(String response) {
        if (response == null || response.isBlank()) return null;
        Matcher m = SEMVER.matcher(response);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Returns {@code true} if {@code candidate} is strictly newer than {@code current}
     * using numeric component-by-component comparison (major → minor → patch).
     */
    static boolean isNewer(String candidate, String current) {
        int[] c   = parseSemver(candidate);
        int[] cur = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (c[i] != cur[i]) return c[i] > cur[i];
        }
        return false;
    }

    private static int[] parseSemver(String version) {
        String[] parts = version.split("\\.", 3);
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    // ── Result ────────────────────────────────────────────────────────────────

    /**
     * The result of a version check.
     *
     * @param updateAvailable {@code true} if {@code latestVersion} is newer than {@code currentVersion}
     * @param latestVersion   the version string retrieved from the remote endpoint
     * @param currentVersion  the version string from the plugin's plugin.yml
     * @param errorMessage    non-null if an error prevented the check from completing
     */
    public record UpdateResult(
            boolean updateAvailable,
            String latestVersion,
            String currentVersion,
            String errorMessage
    ) {}
}
