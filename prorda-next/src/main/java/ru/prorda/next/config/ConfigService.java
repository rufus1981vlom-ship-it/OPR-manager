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
    public String aiProvider() { return config.getString("ai.provider", "openai").trim().toLowerCase(Locale.ROOT); }
    public String aiBaseUrl() {
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
        return openAiKey();
    }
    public String aiModel() {
        String configured = config.getString("ai.model", "").trim();
        if (!configured.isBlank()) return configured;
        return "deepseek".equals(aiProvider()) ? "deepseek-chat" : "gpt-5-mini";
    }
    public boolean imageGenerationEnabled() { return config.getBoolean("auto-pr.image-generation.enabled", false); }
    public boolean attachImagesToVk() { return config.getBoolean("auto-pr.image-generation.attach-to-vk", false); }

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
}
