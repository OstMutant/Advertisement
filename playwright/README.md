# Playwright UI Tests

Скрипти для автоматизованої перевірки UI через headless Chromium.
Запускаються з середини контейнера `claude-j25-dev` через Docker socket.

## Вимоги

- Апка запущена на `localhost:8080` (контейнер `advertisement-app`)
- Docker socket доступний (монтується в `claude.bat`)

## Запуск

```bash
# Конкретний сценарій
./playwright/run.sh add-advertisement

# З UX-скріншотами (тільки коли треба аналіз UI)
./playwright/run.sh add-advertisement --ux
```

Скріншоти зберігаються в `/tmp/screenshots/` (не в репо, зникають при рестарті контейнера). Максимум 3 файли — старіші видаляються автоматично.

## Сценарії

| Файл | Що перевіряє |
|---|---|
| `add-advertisement.js` | Створення нового оголошення |
| `edit-advertisement.js` | Редагування існуючого оголошення |

## Як додати новий сценарій

1. Створи файл `playwright/my-scenario.js`
2. Імпортуй хелпери з `_common.js`
3. Запускай через `run.sh my-scenario`

## Тестові дані

- Email: `user1@example.com`
- Password: `password`
