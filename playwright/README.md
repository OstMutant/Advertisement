# Playwright UI Tests

Автоматизовані E2E тести на `@playwright/test` (headless Chromium).

## Вимоги

- Апка запущена на `localhost:8080` (контейнер `advertisement-app`)
- Docker socket доступний

## Запуск

```bash
# Всі тести
bash /app/playwright/run.sh

# Один сценарій
bash /app/playwright/run.sh smoke
bash /app/playwright/run.sh add-advertisement

# З локальними скріншотами (для AI-аналізу через Read tool)
bash /app/playwright/run.sh --ux
bash /app/playwright/run.sh smoke --ux
```

Після запуску:
- HTML-звіт (зі скріншотами при падінні): `/app/playwright/pw-report/index.html`
- Локальні скріншоти (тільки `--ux`): `/app/playwright/screenshots/`

## Сценарії

| Файл | Що перевіряє |
|---|---|
| `smoke.spec.js` | Мова, повний user flow, admin flow |
| `add-advertisement.spec.js` | Створення оголошення |
| `edit-advertisement.spec.js` | Редагування оголошення |
| `advertisement-history.spec.js` | Історія: create → edit → restore |
| `history-deep.spec.js` | Версії, бейджі, restore flow |
| `filter-advertisements.spec.js` | Фільтрація і сортування оголошень |
| `filter-users.spec.js` | Фільтрація користувачів (admin) |
| `upload-image.spec.js` | Завантаження одного фото |
| `upload-gallery.spec.js` | Завантаження кількох фото |
| `photo-activity.spec.js` | Фото-дифи в історії та активності |
| `verify-photo-history.spec.js` | Перевірка photo-змін у history |
| `verify-thumbnail-history.spec.js` | Thumbnails на картках + history tab |
| `settings.spec.js` | Налаштування: зміна page size |
| `settings-activity.spec.js` | Активність при зміні налаштувань |
| `activity-types.spec.js` | CREATED / UPDATED / DELETED бейджі |
| `user-activity.spec.js` | Активність у профілі користувача |
| `user-edit-diff.spec.js` | Diff при редагуванні user |
| `users-view.spec.js` | Users grid (admin): view + edit overlay |
| `test-view.spec.js` | Відкриття overlay деталі оголошення |
| `change-language.spec.js` | Перемикання мови (без авторизації) |

## Як додати новий сценарій

1. Створи `/app/playwright/my-scenario.spec.js`
2. `const { test, expect, loginAs, ... } = require('./_test-helpers');`
3. Запускай: `bash /app/playwright/run.sh my-scenario`

## Тестові дані

| Email | Роль |
|---|---|
| `user1@example.com` | звичайний користувач |
| `user2@example.com` | звичайний користувач |
| `user3@example.com` | admin |

Пароль для всіх: `password`
