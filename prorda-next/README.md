# PROrdaNext (Text-only SMM + PR manager)

Paper 1.20.4 плагин для автономного SMM/PR по VK:
- **SMM Manager**: постит в основную группу сервера каждые 8 часов.
- **PR Manager**: каждый час генерирует один PR-текст и отправляет его по целевым группам из `groups.yml` **не одновременно**, а в течение 30 минут с jitter.

## Ключевые возможности
- Чёткое разделение SMM и PR режимов.
- Текстовая генерация через **GitHub Models API** (`provider: github-models`).
- Retry при ошибках AI без падения scheduler.
- Валидация текста перед публикацией (мусор/длина/капс/"как ИИ").
- Anti-repeat история для SMM/PR (`mode`, `target`, `rubric`, `hash`, `status`).
- Команды `/piaro`: `start`, `stop`, `reload`, `status`, `debug`, `dryrun`, `testtext`, `postnow [smm|pr]`, `history`, `provider`.

## Настройка
1. В `config.yml` заполнить:
   - `vk-account.token`
   - `smm.main-group-id`
   - `ai.github-models.token` (или через `GITHUB_TOKEN`)
2. В `groups.yml` заполнить PR цели (`group-id`, `enabled`, лимиты и hint).
3. Перезапустить или `/piaro reload`.

## AI блок (пример)
```yaml
ai:
  provider: "github-models"
  github-models:
    enabled: true
    endpoint: "https://models.github.ai/inference/chat/completions"
    token: ""
    model: "openai/gpt-4.1"
    api-version: "2022-11-28"
    timeout-ms: 60000
    retries: 2
```

## Примечание
Проект работает в text-only режиме: отправляет только текстовый `wall.post`.
