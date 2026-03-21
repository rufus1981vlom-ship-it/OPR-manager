package ru.prorda.next.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RubricSelector {
    private String lastSmm = "";
    private String lastPr = "";

    public String pick(String mode, List<String> rubrics, String fallback) {
        if (rubrics == null || rubrics.isEmpty()) return fallback;
        String last = "pr".equals(mode) ? lastPr : lastSmm;
        for (int i = 0; i < 8; i++) {
            String candidate = rubrics.get(ThreadLocalRandom.current().nextInt(rubrics.size()));
            if (!candidate.equals(last)) {
                if ("pr".equals(mode)) lastPr = candidate; else lastSmm = candidate;
                return candidate;
            }
        }
        String candidate = rubrics.get(0);
        if ("pr".equals(mode)) lastPr = candidate; else lastSmm = candidate;
        return candidate;
    }
}
