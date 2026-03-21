package ru.prorda.next.service;

import ru.prorda.next.storage.Storage;
import ru.prorda.next.util.TextNormalizer;

import java.util.List;

public class PostHistoryService {
    private final Storage storage;

    public PostHistoryService(Storage storage) {
        this.storage = storage;
    }

    public String hash(String text) {
        return TextNormalizer.sha256(TextNormalizer.normalizeCore(text));
    }

    public boolean wasRubricUsedRecently(String mode, String rubric, int limit) {
        return storage.recentHistory(mode, rubric, limit).stream().findAny().isPresent();
    }

    public boolean hasSimilarHashRecently(String mode, String hash, int limit) {
        List<String> hashes = storage.recentHashes(mode, limit);
        return hashes.contains(hash);
    }

    public void log(String mode, long target, String rubric, String hash, boolean success, String details) {
        storage.logSmmPrHistory(mode, target, rubric, hash, success ? "success" : "fail", details);
    }

    public String lastSummary() {
        return storage.lastHistorySummary();
    }
}
