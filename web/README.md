# SelfFlow 2.0 — Web + Android (Capacitor)

Offline-first мобильное приложение SelfFlow. Локальная SQLite — источник
правды, синхронизация с PocketBase при появлении сети. Веб-часть —
Svelte 5 (runes) + TypeScript + Vite; Android-упаковка — Capacitor 8 с
кастомным нативным плагином будильника.

## Запуск (dev в браузере)

```bash
npm i
cp .env.example .env   # VITE_PB_URL уже проставлен
npm run dev            # http://localhost:5173
npm run check          # svelte-check + tsc, 0 ошибок
npm run build          # production-сборка
npm run test:e2e       # playwright: создание рутины → вызов планирования уведомлений (мок)
```

## Сборка APK

Требуется JDK 21 (Temurin лежит в `../jdk-21` — Capacitor 8 собирается с
Java 21; путь также прописан в `android/gradle.properties`).
Android SDK: `../android-sdk` (путь в `android/local.properties`).

```bash
npm run build
npx cap sync
cd android && JAVA_HOME=/home/darkpix/Programming/SelfFlow/jdk-21 ./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

Устройство/эмулятор: `npx cap run android` (сборка web + установка + запуск,
Chrome DevTools: `chrome://inspect` для WebView-лога).

## Архитектура

```
src/
├── lib/
│   ├── types.ts            # типы всех сущностей
│   ├── auth/
│   │   ├── pb.ts           # PocketBase (VITE_PB_URL); на нативе store без localStorage
│   │   ├── session.svelte.ts  # $state-сессия; на нативе persistence через @capacitor/preferences
│   │   └── biometrics.ts   # @aparajita/capacitor-biometric-auth (LockScreen, фолбэк — PIN)
│   ├── db/
│   │   ├── driver.ts       # интерфейс DbDriver
│   │   ├── sqljs.ts        # браузер: wasm в памяти + persist в localStorage
│   │   ├── sqlite.ts       # натив: write-through зеркало в @capacitor-community/sqlite
│   │   ├── migrations.ts   # _migrations + numbered-миграции (+ schemaStatements для нативного зеркала)
│   │   ├── repositories.ts # generic Repository: CRUD, soft-delete, _sync_queue
│   │   └── id.ts           # newId() (15 симв., формат PocketBase), nowIso()
│   ├── native/
│   │   └── alarmOverlay.ts # типизированный доступ к кастомному плагину AlarmOverlay
│   ├── notifications/
│   │   ├── planner.ts      # чистый расчёт уведомлений (рутины/привычки/задачи/digest)
│   │   └── index.ts        # каналы, идемпотентное перепланирование, тестовый шов
│   ├── sync/               # push/pull синхронизация PocketBase
│   └── ui/                 # дизайн-система (+ haptics.ts — @capacitor/haptics на нативе)
├── screens/                # auth/, today/, routines/, tasks/, notes/, more/, onboarding/
└── main.ts                 # ErrorScreen-оверлей (error/unhandledrejection) → стартап → mount(App)

android/app/src/main/java/com/selfflow/app/alarm/   # кастомный Kotlin-плагин
├── AlarmOverlayPlugin.kt # scheduleAlarm/cancelAlarm/rescheduleAll/права оверлея/showOverlayNow
├── AlarmScheduler.kt     # AlarmManager: exact/setRepeating, стабильные requestCodes
├── AlarmReceiver.kt      # срабатывание → AlarmService
├── AlarmService.kt       # foreground (dataSync), ongoing-уведомление + fullScreenIntent
├── OverlayActivity.kt    # оверлей поверх lock-screen: рингтон (луп) + вибрация, Выключить/Отложить
└── BootReceiver.kt       # BOOT_COMPLETED → перепланирование из кэша SharedPreferences

android/app/src/main/res/raw/  # рингтоны: morning_light, digital_beep, classic_bell, notification_soft
```

### БД: синхронный интерфейс над нативной SQLite

Репозитории синхронные, поэтому нативный драйвер — write-through зеркало:
синхронный движок sql.js в памяти WebView (чтения/записи мгновенны),
реальная SQLite на устройстве (@capacitor-community/sqlite, файл
`selfflow-db`) — источник правды между запусками: при старте данные
загружаются из нативной БД в память, каждая мутация зеркалится в нативную БД
через сериализованную очередь. Защита по owner: при смене пользователя
нативная БД удаляется (как localStorage в браузере).

### Уведомления и будильники

- Каналы: `routine_reminders` (звук notification_soft) и `alarm_channel`
  (morning_light), оба HIGH importance.
- Планирование (`notifications/planner.ts`, пересчитывается при
  сохранении/удалении рутин и привычек, после pull-sync и при старте):
  рутины — occurrence'ы на 14 дней (правила как в `lib/routines.ts`),
  привычки с reminder_time — ежедневно, задачи с due_date — в 09:00 дня
  дедлайна (если не DONE), digest по wake_time — «Сегодня: N пунктов
  распорядка, M задач». Идемпотентно: cancel по pending-списку + заново.
  Восстановление после перезагрузки устройства делает сам плагин
  @capacitor/local-notifications.
- Wake/sleep-будильники «поверх окон» — AlarmOverlayPlugin:
  AlarmManager.setRepeating → AlarmReceiver → AlarmService (foreground,
  ongoing-уведомление с fullScreenIntent) → OverlayActivity
  (showWhenLocked/turnScreenOn, max яркость, лупинг-рингтон + waveform-вибрация
  с обязательным cancel, «Выключить»/«Отложить» реально перепланирует через
  AlarmScheduler). Расписание кэшируется в SharedPreferences и
  перепланируется BootReceiver'ом после BOOT_COMPLETED.

### Разрешения Android

`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`,
`SYSTEM_ALERT_WINDOW`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE`(+`FOREGROUND_SERVICE_DATA_SYNC`), `VIBRATE`,
`USE_BIOMETRIC`, `USE_FULL_SCREEN_INTENT`. Статусы и кнопки запроса —
на экране «Будильник».

### Принципы синхронизации

- **Push**: upsert → `update`, при 404 → `create` с тем же id и `owner`;
  delete → жёсткий DELETE на сервере (локально уже tombstone `deleted=1`).
- **Pull**: `?filter=(updated>'<ISO>')` + пагинация, last-write-wins по
  серверному `updated`.
- Триггеры: после login, `window online`, интервал 60 с, кнопка «Синхронизировать».

## Известные ограничения (фаза 4)

- Проверка нативных флоу (оверлей, точные алармы, биометрия) требует
  устройства/эмулятора; в CI-подобном окружении проверены сборка APK,
  `npm run check`, `npm run build`, `npx cap sync` и playwright-тест
  JS-планирования с моком плагина.
- Дайджест-будильник без отдельного подтверждения точных уведомлений на
  некоторых прошивках может приходить с задержкой до 9 минут (Doze).
