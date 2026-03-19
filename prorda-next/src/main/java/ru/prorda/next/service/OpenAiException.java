package ru.prorda.next.service;

public class OpenAiException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;
    private final String errorMessage;
    private final String rawBody;

    public OpenAiException(int httpStatus, String errorCode, String errorMessage, String rawBody) {
        super("openai_http_" + httpStatus + ":" + errorCode + ":" + errorMessage);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.rawBody = rawBody;
    }

    public int httpStatus() { return httpStatus; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public String rawBody() { return rawBody; }
}
