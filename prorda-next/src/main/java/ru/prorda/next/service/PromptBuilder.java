package ru.prorda.next.service;

import ru.prorda.next.config.ConfigService;
import ru.prorda.next.model.GameFactsSnapshot;

import java.util.StringJoiner;

public class PromptBuilder {
    private final ConfigService config;

    public PromptBuilder(ConfigService config) { this.config = config; }

    public String build(String rubric, String reason, GameFactsSnapshot facts) {
        StringJoiner j = new StringJoiner("\n");
        j.add("Ты SMM-редактор Minecraft-проекта. Пиши только по-русски.");
        j.add("Рубрика: " + rubric + "; причина: " + reason + ".");
        j.add("Стиль: " + config.textStyle() + ". Без канцелярита, живой тон.");
        j.add("Сделай CTA: " + config.cta());
        j.add("IP сервера: " + config.serverIp());
        j.add("Ссылка VK: " + config.vkLink());
        j.add("Ограничение длины: " + config.minPrLength() + ".." + config.maxPrLength() + " символов.");
        j.add("Игровые факты: online=" + facts.online() + ", peakStart=" + facts.peakSinceStart() + ", peakDay=" + facts.peakToday() +
                ", joins=" + facts.joins() + ", quits=" + facts.quits() + ", deaths=" + facts.deaths() + ", pvpKills=" + facts.pvpKills());
        j.add("Последние события: " + String.join(" | ", facts.recentEvents()));
        j.add("Changelog: " + config.sourceChangelog());
        j.add("Map/Spawn: " + config.sourceMap());
        j.add("Доп.события: " + config.sourceEvents());
        j.add("Верни только текст поста, без markdown и без служебных пояснений.");
        return j.toString();
    }
}
