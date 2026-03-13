package online.blueth.core.webhook;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discord webhook emitter with embed support and rate-limit awareness.
 *
 * <h3>Rate limiting</h3>
 * When Discord returns HTTP 429, the emitter reads the {@code Retry-After} header
 * and sleeps the virtual thread before retrying once.
 *
 * <h3>Thread safety</h3>
 * All sends execute on a configurable {@link Executor} (defaults to a virtual-thread
 * per-task executor on Java 21). The class itself is stateless after construction.
 */
public class WebhookEmitter {

    private static final int MAX_RETRIES = 1;

    private final String webhookUrl;
    private final Executor executor;
    private final Logger logger;

    public WebhookEmitter(String webhookUrl) {
        this(webhookUrl, Executors.newVirtualThreadPerTaskExecutor(), null);
    }

    public WebhookEmitter(String webhookUrl, Executor executor, Logger logger) {
        this.webhookUrl = webhookUrl;
        this.executor   = executor;
        this.logger     = logger;
    }

    // ── Configuration checks ──────────────────────────────────────────────────

    /** Returns true if a non-blank webhook URL was provided. */
    public boolean isConfigured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    // ── Plain message ─────────────────────────────────────────────────────────

    /** Sends a plain-text {@code content} message asynchronously. */
    public CompletableFuture<Void> send(String content) {
        if (!isConfigured()) return CompletableFuture.completedFuture(null);
        return sendJson("{\"content\":" + jsonString(content) + "}");
    }

    // ── Embeds ────────────────────────────────────────────────────────────────

    /** Sends an embed asynchronously. */
    public CompletableFuture<Void> sendEmbed(Embed embed) {
        if (!isConfigured()) return CompletableFuture.completedFuture(null);
        return sendJson("{\"embeds\":[" + embed.toJson() + "]}");
    }

    /** Sends plain content accompanied by an embed. */
    public CompletableFuture<Void> send(String content, Embed embed) {
        if (!isConfigured()) return CompletableFuture.completedFuture(null);
        return sendJson("{\"content\":" + jsonString(content) + ",\"embeds\":[" + embed.toJson() + "]}");
    }

    // ── Core send ─────────────────────────────────────────────────────────────

    private CompletableFuture<Void> sendJson(String json) {
        return CompletableFuture.runAsync(() -> postWithRetry(json, MAX_RETRIES), executor);
    }

    private void postWithRetry(String json, int retriesLeft) {
        try {
            HttpURLConnection conn = openConnection();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status == 429) {
                long retryAfterMs = parseRetryAfter(conn);
                conn.disconnect();
                if (retriesLeft > 0) {
                    Thread.sleep(retryAfterMs);
                    postWithRetry(json, retriesLeft - 1);
                } else {
                    log(Level.WARNING, "Webhook rate-limited; giving up after retry.");
                }
                return;
            }
            conn.disconnect();
        } catch (IOException e) {
            log(Level.WARNING, "Webhook send failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpURLConnection openConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Blueth-Core/1.0");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(10_000);
        return conn;
    }

    private long parseRetryAfter(HttpURLConnection conn) {
        try {
            String header = conn.getHeaderField("Retry-After");
            if (header != null) return (long) (Double.parseDouble(header) * 1000L);
        } catch (NumberFormatException ignored) {}
        return 1_000;
    }

    private void log(Level level, String message) {
        if (logger != null) logger.log(level, message);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r") + "\"";
    }

    // ── Embed ─────────────────────────────────────────────────────────────────

    /**
     * Represents a Discord embed object. Use {@link Embed#builder()} to construct one.
     */
    public static final class Embed {

        private final String title;
        private final String description;
        private final Integer color;
        private final String thumbnailUrl;
        private final String footerText;
        private final List<Field> fields;

        private Embed(Builder b) {
            this.title        = b.title;
            this.description  = b.description;
            this.color        = b.color;
            this.thumbnailUrl = b.thumbnailUrl;
            this.footerText   = b.footerText;
            this.fields       = List.copyOf(b.fields);
        }

        public static Builder builder() { return new Builder(); }

        String toJson() {
            StringBuilder sb = new StringBuilder("{");
            if (title != null)        append(sb, "title", title);
            if (description != null)  append(sb, "description", description);
            if (color != null)        sb.append("\"color\":").append(color).append(",");
            if (thumbnailUrl != null) sb.append("\"thumbnail\":{\"url\":").append(jsonString(thumbnailUrl)).append("},");
            if (footerText != null)   sb.append("\"footer\":{\"text\":").append(jsonString(footerText)).append("},");
            if (!fields.isEmpty()) {
                sb.append("\"fields\":[");
                for (int i = 0; i < fields.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(fields.get(i).toJson());
                }
                sb.append("],");
            }
            if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
            sb.append("}");
            return sb.toString();
        }

        private static void append(StringBuilder sb, String key, String value) {
            sb.append("\"").append(key).append("\":").append(jsonString(value)).append(",");
        }

        public static final class Builder {
            private String title, description, thumbnailUrl, footerText;
            private Integer color;
            private final List<Field> fields = new ArrayList<>();

            public Builder title(String title)             { this.title = title; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder color(int rgb)                  { this.color = rgb; return this; }
            public Builder thumbnail(String url)           { this.thumbnailUrl = url; return this; }
            public Builder footer(String text)             { this.footerText = text; return this; }
            public Builder field(String name, String value, boolean inline) {
                fields.add(new Field(name, value, inline));
                return this;
            }
            public Builder field(String name, String value) { return field(name, value, false); }
            public Embed build() { return new Embed(this); }
        }

        public record Field(String name, String value, boolean inline) {
            String toJson() {
                return "{\"name\":" + jsonString(name) + ",\"value\":" + jsonString(value)
                        + ",\"inline\":" + inline + "}";
            }
        }
    }
}
