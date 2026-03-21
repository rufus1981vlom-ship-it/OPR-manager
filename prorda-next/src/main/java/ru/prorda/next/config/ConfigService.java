package ru.prorda.next.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

public class ConfigService {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration groups;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        reloadAll();
    }

    public void reloadAll() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        File groupsFile = new File(plugin.getDataFolder(), "groups.yml");
        if (!groupsFile.exists()) plugin.saveResource("groups.yml", false);
        this.groups = YamlConfiguration.loadConfiguration(groupsFile);
    }

    public boolean debugEnabled() { return config.getBoolean("debug.enabled", false); }
    public String vkToken() { return config.getString("vk-account.token", "").trim(); }
    public String vkApiVersion() { return config.getString("vk-account.api-version", "5.199"); }
    public String serverIp() { return config.getString("server.ip", "play.example.net"); }
    public String vkLink() { return config.getString("server.vk-link", "https://vk.com/example"); }
    public String cta() { return config.getString("auto-pr.cta", "Залетай на сервер сегодня и зови друзей!"); }
    public String textStyle() { return config.getString("auto-pr.text-style", "живой, игровой"); }
    public int prDailyLimit() { return config.getInt("auto-pr.daily-limit", 6); }
    public int promoDailyLimit() { return config.getInt("auto-promo.daily-limit", 24); }
    public int minPrLength() { return config.getInt("auto-pr.min-post-length", 60); }
    public int maxPrLength() { return config.getInt("auto-pr.max-post-length", 700); }
    public int minPromoLength() { return config.getInt("auto-promo.min-post-length", 40); }
    public int maxPromoLength() { return config.getInt("auto-promo.max-post-length", 650); }
    public int promoIntervalMinutes() { return config.getInt("auto-promo.interval-minutes", 20); }
    public int promoMaxTargets() { return config.getInt("auto-promo.max-targets", 200); }
    public boolean autoPromoEnabled() { return config.getBoolean("auto-promo.enabled", true); }
    public boolean autoPrAutoEnable() { return config.getBoolean("auto-pr.auto-enable", true); }
    public boolean smmGroupEnabled() { return config.getBoolean("auto-pr.smm-group.enabled", true); }
    public long smmOwnerId() { return config.getLong("auto-pr.smm-group.owner-id", 0L); }
    public String morning() { return config.getString("auto-pr.schedule.morning", "09:00"); }
    public String evening() { return config.getString("auto-pr.schedule.evening", "19:00"); }
    public DayOfWeek weeklyDay() { return DayOfWeek.valueOf(config.getString("auto-pr.schedule.weekly-day", "SUNDAY").toUpperCase(Locale.ROOT)); }
    public boolean eventDrivenEnabled() { return config.getBoolean("auto-pr.schedule.event-driven", true); }
    public int eventCooldownMinutes() { return config.getInt("auto-pr.event-cooldown-minutes", 90); }
    public int pvpThreshold() { return config.getInt("auto-pr.event-thresholds.pvp-kills", 6); }
    public int deathThreshold() { return config.getInt("auto-pr.event-thresholds.deaths", 15); }
    public List<String> rubrics() { return config.getStringList("auto-pr.rubrics-enabled"); }
    public String sourceEvents() { return config.getString("auto-pr.sources.events", ""); }
    public String sourceChangelog() { return config.getString("auto-pr.sources.changelog", ""); }
    public String sourceMap() { return config.getString("auto-pr.sources.map", ""); }
    public String responsesModel() { return aiModel(); }
    public String openAiKey() {
        String c = config.getString("auto-pr.openai.api-key", "").trim();
        return c.isBlank() ? System.getenv().getOrDefault("OPENAI_API_KEY", "") : c;
    }
    public String aiProvider() { return config.getString("ai.provider", "github-models").trim().toLowerCase(Locale.ROOT); }
    public String aiBaseUrl() {
        if ("github-models".equals(aiProvider())) return githubModelsEndpoint();
        String configured = config.getString("ai.base-url", "").trim();
        if (!configured.isBlank()) return configured;
        return "deepseek".equals(aiProvider()) ? "https://api.deepseek.com" : "https://api.openai.com/v1";
    }
    public String aiApiKey() {
        String configured = config.getString("ai.api-key", "").trim();
        if (!configured.isBlank()) return configured;
        String provider = aiProvider();
        if ("deepseek".equals(provider)) {
            String deepseekEnv = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "").trim();
            if (!deepseekEnv.isBlank()) return deepseekEnv;
        }
        if ("copilot".equals(provider)) {
            String githubToken = System.getenv().getOrDefault("GITHUB_TOKEN", "").trim();
            if (!githubToken.isBlank()) return githubToken;
            String copilotKey = System.getenv().getOrDefault("COPILOT_API_KEY", "").trim();
            if (!copilotKey.isBlank()) return copilotKey;
        }
        return openAiKey();
    }
    public String aiModel() {
        if ("github-models".equals(aiProvider())) return githubModelsModel();
        String configured = config.getString("ai.model", "").trim();
        if (!configured.isBlank()) return configured;
        return "deepseek".equals(aiProvider()) ? "deepseek-chat" : "gpt-5-mini";
    }
    public boolean imageGenerationEnabled() { return config.getBoolean("auto-pr.image-generation.enabled", false); }
    public boolean attachImagesToVk() { return config.getBoolean("auto-pr.image-generation.attach-to-vk", false); }
    public String imageModel() { return config.getString("auto-pr.openai.images-model", "gpt-image-1").trim(); }
    public String imageSize() { return config.getString("auto-pr.image-generation.image-size", "1024x1024").trim(); }
    public boolean githubModelsEnabled() { return config.getBoolean("ai.github-models.enabled", true); }
    public String githubModelsEndpoint() { return config.getString("ai.github-models.endpoint", "https://models.github.ai/inference/chat/completions").trim(); }
    public String githubModelsToken() {
        String explicit = config.getString("ai.github-models.token", "").trim();
        if (!explicit.isBlank()) return explicit;
        return System.getenv().getOrDefault("GITHUB_TOKEN", "").trim();
    }
    public String githubModelsModel() { return config.getString("ai.github-models.model", "openai/gpt-4.1").trim(); }
    public String githubModelsApiVersion() { return config.getString("ai.github-models.api-version", "2022-11-28").trim(); }
    public int githubModelsTimeoutMs() { return config.getInt("ai.github-models.timeout-ms", 60000); }
    public int githubModelsRetries() { return config.getInt("ai.github-models.retries", 2); }

    public String serverName() { return config.getString("server-profile.name", "PROrda"); }
    public String serverVersion() { return config.getString("server-profile.version", "1.20.4"); }
    public String serverGenre() { return config.getString("server-profile.genre", "factions"); }
    public String serverSubgenre() { return config.getString("server-profile.subgenre", "classic"); }
    public List<String> serverFeatures() { return config.getStringList("server-profile.features"); }
    public List<String> staffRecruitmentRoles() { return config.getStringList("server-profile.staff-recruitment.roles"); }

    public boolean smmEnabled() { return config.getBoolean("smm.enabled", true); }
    public long smmMainGroupId() { return -Math.abs(config.getLong("smm.main-group-id", 0L)); }
    public int smmIntervalHours() { return config.getInt("smm.interval-hours", 8); }
    public List<String> smmRubrics() { return config.getStringList("smm.rubrics"); }
    public boolean prEnabled() { return config.getBoolean("pr.enabled", true); }
    public int prCycleMinutes() { return config.getInt("pr.cycle-minutes", 60); }
    public int prDispatchWindowMinutes() { return config.getInt("pr.dispatch-window-minutes", 30); }
    public int prJitterSeconds() { return config.getInt("pr.jitter-seconds", 20); }
    public List<String> prRubrics() { return config.getStringList("pr.rubrics"); }
    public int minTextLength() { return config.getInt("validation.min-length", 80); }
    public int maxTextLength() { return config.getInt("validation.max-length", 900); }
    public int validationRetries() { return config.getInt("validation.retries", 2); }
    public List<String> promptOpeningsSmm() { return config.getStringList("prompts.smm.openings"); }
    public List<String> promptOpeningsPr() { return config.getStringList("prompts.pr.openings"); }
    public List<String> promptCtasSmm() { return config.getStringList("prompts.smm.cta"); }
    public List<String> promptCtasPr() { return config.getStringList("prompts.pr.cta"); }
    public List<String> promptTonesSmm() { return config.getStringList("prompts.smm.tones"); }
    public List<String> promptTonesPr() { return config.getStringList("prompts.pr.tones"); }
    public List<String> promptStylesSmm() { return config.getStringList("prompts.smm.styles"); }
    public List<String> promptStylesPr() { return config.getStringList("prompts.pr.styles"); }

    public LocalTime parseTime(String text, LocalTime fallback) {
        try { return LocalTime.parse(text); } catch (Exception ignored) { return fallback; }
    }

    public List<Long> promoTargetGroupIds() {
        ConfigurationSection section = groups.getConfigurationSection("groups");
        if (section == null) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node != null && node.getBoolean("enabled", true)) {
                long id = node.getLong("id", 0L);
                if (id != 0) ids.add(-Math.abs(id));
            }
        }
        return ids.stream().limit(promoMaxTargets()).toList();
    }

    public List<Long> prTargetGroupIds() {
        ConfigurationSection section = groups.getConfigurationSection("groups");
        if (section == null) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null || !node.getBoolean("enabled", true)) continue;
            long id = node.getLong("group-id", node.getLong("id", 0L));
            if (id != 0) ids.add(-Math.abs(id));
        }
        return ids;
    }

    public List<String> prPromptHints() {
        ConfigurationSection section = groups.getConfigurationSection("groups");
        if (section == null) return List.of();
        List<String> hints = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null || !node.getBoolean("enabled", true)) continue;
            String hint = node.getString("prompt-hint", "").trim();
            if (!hint.isBlank()) hints.add(hint);
        }
        return hints;
    }
}
