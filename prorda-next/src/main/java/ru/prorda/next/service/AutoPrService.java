package ru.prorda.next.service;

import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.GameFactsSnapshot;
import ru.prorda.next.model.PublicationStatus;
import ru.prorda.next.model.PublishDebugReport;
import ru.prorda.next.model.PublishResult;
import ru.prorda.next.storage.Storage;
import ru.prorda.next.util.TextNormalizer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoPrService {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final Storage storage;
    private final RuntimeFactCollector facts;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final VkClient vk;

    private BukkitTask heartbeatTask;
    private volatile boolean running;
    private volatile boolean debug;
    private final AtomicBoolean publishInProgress = new AtomicBoolean(false);

    public AutoPrService(JavaPlugin plugin, ConfigService config, Storage storage, RuntimeFactCollector facts,
                         PromptBuilder promptBuilder, LlmClient llmClient, VkClient vk) {
        this.plugin = plugin; this.config = config; this.storage = storage; this.facts = facts;
        this.promptBuilder = promptBuilder; this.llmClient = llmClient; this.vk = vk;
    }

    public void setDebug(boolean debug) { this.debug = debug; }
    public boolean isRunning() { return running; }

    public synchronized void start() {
        if (running) return;
        running = true;
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L, 20L * 30L);
    }

    public synchronized void stop() {
        running = false;
        if (heartbeatTask != null) heartbeatTask.cancel();
        heartbeatTask = null;
    }

    private void tick() {
        if (!running) return;
        if (debug) plugin.getLogger().info("[auto-pr] heartbeat");
        LocalDateTime now = LocalDateTime.now();
        LocalTime morning = config.parseTime(config.morning(), LocalTime.of(9, 0));
        LocalTime evening = config.parseTime(config.evening(), LocalTime.of(19, 0));
        if (isDue("auto-pr.last_morning", now, morning)) publish("новости", "morning");
        if (isDue("auto-pr.last_evening", now, evening)) publish("анонсы", "evening");
        if (now.getDayOfWeek() == config.weeklyDay() && isDue("auto-pr.last_weekly", now, LocalTime.of(12, 0))) publish("итоги недели", "weekly");

        if (config.eventDrivenEnabled()) {
            GameFactsSnapshot s = facts.snapshot();
            if (s.pvpKills() >= config.pvpThreshold() || s.deaths() >= config.deathThreshold()) {
                long last = Long.parseLong(storage.getState("auto-pr.last_event_ts", "0"));
                long cd = config.eventCooldownMinutes() * 60L;
                long nowTs = Instant.now().getEpochSecond();
                if (nowTs - last > cd) {
                    storage.setState("auto-pr.last_event_ts", String.valueOf(nowTs));
                    publish("живые посты по событиям", "event-driven");
                }
            }
        }
    }

    private boolean isDue(String key, LocalDateTime now, LocalTime time) {
        String today = LocalDate.now().toString();
        String marker = storage.getState(key, "");
        return now.toLocalTime().isAfter(time) && !today.equals(marker);
    }

    public CompletableFuture<PublishResult> publish(String rubric, String reason) {
        return publishWithReport(rubric, reason, false).thenApply(PublishDebugReport::result);
    }

    public CompletableFuture<PublishDebugReport> publishWithReport(String rubric, String reason, boolean forceTestBypass) {
        if (!config.rubrics().contains(rubric)) return completed(PublicationStatus.SKIPPED_RUBRIC_DISABLED, "Rubric disabled", "");
        if (!config.smmGroupEnabled()) return completed(PublicationStatus.SKIPPED_NO_OWNER_ID, "smm-group disabled", "");
        if (config.smmOwnerId() == 0) return completed(PublicationStatus.SKIPPED_NO_OWNER_ID, "owner-id is 0", "");
        if (storage.todayPublishedCount() >= config.prDailyLimit()) return completed(PublicationStatus.SKIPPED_DAILY_LIMIT, "daily limit reached", "");
        if (!publishInProgress.compareAndSet(false, true)) return completed(PublicationStatus.SKIPPED_NOT_ENOUGH_DATA, "publish already in progress", "");

        storage.setState("debug.last_stage", "prompt_building");
        GameFactsSnapshot snapshot = withFallbackEventIfNeeded(facts.snapshot(), forceTestBypass);
        if (snapshot.recentEvents().isEmpty()) return completedCleanup(PublicationStatus.SKIPPED_NOT_ENOUGH_DATA, "no events collected yet", "");
        if (storage.topicUsedRecently(rubric, 1)) return completedCleanup(PublicationStatus.SKIPPED_TOPIC_RECENTLY_USED, "rubric recently used", "");

        String prompt = promptBuilder.build(rubric, reason, snapshot);
        storage.logPrompt(rubric, prompt);
        storage.setState("debug.last_stage", "openai_request");
        storage.setState("debug.ai.provider", llmClient.provider());
        storage.setState("debug.ai.base-url", llmClient.baseUrl());
        storage.setState("debug.ai.model", llmClient.model());
        return llmClient.generatePostAsync(prompt)
                .handle((text, err) -> {
                    if (err != null) {
                        Throwable cause = unwrap(err);
                        if (cause instanceof AiProviderException aiEx) {
                            storage.setState("debug.last-openai-http", String.valueOf(aiEx.httpStatus()));
                            storage.setState("debug.last-openai-error-code", aiEx.errorCode());
                            storage.setState("debug.last-openai-error-message", aiEx.errorMessage());
                            storage.logOpenAi(rubric, reason, "http_" + aiEx.httpStatus(), aiEx.errorBody(), "");
                            return new PublishDebugReport(
                                    new PublishResult(PublicationStatus.OPENAI_FAILED, aiEx.getMessage(), ""),
                                    true,
                                    forceTestBypass,
                                    true,
                                    llmClient.provider(),
                                    llmClient.baseUrl(),
                                    llmClient.model(),
                                    String.valueOf(aiEx.httpStatus()),
                                    aiEx.errorBody(),
                                    "http_" + aiEx.httpStatus(),
                                    0
                            );
                        }
                        storage.logOpenAi(rubric, reason, "failed", cause.getMessage(), "");
                        return new PublishDebugReport(
                                new PublishResult(PublicationStatus.OPENAI_FAILED, cause.getMessage(), ""),
                                true,
                                forceTestBypass,
                                true,
                                llmClient.provider(),
                                llmClient.baseUrl(),
                                llmClient.model(),
                                "-",
                                cause.getMessage(),
                                "failed",
                                0
                        );
                    }
                    storage.logOpenAi(rubric, reason, "ok", "", text);
                    storage.setState("debug.last-openai-http", "200");
                    storage.setState("debug.last-openai-error-code", "");
                    storage.setState("debug.last-openai-error-message", "");
                    if (text == null || text.isBlank()) return new PublishDebugReport(new PublishResult(PublicationStatus.EMPTY_OPENAI_RESPONSE, "empty text", ""), true, forceTestBypass, true, llmClient.provider(), llmClient.baseUrl(), llmClient.model(), "200", "", "ok_200", 0);
                    if (text.length() < config.minPrLength()) return new PublishDebugReport(new PublishResult(PublicationStatus.FILTERED_TOO_SHORT, "text too short", text), true, forceTestBypass, true, llmClient.provider(), llmClient.baseUrl(), llmClient.model(), "200", "", "ok_200", text.length());
                    String fp = TextNormalizer.sha256(TextNormalizer.normalizeCore(text));
                    List<String> recent = storage.recentFingerprints(30);
                    if (recent.contains(fp)) return new PublishDebugReport(new PublishResult(PublicationStatus.DUPLICATE_TEXT, "duplicate fingerprint", text), true, forceTestBypass, true, llmClient.provider(), llmClient.baseUrl(), llmClient.model(), "200", "", "ok_200", text.length());
                    return new PublishDebugReport(new PublishResult(PublicationStatus.PUBLISHED, fp, text), true, forceTestBypass, true, llmClient.provider(), llmClient.baseUrl(), llmClient.model(), "200", "", "ok_200", text.length());
                })
                .thenCompose(report -> {
                    PublishResult result = report.result();
                    if (result.status() != PublicationStatus.PUBLISHED) return CompletableFuture.completedFuture(report);
                    storage.setState("debug.last_stage", "vk_publish");
                    CompletableFuture<String> finalTextFuture = prepareFinalTextAsync(result.text());
                    return finalTextFuture.thenCompose(finalText -> vk.postToWallAsync(config.smmOwnerId(), trim(finalText, config.maxPrLength())))
                            .<PublishDebugReport>handle((vkRaw, vkErr) -> vkErr == null
                                    ? new PublishDebugReport(new PublishResult(PublicationStatus.PUBLISHED, result.details(), result.text()), report.promptBuilt(), report.dataThresholdBypass(), report.openAiRequestSent(), report.provider(), report.baseUrl(), report.model(), report.httpStatus(), report.errorBody(), report.openAiResponseStatus(), report.finalTextLength())
                                    : new PublishDebugReport(new PublishResult(PublicationStatus.VK_PUBLISH_FAILED, vkErr.getMessage(), result.text()), report.promptBuilt(), report.dataThresholdBypass(), report.openAiRequestSent(), report.provider(), report.baseUrl(), report.model(), report.httpStatus(), report.errorBody(), report.openAiResponseStatus(), report.finalTextLength()));
                })
                .thenApply(report -> {
                    PublishResult r = report.result();
                    storeResult(rubric, reason, r);
                    if (r.isPublished()) {
                        storage.logTopic(rubric);
                        if (reason.equals("morning")) storage.setState("auto-pr.last_morning", LocalDate.now().toString());
                        if (reason.equals("evening")) storage.setState("auto-pr.last_evening", LocalDate.now().toString());
                        if (reason.equals("weekly")) storage.setState("auto-pr.last_weekly", LocalDate.now().toString());
                    }
                    publishInProgress.set(false);
                    return report;
                });
    }

    private String trim(String text, int max) { return text.length() > max ? text.substring(0, max) : text; }

    private CompletableFuture<String> prepareFinalTextAsync(String text) {
        if (!config.imageGenerationEnabled() || !config.attachImagesToVk()) return CompletableFuture.completedFuture(text);
        storage.setState("debug.last_stage", "image_generation");
        return llmClient.generateImageUrlAsync("Сгенерируй иллюстрацию к посту: " + text)
                .handle((imageUrl, err) -> {
                    if (err != null || imageUrl == null || imageUrl.isBlank()) {
                        Throwable cause = unwrap(err == null ? new RuntimeException("empty_image_url") : err);
                        plugin.getLogger().warning("[AI:image] failed: " + cause.getMessage());
                        return text;
                    }
                    return text + "\n\n🖼 " + imageUrl;
                });
    }

    private CompletableFuture<PublishDebugReport> completed(PublicationStatus status, String details, String text) {
        PublishResult result = new PublishResult(status, details, text);
        storeResult("-", "manual-precheck", result);
        return CompletableFuture.completedFuture(new PublishDebugReport(result, false, false, false, llmClient.provider(), llmClient.baseUrl(), llmClient.model(), "-", "", "not_sent", text == null ? 0 : text.length()));
    }
    private CompletableFuture<PublishDebugReport> completedCleanup(PublicationStatus s, String d, String t) {
        publishInProgress.set(false);
        return completed(s, d, t);
    }

    private GameFactsSnapshot withFallbackEventIfNeeded(GameFactsSnapshot snapshot, boolean forceBypass) {
        if (!snapshot.recentEvents().isEmpty()) return snapshot;
        if (!forceBypass) return snapshot;
        return new GameFactsSnapshot(
                snapshot.online(),
                snapshot.peakSinceStart(),
                snapshot.peakToday(),
                snapshot.joins(),
                snapshot.quits(),
                snapshot.deaths(),
                snapshot.pvpKills(),
                List.of("События: пока без заметных игровых событий")
        );
    }

    private Throwable unwrap(Throwable err) {
        if (err instanceof CompletionException ce && ce.getCause() != null) return ce.getCause();
        return err;
    }

    private void storeResult(String rubric, String reason, PublishResult r) {
        storage.logPublicationStatus(rubric, r.status(), r.details());
        String fp = r.status() == PublicationStatus.PUBLISHED ? r.details() : "";
        storage.logPostHistory(rubric, reason, r.text(), r.status().code(), r.details(), fp);
    }
}
