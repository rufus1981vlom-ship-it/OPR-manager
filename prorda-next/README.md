# PROrdaNext

Новый автономный Paper 1.20.4 плагин для PR/SMM автопостинга в VK.

## Сборка
```bash
cd prorda-next
JAVA_HOME=/path/to/jdk-21 gradle clean jar
```

> Исходники собираются с Java toolchain 17 (runtime совместим с Java 17+),
> команда выше запускает Gradle под JDK 21, что обычно стабильнее для окружений с новым Gradle.

## Что реализовано
- Runtime-сбор фактов (online/joins/quits/deaths/pvp/events).
- Auto-PR (утро/вечер/неделя/event-driven).
- Auto-promo по `groups.yml` (очередь, интервал, переход к следующей группе при ошибке).
- OpenAI и VK клиент на `java.net.http.HttpClient`.
- SQLite storage с авто-recovery при corrupt DB.
- `/piaro` команды: start/stop/reload/status/debug/testpost/dryrun.

## Важно
- Сейчас поддержан только текстовый `wall.post`.
- Image generation/upload в VK по умолчанию безопасно отключены.
- LLM-провайдер переключается только через `config.yml` (`ai.provider`: `openai` или `deepseek`).
- Для ключей можно использовать либо `ai.api-key`, либо переменные окружения:
  - `OPENAI_API_KEY` для `openai`;
  - `DEEPSEEK_API_KEY` для `deepseek` (и `OPENAI_API_KEY` как запасной fallback).
