package ru.prorda.next.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ToneSelector {
    public String pick(List<String> options, String fallback) {
        if (options == null || options.isEmpty()) return fallback;
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
