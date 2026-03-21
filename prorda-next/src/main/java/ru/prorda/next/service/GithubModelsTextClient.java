package ru.prorda.next.service;

import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.prorda.next.config.ConfigService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GithubModelsTextClient implements LlmClient {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Gson gson = new Gson();

    public GithubModelsTextClient(JavaPlugin plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public CompletableFuture<String> generatePostAsync(String prompt) {
        return tryGenerate(prompt, Math.max(0, config.githubModelsRetries()));
    }

    private CompletableFuture<String> tryGenerate(String prompt, int retriesLeft) {
        HttpRequest req = buildRequest(prompt);
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenCompose(resp -> {
                    if (resp.statusCode() >= 300) {
                        if (retriesLeft > 0) {
                            plugin.getLogger().warning("[AI:github-models] retry due to HTTP " + resp.statusCode());
                            return tryGenerate(prompt, retriesLeft - 1);
                        }
                        return CompletableFuture.failedFuture(toProviderException(resp.statusCode(), resp.body()));
                    }
                    try {
                        String text = parseContent(resp.body());
                        if (text == null || text.isBlank()) {
                            if (retriesLeft > 0) return tryGenerate(prompt, retriesLeft - 1);
                            return CompletableFuture.failedFuture(new IllegalStateException("empty_model_response"));
                        }
                        return CompletableFuture.completedFuture(text);
                    } catch (Exception ex) {
                        if (retriesLeft > 0) return tryGenerate(prompt, retriesLeft - 1);
                        return CompletableFuture.failedFuture(ex);
                    }
                })
                .exceptionallyCompose(ex -> {
                    if (retriesLeft > 0) {
                        plugin.getLogger().warning("[AI:github-models] retry due to exception: " + ex.getMessage());
                        return tryGenerate(prompt, retriesLeft - 1);
                    }
                    return CompletableFuture.failedFuture(ex);
                });
    }

    private HttpRequest buildRequest(String prompt) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model());
        JsonArray messages = new JsonArray();
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        m.addProperty("content", prompt);
        messages.add(m);
        payload.add("messages", messages);

        return HttpRequest.newBuilder(URI.create(baseUrl()))
                .header("Authorization", "Bearer " + config.githubModelsToken())
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", config.githubModelsApiVersion())
                .timeout(Duration.ofMillis(config.githubModelsTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
    }

    private String parseContent(String raw) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return "";
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.getAsJsonObject("message");
        if (message == null || !message.has("content")) return "";
        return message.get("content").getAsString();
    }

    private AiProviderException toProviderException(int status, String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject err = root.has("error") ? root.getAsJsonObject("error") : new JsonObject();
            String code = err.has("code") ? err.get("code").getAsString() : "unknown_error";
            String message = err.has("message") ? err.get("message").getAsString() : ("HTTP " + status);
            return new AiProviderException(provider(), status, code, message, body);
        } catch (Exception ignored) {
            return new AiProviderException(provider(), status, "unknown_error", "failed_to_parse_error", body);
        }
    }

    @Override
    public String provider() {
        return "github-models";
    }

    @Override
    public String baseUrl() {
        return config.githubModelsEndpoint();
    }

    @Override
    public String model() {
        return config.githubModelsModel();
    }
}
