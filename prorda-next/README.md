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
- Поддержка AI-провайдеров: OpenAI / DeepSeek / Copilot (GitHub Models).
- SQLite storage с авто-recovery при corrupt DB.
- `/piaro` команды: start/stop/reload/status/debug/testpost/dryrun.

## Важно
- Сейчас поддержан только текстовый `wall.post`.
- Image generation/upload в VK по умолчанию безопасно отключены.
- LLM-провайдер переключается через `config.yml` (`ai.provider`: `openai`, `deepseek`, `copilot`).
- Для ключей можно использовать либо `ai.api-key`, либо переменные окружения:
  - `OPENAI_API_KEY` для `openai`;
  - `DEEPSEEK_API_KEY` для `deepseek` (и `OPENAI_API_KEY` как запасной fallback);
  - `GITHUB_TOKEN` или `COPILOT_API_KEY` для `copilot` (и `OPENAI_API_KEY` как запасной fallback).
- При `auto-pr.image-generation.enabled=true` и `attach-to-vk=true` плагин запрашивает генерацию изображения у текущего AI-провайдера (для OpenAI/Copilot) и добавляет ссылку на изображение в текст поста перед отправкой в VK.
