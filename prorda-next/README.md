# PROrdaNext

Новый автономный Paper 1.20.4 плагин для PR/SMM автопостинга в VK.

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
