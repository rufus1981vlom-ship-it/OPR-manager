package ru.prorda.next.service;

import java.util.concurrent.CompletableFuture;

public interface LlmClient {
    CompletableFuture<String> generatePostAsync(String prompt);
    default CompletableFuture<String> generateImageUrlAsync(String prompt) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("image_generation_not_supported"));
    }
    String provider();
    String baseUrl();
    String model();
}
