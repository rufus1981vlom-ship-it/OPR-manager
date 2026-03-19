package ru.prorda.next.model;

public enum PublicationStatus {
    PUBLISHED("published"),
    OPENAI_FAILED("openai_failed"),
    EMPTY_OPENAI_RESPONSE("empty_openai_response"),
    FILTERED_TOO_SHORT("filtered_too_short"),
    DUPLICATE_TEXT("duplicate_text"),
    VK_PUBLISH_FAILED("vk_publish_failed"),
    SKIPPED_NO_TARGETS("skipped_no_targets"),
    SKIPPED_NO_OWNER_ID("skipped_no_owner_id"),
    SKIPPED_DAILY_LIMIT("skipped_daily_limit"),
    SKIPPED_RUBRIC_DISABLED("skipped_rubric_disabled"),
    SKIPPED_NOT_ENOUGH_DATA("skipped_not_enough_data"),
    SKIPPED_TOPIC_RECENTLY_USED("skipped_topic_recently_used"),
    DB_RECOVERED("db_recovered");

    private final String code;

    PublicationStatus(String code) { this.code = code; }

    public String code() { return code; }
}
