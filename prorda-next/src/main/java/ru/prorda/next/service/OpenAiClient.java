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

public class OpenAiClient implements LlmClient {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Gson gson = new Gson();

    public OpenAiClient(JavaPlugin plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public CompletableFuture<String> generatePostAsync(String prompt) {
        String apiKey = config.aiApiKey();
        if (apiKey.isBlank()) {
            plugin.getLogger().warning("[AI] API key is missing (ai.api-key and OPENAI_API_KEY are empty)");
            return CompletableFuture.failedFuture(new IllegalStateException("missing_ai_key"));
        }

        String provider = provider();
        JsonObject payload = new JsonObject();
        String endpoint;
        if ("deepseek".equals(provider)) {
            endpoint = "/chat/completions";
            payload.addProperty("model", model());
            JsonArray messages = new JsonArray();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", prompt);
            messages.add(msg);
            payload.add("messages", messages);
        } else {
            endpoint = "/responses";
            payload.addProperty("model", model());
            payload.addProperty("input", prompt);
        }

        String url = normalizeBaseUrl(baseUrl()) + endpoint;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(40))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    plugin.getLogger().info("[AI] provider=" + provider + " code=" + response.statusCode());
                    plugin.getLogger().info("[AI] raw=" + response.body());
                    if (response.statusCode() >= 300) {
                        throw toProviderException(provider, response.statusCode(), response.body());
                    }
                    String extracted = extractText(provider, response.body());
                    plugin.getLogger().info("[AI] extracted=" + extracted);
                    return extracted;
                });
    }

    @Override
    public String provider() { return config.aiProvider(); }

    @Override
    public String baseUrl() { return config.aiBaseUrl(); }

    @Override
    public String model() { return config.aiModel(); }

    private String normalizeBaseUrl(String base) {
        String b = (base == null || base.isBlank()) ? ("deepseek".equals(provider()) ? "https://api.deepseek.com" : "https://api.openai.com/v1") : base;
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if ("openai".equals(provider()) && !b.endsWith("/v1")) b = b + "/v1";
        return b;
    }

    private AiProviderException toProviderException(String provider, int httpStatus, String raw) {
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonObject err = root.has("error") ? root.getAsJsonObject("error") : new JsonObject();
            String code = err.has("code") ? err.get("code").getAsString() : "unknown_error";
            String message = err.has("message") ? err.get("message").getAsString() : ("HTTP " + httpStatus);
            return new AiProviderException(provider, httpStatus, code, message, raw);
        } catch (Exception parseEx) {
            return new AiProviderException(provider, httpStatus, "unknown_error", "failed_to_parse_error", raw);
        }
    }

    private String extractText(String provider, String raw) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        if ("deepseek".equals(provider) && root.has("choices")) {
            JsonArray choices = root.getAsJsonArray("choices");
            if (!choices.isEmpty()) {
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (message != null && message.has("content")) return message.get("content").getAsString();
            }
            return "";
        }
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
