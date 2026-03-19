package ru.prorda.next.command;

import ru.prorda.next.ProRdaNextPlugin;
import ru.prorda.next.model.PublishDebugReport;
import ru.prorda.next.model.PublishResult;
import org.bukkit.command.*;

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
            case "testpost" -> {
                String rubric = args.length > 1 ? args[1] : "новости";
                sender.sendMessage("§7testpost started: " + rubric);
                plugin.autoPr().publishWithReport(rubric, "testpost", true).thenAccept(r -> sender.sendMessage(formatReport(r)));
            }
            case "dryrun" -> {
                String rubric = args.length > 1 ? args[1] : "новости";
                sender.sendMessage("§7dryrun started: " + rubric);
                plugin.dryRun(rubric).thenAccept(r -> sender.sendMessage(formatReport(r)));
            }
            default -> { return false; }
        }
        return true;
    }

    private String format(PublishResult r) {
        return "§bstatus=" + r.status().code() + " details=" + r.details() + " len=" + r.text().length();
    }

    private String formatReport(PublishDebugReport r) {
        return "§bstatus=" + r.result().status().code()
                + " prompt built=" + yesNo(r.promptBuilt())
                + " data threshold bypassed=" + yesNo(r.dataThresholdBypass())
                + " openai request sent=" + yesNo(r.openAiRequestSent())
                + " openai response status=" + r.openAiResponseStatus()
                + " final text length=" + r.finalTextLength();
    }

    private String yesNo(boolean v) { return v ? "yes" : "no"; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("start", "stop", "reload", "status", "debug", "testpost", "dryrun");
        if (args.length == 2 && (args[0].equalsIgnoreCase("testpost") || args[0].equalsIgnoreCase("dryrun"))) return plugin.config().rubrics();
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return List.of("on", "off");
        return List.of();
    }
}
