package ru.prorda.next.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.GameFactsSnapshot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmmManager {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final RuntimeFactCollector facts;
    private final LlmClient llm;
    private final VkClient vk;
    private final PromptBuilder promptBuilder;
    private final PostValidationService validator;
    private final PostHistoryService history;
    private BukkitTask task;
    private volatile boolean running;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    public SmmManager(JavaPlugin plugin, ConfigService config, RuntimeFactCollector facts, LlmClient llm, VkClient vk,
                      PromptBuilder promptBuilder, PostValidationService validator, PostHistoryService history) {
        this.plugin = plugin;
        this.config = config;
        this.facts = facts;
        this.llm = llm;
        this.vk = vk;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
        this.history = history;
    }

    public synchronized void start() {
        if (running || !config.smmEnabled()) return;
        running = true;
        long periodTicks = Math.max(1, config.smmIntervalHours()) * 60L * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 60L, periodTicks);
    }

    public synchronized void stop() {
        running = false;
        if (task != null) task.cancel();
        task = null;
    }

    public boolean isRunning() { return running; }

    public CompletableFuture<Boolean> postNow() {
        if (!inProgress.compareAndSet(false, true)) return CompletableFuture.completedFuture(false);
        GameFactsSnapshot snapshot = facts.snapshot();
        String rubric = promptBuilder.nextRubric("smm");
        PromptContext ctx = promptBuilder.nextContext("smm", rubric, "scheduled", snapshot);
        return generateWithRetry(ctx, config.validationRetries())
                .thenCompose(text -> vk.postToWallAsync(config.smmMainGroupId(), text).thenApply(x -> text))
                .thenApply(text -> {
                    history.log("smm", config.smmMainGroupId(), rubric, history.hash(text), true, "published");
                    return true;
                })
                .exceptionally(ex -> {
                    history.log("smm", config.smmMainGroupId(), rubric, "", false, ex.getMessage());
                    plugin.getLogger().warning("[SMM] post failed: " + ex.getMessage());
                    return false;
                })
                .whenComplete((ok, ex) -> inProgress.set(false));
    }

    private void tick() {
        postNow();
    }

    private CompletableFuture<String> generateWithRetry(PromptContext ctx, int retries) {
        String prompt = promptBuilder.build(ctx);
        return llm.generatePostAsync(prompt).thenCompose(text -> {
            if (validator.isValid(text)) return CompletableFuture.completedFuture(text);
            if (retries <= 0) return CompletableFuture.failedFuture(new IllegalStateException("validation_failed"));
            return generateWithRetry(ctx, retries - 1);
        });
    }

}
