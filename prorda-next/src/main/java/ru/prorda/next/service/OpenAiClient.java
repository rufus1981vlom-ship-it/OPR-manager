package ru.prorda.next.service;

import com.google.gson.*;
import ru.prorda.next.config.ConfigService;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class OpenAiClient {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Gson gson = new Gson();

    public OpenAiClient(JavaPlugin plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
    }

    public CompletableFuture<String> generatePostAsync(String prompt) {
        String apiKey = config.openAiKey();
        if (apiKey.isBlank()) {
            plugin.getLogger().warning("[OpenAI] API key is missing (config auto-pr.openai.api-key and OPENAI_API_KEY are empty)");
            return CompletableFuture.failedFuture(new IllegalStateException("missing_openai_key"));
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.responsesModel());
        payload.addProperty("input", prompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(40))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    plugin.getLogger().info("[OpenAI] response_code=" + response.statusCode());
                    plugin.getLogger().info("[OpenAI] raw=" + response.body());
                    if (response.statusCode() >= 300) throw new IllegalStateException("openai_http_" + response.statusCode());
                    String extracted = extractText(response.body());
                    plugin.getLogger().info("[OpenAI] extracted=" + extracted);
                    return extracted;
                });
    }

    private String extractText(String raw) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        if (root.has("output_text")) return root.get("output_text").getAsString();
        if (root.has("output")) {
            JsonArray output = root.getAsJsonArray("output");
            for (JsonElement item : output) {
                JsonObject obj = item.getAsJsonObject();
                if (!obj.has("content")) continue;
                for (JsonElement content : obj.getAsJsonArray("content")) {
                    JsonObject c = content.getAsJsonObject();
                    if (c.has("text")) return c.get("text").getAsString();
                }
            }
        }
        return "";
    }
}
