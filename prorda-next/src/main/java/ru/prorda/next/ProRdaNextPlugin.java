package ru.prorda.next;

import ru.prorda.next.command.PiaroCommand;
import ru.prorda.next.config.ConfigService;
import ru.prorda.next.service.*;
import ru.prorda.next.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class ProRdaNextPlugin extends JavaPlugin {
    private ConfigService config;
    private Storage storage;
    private RuntimeFactCollector facts;
    private SmmManager smmManager;
    private PrManager prManager;
    private LlmClient llmClient;
    private PostHistoryService postHistory;

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

        initManagers();

        PiaroCommand piaroCommand = new PiaroCommand(this);
        PluginCommand command = getCommand("piaro");
        if (command != null) {
            command.setExecutor(piaroCommand);
            command.setTabCompleter(piaroCommand);
        }

        setDebug(config.debugEnabled());
        startServices();

        if (llmClient instanceof GithubModelsTextClient && config.githubModelsToken().isBlank()) getLogger().warning("[PROrdaNext] GitHub Models token missing");
        if (!(llmClient instanceof GithubModelsTextClient) && config.aiApiKey().isBlank()) getLogger().warning("[PROrdaNext] AI key missing");
        if (config.vkToken().isBlank()) getLogger().warning("[PROrdaNext] VK token missing");
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
        initManagers();
        setDebug(config.debugEnabled());
        startServices();
        storage.setState("debug.last_stage", "reload_applied");
        getLogger().info("[PROrdaNext] reload applied");
    }

    private void initManagers() {
        PromptBuilder promptBuilder = new PromptBuilder(config);
        this.llmClient = "github-models".equals(config.aiProvider())
                ? new GithubModelsTextClient(this, config)
                : new OpenAiClient(this, config);
        VkClient vk = new VkClient(this, config);
        PostValidationService validator = new PostValidationService(config);
        this.postHistory = new PostHistoryService(storage);
        this.smmManager = new SmmManager(this, config, facts, llmClient, vk, promptBuilder, validator, postHistory);
        this.prManager = new PrManager(this, config, facts, llmClient, vk, promptBuilder, validator, postHistory);
    }

    public void startServices() {
        smmManager.start();
        prManager.start();
    }

    public void stopServices() {
        smmManager.stop();
        prManager.stop();
    }

    public void setDebug(boolean enabled) {
        storage.setState("debug.enabled", String.valueOf(enabled));
    }

    public String statusText() {
        return "§bsmm=" + smmManager.isRunning()
                + " pr=" + prManager.isRunning()
                + " debug=" + storage.getState("debug.enabled", "false")
                + " provider=" + llmClient.provider()
                + " model=" + llmClient.model()
                + " vk-token=" + !config.vkToken().isBlank()
                + " smm-today=" + storage.todayModeCount("smm")
                + " pr-today=" + storage.todayModeCount("pr")
                + " last=" + postHistory.lastSummary()
                + " last-error=" + storage.getState("debug.last_error", "-");
    }

    public CompletableFuture<String> dryRun() {
        PromptBuilder builder = new PromptBuilder(config);
        PromptContext ctx = builder.nextContext("smm", "dryrun", "manual", facts.snapshot());
        String prompt = builder.build(ctx);
        storage.logPrompt("dryrun", prompt);
        return llmClient.generatePostAsync(prompt);
    }

    public CompletableFuture<Boolean> postNowSmm() { return smmManager.postNow(); }
    public CompletableFuture<Boolean> postNowPr() { return prManager.postNow(); }
    public LlmClient llmClient() { return llmClient; }
    public String lastHistorySummary() { return postHistory.lastSummary(); }
    public ConfigService config() { return config; }
}
