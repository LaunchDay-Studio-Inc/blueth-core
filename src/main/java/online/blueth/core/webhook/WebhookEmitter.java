package online.blueth.core.webhook;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Simple Discord webhook emitter.
 */
public class WebhookEmitter {

    private final String webhookUrl;

    public WebhookEmitter(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public CompletableFuture<Void> send(String content) {
        return CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String json = "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (IOException ignored) {
            }
        });
    }
}
