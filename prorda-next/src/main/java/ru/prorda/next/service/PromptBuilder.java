package ru.prorda.next.service;

import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.GameFactsSnapshot;

import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

public class PromptBuilder {
    private final ConfigService config;

    private static final List<String> OPENINGS = List.of(
            "Сегодня на сервере снова жарко",
            "Пока ты читаешь это — на карте уже движ",
            "Комьюнити не спит и собирается в игру",
            "Вечер обещает быть насыщенным",
            "Небольшой апдейт по жизни сервера",
            "У нас тут родилась новая история",
            "Если любишь живой онлайн — тебе к нам",
            "Этот пост для тех, кто любит Minecraft с вайбом",
            "Коротко о главном на сегодня",
            "Лови свежий сигнал с сервера"
    );

    private static final List<String> CTAS = List.of(
            "Залетай сегодня и позови друзей.",
            "Заходи прямо сейчас — онлайн ждёт.",
            "Собирай клан и врывайся в игру.",
            "Если давно хотел вернуться — идеальный момент.",
            "Проверь сервер этим вечером.",
            "Залетай на пару часов и оцени атмосферу.",
            "Подключайся и покажи свой скилл.",
            "Приходи и оставь след в истории сервера."
    );

    private static final List<String> TONES = List.of(
            "живой",
            "дружелюбный",
            "энергичный",
            "ламповый",
            "ироничный",
            "боевой"
    );

    private static final List<String> STYLES = List.of(
            "короткие абзацы",
            "без канцелярита",
            "простые формулировки",
            "естественный разговорный язык",
            "без заезженных клише",
            "вовлекающий формат с вопросом"
    );

    public PromptBuilder(ConfigService config) {
        this.config = config;
    }

    public String build(PromptContext ctx) {
        StringJoiner j = new StringJoiner("\n");
        j.add("Ты редактор Minecraft-сообщества. Пиши по-русски, только готовый текст поста.");
        j.add("Режим: " + ctx.mode() + ", рубрика: " + ctx.rubric() + ", причина: " + ctx.reason() + ".");
        j.add("Открытие поста: " + ctx.opening());
        j.add("Тон: " + ctx.tone() + ". Стиль: " + ctx.style() + ".");
        j.add("В конце используй CTA: " + ctx.cta());
        j.add("Сервер: " + config.serverName() + " | IP: " + config.serverIp() + " | VK: " + config.vkLink());
        j.add("Версия: " + config.serverVersion() + " | Жанр: " + config.serverGenre() + " | Поджанр: " + config.serverSubgenre());
        j.add("Фичи: " + String.join(", ", config.serverFeatures()));
        j.add("Набор в стафф: " + String.join(", ", config.staffRecruitmentRoles()));
        j.add("Факты: online=" + ctx.facts().online() + ", peakDay=" + ctx.facts().peakToday() + ", joins=" + ctx.facts().joins() + ", deaths=" + ctx.facts().deaths() + ", pvpKills=" + ctx.facts().pvpKills());
        j.add("События: " + String.join(" | ", ctx.facts().recentEvents()));
        j.add("Ограничение длины: " + config.minTextLength() + ".." + config.maxTextLength() + " символов.");
        j.add("Запрещено: 'как ИИ', markdown, хэштеги простынёй, спам-эмодзи.");
        return j.toString();
    }

    public PromptContext nextContext(String mode, String rubric, String reason, ru.prorda.next.model.GameFactsSnapshot facts) {
        int seed = ThreadLocalRandom.current().nextInt();
        String opening = OPENINGS.get(Math.floorMod(seed, OPENINGS.size()));
        String cta = CTAS.get(Math.floorMod(seed / 7, CTAS.size()));
        String tone = TONES.get(Math.floorMod(seed / 13, TONES.size()));
        String style = STYLES.get(Math.floorMod(seed / 17, STYLES.size()));
        return new PromptContext(mode, rubric, reason, facts, opening, cta, tone, style);
    }

    public int promptVariations() {
        return OPENINGS.size() * CTAS.size() * TONES.size() * STYLES.size();
    }

    // backward-compatible helper for legacy services
    public String build(String rubric, String reason, GameFactsSnapshot facts) {
        return build(nextContext("legacy", rubric, reason, facts));
    }
}
