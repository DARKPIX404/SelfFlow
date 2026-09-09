import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import ErrorScreen from './ErrorScreen.svelte'

// --- Диагностируемость: любая фатальная ошибка — на экран, не в чёрный провал.
// Оверлей ставится ДО старта инициализации и ловит и ошибки стартапа
// (start().catch), и поздние падения (глобальные слушатели).

function showErrorOverlay(err: unknown): void {
  console.error('[startup] fatal', err)
  if (document.getElementById('error-overlay')) return
  const target = document.createElement('div')
  target.id = 'error-overlay'
  document.body.appendChild(target)
  mount(ErrorScreen, { target, props: { error: err } })
}

window.addEventListener('error', (e) => showErrorOverlay(e.error ?? e.message))
window.addEventListener('unhandledrejection', (e) => {
  e.preventDefault()
  showErrorOverlay(e.reason)
})

async function start(): Promise<void> {
  const { prepareDbForUser, restoreNativeSession, prepareDbForUserNative, initGuestIfNeeded } = await import(
    './lib/auth/session.svelte'
  )
  const { initDb } = await import('./lib/db')

  await restoreNativeSession()
  prepareDbForUser()
  await prepareDbForUserNative()
  initGuestIfNeeded()
  await initDb()

  const { initSync, syncAll } = await import('./lib/sync/sync')
  const { syncStatus, updatePendingCount } = await import('./lib/sync/status.svelte')
  initSync()
  updatePendingCount()

  // модули settings/dayState могли загрузиться раньше initDb (восстановленная
  // сессия) — теперь, когда БД готова, перечитываем сохранённое состояние
  const { getSetting, theme } = await import('./lib/settings.svelte')
  if (getSetting('theme') === 'light') theme.value = 'light'
  const { refreshDayState } = await import('./lib/dayState.svelte')
  refreshDayState()

  // нативные уведомления/будильники: каналы + первое планирование + перепланирование на pull-sync
  const { initNotifications, __setLocalNotificationsForTest, rescheduleReminders } = await import(
    './lib/notifications'
  )
  initNotifications()

  // отладочный доступ к состоянию синка и очереди (e2e-дым, поддержка)
  const { getDb } = await import('./lib/db')
  const { pb } = await import('./lib/auth/pb')
  ;(window as unknown as { __sf: unknown }).__sf = {
    syncStatus,
    getDb,
    pb,
    syncAll,
    __setLocalNotificationsForTest,
    rescheduleReminders,
  }

  mount(App, { target: document.getElementById('app')! })
}

start().catch(showErrorOverlay)
