package ru.prorda.next.service;

import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.PublicationStatus;
import ru.prorda.next.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoPromoService {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final AutoPrService autoPr;
    private final VkClient vk;
    private final Storage storage;

    private BukkitTask task;
    private boolean running;
    private int cursor;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    public AutoPromoService(JavaPlugin plugin, ConfigService config, AutoPrService autoPr, VkClient vk, Storage storage) {
        this.plugin = plugin; this.config = config; this.autoPr = autoPr; this.vk = vk; this.storage = storage;
    }

    public boolean isRunning() { return running; }

    public synchronized void start() {
        if (running || !config.autoPromoEnabled()) return;
        running = true;
        long ticks = Math.max(1, config.promoIntervalMinutes()) * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 100L, ticks);
    }

    public synchronized void stop() {
        running = false;
        if (task != null) task.cancel();
        task = null;
    }

    private void tick() {
        if (!running || !inProgress.compareAndSet(false, true)) return;
        if (storage.todayPublishedCount() >= config.promoDailyLimit()) {
            storage.logPublicationStatus("promo", PublicationStatus.SKIPPED_DAILY_LIMIT, "promo daily limit reached");
            inProgress.set(false);
            return;
        }
        List<Long> targets = config.promoTargetGroupIds();
        if (targets.isEmpty()) {
            storage.logPublicationStatus("promo", PublicationStatus.SKIPPED_NO_TARGETS, "no enabled targets in groups.yml");
            inProgress.set(false);
            return;
        }
        long owner = targets.get(cursor % targets.size());
        cursor++;
        autoPr.publish("новости", "promo")
                .thenCompose(r -> {
                    if (!r.isPublished()) return java.util.concurrent.CompletableFuture.completedFuture(r);
                    String text = r.text();
                    if (text.length() < config.minPromoLength()) return java.util.concurrent.CompletableFuture.completedFuture(r);
                    if (text.length() > config.maxPromoLength()) text = text.substring(0, config.maxPromoLength());
                    String finalText = text;
                    return vk.postToWallAsync(owner, finalText).handle((ok, err) -> {
                        if (err != null) {
                            storage.logPublicationStatus("promo", PublicationStatus.VK_PUBLISH_FAILED, "target=" + owner + ", " + err.getMessage());
                        } else {
                            storage.logPublicationStatus("promo", PublicationStatus.PUBLISHED, "target=" + owner);
                        }
                        return r;
                    });
                })
                .whenComplete((x, e) -> inProgress.set(false));
    }
}
