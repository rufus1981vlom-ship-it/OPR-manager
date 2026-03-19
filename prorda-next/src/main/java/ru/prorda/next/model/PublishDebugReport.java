package ru.prorda.next.model;

public record PublishDebugReport(
        PublishResult result,
        boolean promptBuilt,
        boolean dataThresholdBypass,
        boolean openAiRequestSent,
        String openAiResponseStatus,
        int finalTextLength
) {}
