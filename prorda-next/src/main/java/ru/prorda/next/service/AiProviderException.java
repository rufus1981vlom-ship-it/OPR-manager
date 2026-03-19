package ru.prorda.next.service;

public class AiProviderException extends RuntimeException {
    private final String provider;
    private final int httpStatus;
    private final String errorCode;
    private final String errorMessage;
    private final String errorBody;

    public AiProviderException(String provider, int httpStatus, String errorCode, String errorMessage, String errorBody) {
        super(provider + "_http_" + httpStatus + ":" + errorCode + ":" + errorMessage);
        this.provider = provider;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorBody = errorBody;
    }

    public String provider() { return provider; }
    public int httpStatus() { return httpStatus; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public String errorBody() { return errorBody; }
}
