package ru.prorda.next.command;

import org.bukkit.command.*;
import ru.prorda.next.ProRdaNextPlugin;

import java.util.List;

public class PiaroCommand implements CommandExecutor, TabCompleter {
    private final ProRdaNextPlugin plugin;

    public PiaroCommand(ProRdaNextPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "start" -> { plugin.startServices(); sender.sendMessage("§aPiaro services started"); }
            case "stop" -> { plugin.stopServices(); sender.sendMessage("§ePiaro services stopped"); }
            case "reload" -> { plugin.reloadAll(); sender.sendMessage("§aReload applied"); }
            case "status" -> sender.sendMessage(plugin.statusText());
            case "debug" -> {
                boolean on = args.length > 1 && args[1].equalsIgnoreCase("on");
                plugin.setDebug(on);
                sender.sendMessage("§aDebug=" + on);
            }
            case "dryrun", "testtext" -> plugin.dryRun()
                    .thenAccept(text -> sender.sendMessage("§bDRYRUN len=" + text.length() + " text=" + text))
                    .exceptionally(ex -> {
                        sender.sendMessage("§cDRYRUN failed: " + ex.getMessage());
                        return null;
                    });
            case "postnow" -> {
                String mode = args.length > 1 ? args[1].toLowerCase() : "all";
                if ("smm".equals(mode)) {
                    plugin.postNowSmm().thenAccept(ok -> sender.sendMessage("§bpostnow smm=" + ok));
                } else if ("pr".equals(mode)) {
                    plugin.postNowPr().thenAccept(ok -> sender.sendMessage("§bpostnow pr=" + ok));
                } else {
                    plugin.postNowSmm().thenAccept(ok -> sender.sendMessage("§bpostnow smm=" + ok));
                    plugin.postNowPr().thenAccept(ok -> sender.sendMessage("§bpostnow pr=" + ok));
                }
            }
            case "provider" -> sender.sendMessage("§bprovider=" + plugin.llmClient().provider() + " model=" + plugin.llmClient().model());
            case "history" -> sender.sendMessage("§blast=" + plugin.lastHistorySummary());
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("start", "stop", "reload", "status", "debug", "dryrun", "testtext", "postnow", "history", "provider");
        if (args.length == 2 && args[0].equalsIgnoreCase("postnow")) return List.of("smm", "pr");
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return List.of("on", "off");
        return List.of();
    }
}
