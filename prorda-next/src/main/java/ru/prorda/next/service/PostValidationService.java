package ru.prorda.next.service;

import ru.prorda.next.config.ConfigService;

import java.util.Locale;
import java.util.regex.Pattern;

public class PostValidationService {
    private final ConfigService config;
    private static final Pattern REPEATED_CHARS = Pattern.compile("(.)\\1{6,}");
    private static final Pattern TOO_MANY_EMOJI = Pattern.compile("[\\p{So}\\p{Cn}]{10,}");

    public PostValidationService(ConfigService config) {
        this.config = config;
    }

    public boolean isValid(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        if (t.length() < config.minTextLength() || t.length() > config.maxTextLength()) return false;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("как ии") || lower.contains("as an ai")) return false;
        if (REPEATED_CHARS.matcher(t).find()) return false;
        if (TOO_MANY_EMOJI.matcher(t).find()) return false;
        long upper = t.chars().filter(Character::isUpperCase).count();
        long letters = t.chars().filter(Character::isLetter).count();
        if (letters > 0 && (double) upper / (double) letters > 0.55d) return false;
        return true;
    }
}
