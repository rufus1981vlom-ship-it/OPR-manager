package ru.prorda.next.service;

import ru.prorda.next.model.GameFactsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;

public class RuntimeFactCollector implements Listener {
    private final ArrayDeque<String> recentEvents = new ArrayDeque<>();
    private int peakSinceStart;
    private int peakToday;
    private LocalDate peakDate = LocalDate.now();
    private long joins;
    private long quits;
    private long deaths;
    private long pvpKills;

    public synchronized GameFactsSnapshot snapshot() {
        rotateDayIfNeeded();
        return new GameFactsSnapshot(Bukkit.getOnlinePlayers().size(), peakSinceStart, peakToday, joins, quits, deaths, pvpKills, List.copyOf(recentEvents));
    }

    private void rotateDayIfNeeded() {
        if (!LocalDate.now().equals(peakDate)) {
            peakDate = LocalDate.now();
            peakToday = Bukkit.getOnlinePlayers().size();
        }
    }

    private synchronized void trackEvent(String event) {
        rotateDayIfNeeded();
        while (recentEvents.size() >= 40) recentEvents.removeFirst();
        recentEvents.addLast(event);
        int online = Bukkit.getOnlinePlayers().size();
        peakSinceStart = Math.max(peakSinceStart, online);
        peakToday = Math.max(peakToday, online);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { joins++; trackEvent("Игрок зашёл: " + e.getPlayer().getName()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { quits++; trackEvent("Игрок вышел: " + e.getPlayer().getName()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        deaths++;
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            pvpKills++;
            trackEvent("PvP kill: " + killer.getName() + " -> " + e.getEntity().getName());
        } else {
            trackEvent("Игрок умер: " + e.getEntity().getName());
        }
    }
}
