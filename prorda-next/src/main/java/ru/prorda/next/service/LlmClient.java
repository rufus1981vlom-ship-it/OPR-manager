package ru.prorda.next.service;

import java.util.concurrent.CompletableFuture;

public interface LlmClient {
    CompletableFuture<String> generatePostAsync(String prompt);
    CompletableFuture<String> generateImageUrlAsync(String prompt);
    String provider();
    String baseUrl();
    String model();
}
