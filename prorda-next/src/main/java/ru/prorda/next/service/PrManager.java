package ru.prorda.next.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.GameFactsSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrManager {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final RuntimeFactCollector facts;
    private final LlmClient llm;
    private final VkClient vk;
    private final PromptBuilder promptBuilder;
    private final PostValidationService validator;
    private final PostHistoryService history;
    private BukkitTask cycleTask;
    private volatile boolean running;
    private final AtomicBoolean cycleInProgress = new AtomicBoolean(false);

    public PrManager(JavaPlugin plugin, ConfigService config, RuntimeFactCollector facts, LlmClient llm, VkClient vk,
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
        if (running || !config.prEnabled()) return;
        running = true;
        long periodTicks = Math.max(1, config.prCycleMinutes()) * 60L * 20L;
        cycleTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runCycle, 120L, periodTicks);
    }

    public synchronized void stop() {
        running = false;
        if (cycleTask != null) cycleTask.cancel();
        cycleTask = null;
    }

    public boolean isRunning() { return running; }

    public CompletableFuture<Boolean> postNow() {
        if (!cycleInProgress.compareAndSet(false, true)) return CompletableFuture.completedFuture(false);
        return generatePrText().thenCompose(this::dispatchOverWindow)
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[PR] cycle failed: " + ex.getMessage());
                    return false;
                })
                .whenComplete((r, e) -> cycleInProgress.set(false));
    }

    private void runCycle() {
        postNow();
    }

    private CompletableFuture<String> generatePrText() {
        GameFactsSnapshot snapshot = facts.snapshot();
        String rubric = promptBuilder.nextRubric("pr");
        PromptContext ctx = promptBuilder.nextContext("pr", rubric, "hourly-cycle", snapshot);
        return generateWithRetry(ctx, config.validationRetries());
    }

    private CompletableFuture<String> generateWithRetry(PromptContext ctx, int retries) {
        String prompt = promptBuilder.build(ctx);
        return llm.generatePostAsync(prompt).thenCompose(text -> {
            if (validator.isValid(text)) return CompletableFuture.completedFuture(text);
            if (retries <= 0) return CompletableFuture.failedFuture(new IllegalStateException("validation_failed"));
            return generateWithRetry(ctx, retries - 1);
        });
    }

    private CompletableFuture<Boolean> dispatchOverWindow(String text) {
        List<Long> targets = new ArrayList<>(config.prTargetGroupIds());
        if (targets.isEmpty()) return CompletableFuture.completedFuture(false);

        int totalSeconds = Math.max(300, config.prDispatchWindowMinutes() * 60);
        int baseGap = Math.max(5, totalSeconds / Math.max(1, targets.size()));

        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (int i = 0; i < targets.size(); i++) {
            long target = targets.get(i);
            int jitter = ThreadLocalRandom.current().nextInt(-config.prJitterSeconds(), config.prJitterSeconds() + 1);
            long delaySeconds = Math.max(3, (long) i * baseGap + jitter);
            chain = chain.thenCompose(ok -> delayedPost(target, text, delaySeconds));
        }
        return chain;
    }

    private CompletableFuture<Boolean> delayedPost(long target, String text, long delaySeconds) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            vk.postToWallAsync(target, text).thenAccept(raw -> {
                history.log("pr", target, "dispatch", history.hash(text), true, "published");
                f.complete(true);
            }).exceptionally(ex -> {
                history.log("pr", target, "dispatch", history.hash(text), false, ex.getMessage());
                plugin.getLogger().warning("[PR] target failed " + target + ": " + ex.getMessage());
                f.complete(true);
                return null;
            });
        }, delaySeconds * 20L);
        return f;
    }
}
