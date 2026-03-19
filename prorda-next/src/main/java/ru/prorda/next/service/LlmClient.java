package ru.prorda.next.service;

import java.util.concurrent.CompletableFuture;

public interface LlmClient {
    CompletableFuture<String> generatePostAsync(String prompt);
    String provider();
    String baseUrl();
    String model();
}
