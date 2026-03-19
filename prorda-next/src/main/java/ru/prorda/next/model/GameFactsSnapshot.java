package ru.prorda.next.model;

import java.util.List;

public record GameFactsSnapshot(
        int online,
        int peakSinceStart,
        int peakToday,
        long joins,
        long quits,
        long deaths,
        long pvpKills,
        List<String> recentEvents
) {}
