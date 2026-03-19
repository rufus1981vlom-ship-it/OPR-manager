package ru.prorda.next;

import ru.prorda.next.command.PiaroCommand;
import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.PublicationStatus;
import ru.prorda.next.model.PublishDebugReport;
import ru.prorda.next.model.PublishResult;
import ru.prorda.next.service.*;
import ru.prorda.next.service.OpenAiException;
import ru.prorda.next.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class ProRdaNextPlugin extends JavaPlugin {
    private ConfigService config;
    private Storage storage;
    private RuntimeFactCollector facts;
    private AutoPrService autoPr;
    private AutoPromoService autoPromo;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        getLogger().info("[PROrdaNext] enabling...");
        this.config = new ConfigService(this);
        config.loadAll();

        this.storage = new Storage(this);
        storage.init();

        this.facts = new RuntimeFactCollector();
        Bukkit.getPluginManager().registerEvents(facts, this);

        PromptBuilder promptBuilder = new PromptBuilder(config);
        OpenAiClient openAi = new OpenAiClient(this, config);
        VkClient vk = new VkClient(this, config);
        this.autoPr = new AutoPrService(this, config, storage, facts, promptBuilder, openAi, vk);
        this.autoPromo = new AutoPromoService(this, config, autoPr, vk, storage);

        PiaroCommand piaroCommand = new PiaroCommand(this);
        PluginCommand command = getCommand("piaro");
        if (command != null) {
            command.setExecutor(piaroCommand);
            command.setTabCompleter(piaroCommand);
        }

        setDebug(config.debugEnabled());
        if (config.autoPrAutoEnable()) startServices();

        if (config.openAiKey().isBlank()) getLogger().warning("[PROrdaNext] OpenAI key missing");
        if (config.vkToken().isBlank()) getLogger().warning("[PROrdaNext] VK token missing");
        if (config.imageGenerationEnabled() && !config.attachImagesToVk()) {
            getLogger().warning("[PROrdaNext] image generation enabled but VK attachment flow is disabled (safe mode)");
        }
        getLogger().info("[PROrdaNext] enabled");
    }

    @Override
    public void onDisable() {
        stopServices();
        if (storage != null) storage.close();
        getLogger().info("[PROrdaNext] disabled");
    }

    public void reloadAll() {
        stopServices();
        config.reloadAll();
        setDebug(config.debugEnabled());
        if (config.autoPrAutoEnable()) startServices();
        storage.setState("debug.last_stage", "reload_applied");
        getLogger().info("[PROrdaNext] reload applied");
    }

    public void startServices() {
        autoPr.start();
        autoPromo.start();
    }

    public void stopServices() {
        autoPromo.stop();
        autoPr.stop();
    }

    public void setDebug(boolean enabled) {
        autoPr.setDebug(enabled);
        storage.setState("debug.enabled", String.valueOf(enabled));
    }

    public String statusText() {
        return "§bauto-pr=" + autoPr.isRunning()
                + " auto-promo=" + autoPromo.isRunning()
                + " debug=" + storage.getState("debug.enabled", "false")
                + " owner-id=" + config.smmOwnerId()
                + " vk-token=" + !config.vkToken().isBlank()
                + " openai-key=" + !config.openAiKey().isBlank()
                + " posts-today=" + storage.todayPublishedCount()
                + " last-stage=" + storage.getState("debug.last_stage", "-")
                + " last-status=" + storage.getState("debug.last_status", "-")
                + " last-openai-http=" + storage.getState("debug.last-openai-http", "-")
                + " last-openai-error-code=" + storage.getState("debug.last-openai-error-code", "-")
                + " last-openai-error-message=" + storage.getState("debug.last-openai-error-message", "-")
                + " last-error=" + storage.getState("debug.last_error", "-");
    }

    public CompletableFuture<PublishDebugReport> dryRun(String rubric) {
        String prompt = new PromptBuilder(config).build(rubric, "dryrun", facts.snapshot());
        storage.logPrompt(rubric, prompt);
        return new OpenAiClient(this, config).generatePostAsync(prompt)
                .handle((text, err) -> {
                    if (err != null) {
                        Throwable cause = (err.getCause() != null) ? err.getCause() : err;
                        if (cause instanceof OpenAiException openAiEx) {
                            storage.setState("debug.last-openai-http", String.valueOf(openAiEx.httpStatus()));
                            storage.setState("debug.last-openai-error-code", openAiEx.errorCode());
                            storage.setState("debug.last-openai-error-message", openAiEx.errorMessage());
                            return new PublishDebugReport(
                                    new PublishResult(PublicationStatus.OPENAI_FAILED, openAiEx.getMessage(), ""),
                                    true,
                                    true,
                                    true,
                                    "http_" + openAiEx.httpStatus(),
                                    0
                            );
                        }
                        return new PublishDebugReport(new PublishResult(PublicationStatus.OPENAI_FAILED, cause.getMessage(), ""), true, true, true, "failed", 0);
                    }
                    storage.setState("debug.last-openai-http", "200");
                    storage.setState("debug.last-openai-error-code", "");
                    storage.setState("debug.last-openai-error-message", "");
                    if (text == null || text.isBlank()) return new PublishDebugReport(new PublishResult(PublicationStatus.EMPTY_OPENAI_RESPONSE, "empty", ""), true, true, true, "ok_200", 0);
                    return new PublishDebugReport(new PublishResult(PublicationStatus.PUBLISHED, "dryrun_ok", text), true, true, true, "ok_200", text.length());
                });
    }

    public AutoPrService autoPr() { return autoPr; }
    public ConfigService config() { return config; }
}
