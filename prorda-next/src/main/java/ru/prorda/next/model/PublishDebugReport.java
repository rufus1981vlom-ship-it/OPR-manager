package ru.prorda.next.model;

public record PublishDebugReport(
        PublishResult result,
        boolean promptBuilt,
        boolean dataThresholdBypass,
        boolean openAiRequestSent,
        String provider,
        String baseUrl,
        String model,
        String httpStatus,
        String errorBody,
        String openAiResponseStatus,
        int finalTextLength
) {}
