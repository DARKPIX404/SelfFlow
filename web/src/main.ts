import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import { prepareDbForUser, restoreNativeSession, prepareDbForUserNative } from './lib/auth/session.svelte'
import { initDb } from './lib/db'
import { initSync } from './lib/sync/sync'
import { syncStatus, updatePendingCount } from './lib/sync/status.svelte'

await restoreNativeSession()
prepareDbForUser()
await prepareDbForUserNative()
await initDb()
initSync()
updatePendingCount()
// модули settings/dayState могли загрузиться раньше initDb (восстановленная
// сессия) — теперь, когда БД готова, перечитываем сохранённое состояние
import { getSetting, theme } from './lib/settings.svelte'
if (getSetting('theme') === 'light') theme.value = 'light'
import { refreshDayState } from './lib/dayState.svelte'
refreshDayState()

// нативные уведомления/будильники: каналы + первое планирование + перепланирование на pull-sync
import { initNotifications } from './lib/notifications'
initNotifications()

// отладочный доступ к состоянию синка и очереди (e2e-дым, поддержка)
import { getDb } from './lib/db'
import { pb } from './lib/auth/pb'
import { syncAll } from './lib/sync/sync'
import { __setLocalNotificationsForTest, rescheduleReminders } from './lib/notifications'
;(window as unknown as { __sf: unknown }).__sf = {
  syncStatus,
  getDb,
  pb,
  syncAll,
  __setLocalNotificationsForTest,
  rescheduleReminders,
}

const app = mount(App, {
  target: document.getElementById('app')!,
})

export default app
