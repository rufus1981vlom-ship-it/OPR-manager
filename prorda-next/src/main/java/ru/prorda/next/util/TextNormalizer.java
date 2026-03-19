package ru.prorda.next.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class TextNormalizer {
    private TextNormalizer() {}

    public static String normalizeCore(String text) {
        String t = text == null ? "" : text;
        t = t.replaceAll("https?://\\S+", " ");
        t = t.replaceAll("play\\.[a-z0-9.-]+", " ");
        t = t.replaceAll("vk\\.com/\\S+", " ");
        t = t.toLowerCase().replaceAll("[^\\p{L}\\p{N} ]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
