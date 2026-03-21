package ru.prorda.next.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.prorda.next.config.ConfigService;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class VkClient {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public VkClient(JavaPlugin plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
    }

    public CompletableFuture<String> postToWallAsGroup(long ownerId, String text) {
        return postToWall(ownerId, text, true);
    }

    public CompletableFuture<String> postToWallAsUser(long ownerId, String text) {
        return postToWall(ownerId, text, false);
    }

    private CompletableFuture<String> postToWall(long ownerId, String text, boolean fromGroup) {
        String token = config.vkToken();
        if (token.isBlank()) return CompletableFuture.failedFuture(new IllegalStateException("missing_vk_token"));
        String body = "owner_id=" + ownerId +
                "&message=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                "&from_group=" + (fromGroup ? "1" : "0") +
                "&access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8) +
                "&v=" + URLEncoder.encode(config.vkApiVersion(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.vk.com/method/wall.post"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        plugin.getLogger().info("[VK] wall.post ownerId=" + ownerId + ", from_group=" + (fromGroup ? "1" : "0") + ", textLength=" + text.length());
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    plugin.getLogger().info("[VK] response=" + r.body());
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (root.has("error")) {
                        throw new IllegalStateException("vk_error: " + root.getAsJsonObject("error").toString());
                    }
                    return r.body();
                });
    }
}
