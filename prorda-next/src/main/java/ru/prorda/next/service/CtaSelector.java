package ru.prorda.next.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CtaSelector {
    private String last = "";

    public String pick(List<String> options, String fallback) {
        if (options == null || options.isEmpty()) return fallback;
        for (int i = 0; i < 5; i++) {
            String candidate = options.get(ThreadLocalRandom.current().nextInt(options.size()));
            if (!candidate.equals(last)) {
                last = candidate;
                return candidate;
            }
        }
        String candidate = options.get(0);
        last = candidate;
        return candidate;
    }
}
