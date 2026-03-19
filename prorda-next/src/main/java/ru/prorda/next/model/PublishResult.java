package ru.prorda.next.model;

public record PublishResult(PublicationStatus status, String details, String text) {
    public boolean isPublished() { return status == PublicationStatus.PUBLISHED; }
}
