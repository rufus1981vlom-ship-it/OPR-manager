package ru.prorda.next.service;

import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.GameFactsSnapshot;

import java.util.List;
import java.util.StringJoiner;

public class PromptBuilder {
    private final ConfigService config;
    private final OpeningSelector openingSelector = new OpeningSelector();
    private final CtaSelector ctaSelector = new CtaSelector();
    private final ToneSelector toneSelector = new ToneSelector();
    private final RubricSelector rubricSelector = new RubricSelector();

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
        if ("pr".equals(ctx.mode())) {
            j.add("PR режим: дай прямой призыв зайти на сервер, явно укажи IP и преимущества.");
            j.add("Добавь мягкое упоминание VK-сообщества: " + config.vkLink());
        } else {
            j.add("SMM режим: интересный живой пост, не всегда рекламный, можно вопрос/юмор/мини-историю.");
        }
        if (ctx.targetHint() != null && !ctx.targetHint().isBlank()) {
            j.add("Target prompt hint: " + ctx.targetHint());
        }
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
        boolean pr = "pr".equals(mode);
        List<String> openings = pr ? merge(config.promptOpeningsPr(), OPENINGS) : merge(config.promptOpeningsSmm(), OPENINGS);
        List<String> ctas = pr ? merge(config.promptCtasPr(), CTAS) : merge(config.promptCtasSmm(), CTAS);
        List<String> tones = pr ? merge(config.promptTonesPr(), TONES) : merge(config.promptTonesSmm(), TONES);
        List<String> styles = pr ? merge(config.promptStylesPr(), STYLES) : merge(config.promptStylesSmm(), STYLES);
        String opening = openingSelector.pick(openings, OPENINGS.get(0));
        String cta = ctaSelector.pick(ctas, CTAS.get(0)).replace("{ip}", config.serverIp());
        String tone = toneSelector.pick(tones, TONES.get(0));
        String style = toneSelector.pick(styles, STYLES.get(0));
        String targetHint = pr ? String.join("; ", config.prPromptHints()) : "";
        return new PromptContext(mode, rubric, reason, facts, opening, cta, tone, style, targetHint);
    }

    public int promptVariations() {
        return OPENINGS.size() * CTAS.size() * TONES.size() * STYLES.size();
    }

    // backward-compatible helper for legacy services
    public String build(String rubric, String reason, GameFactsSnapshot facts) {
        return build(nextContext("legacy", rubric, reason, facts));
    }

    public String nextRubric(String mode) {
        if ("pr".equals(mode)) return rubricSelector.pick("pr", config.prRubrics(), "join-call");
        return rubricSelector.pick("smm", config.smmRubrics(), "community-post");
    }

    private List<String> merge(List<String> fromConfig, List<String> fallback) {
        return (fromConfig == null || fromConfig.isEmpty()) ? fallback : fromConfig;
    }
}
