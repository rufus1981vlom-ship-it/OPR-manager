package ru.prorda.next.service;

import ru.prorda.next.model.GameFactsSnapshot;

public record PromptContext(
        String mode,
        String rubric,
        String reason,
        GameFactsSnapshot facts,
        String opening,
        String cta,
        String tone,
        String style,
        String targetHint
) {}
