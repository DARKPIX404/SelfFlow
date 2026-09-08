import { Capacitor } from '@capacitor/core'
import { LocalNotifications, type Channel, type LocalNotificationSchema } from '@capacitor/local-notifications'
import { getDb } from '../db'
import { routines, habits, tasks } from '../db/repositories'
import { session } from '../auth/session.svelte'
import { getSetting } from '../settings.svelte'
import { planAll, type PlannedNotification } from './planner'
import { AlarmOverlay } from '../native/alarmOverlay'

/**
 * Планирование нативных уведомлений и будильников.
 *
 * - Рутины/привычки/задачи/digest — @capacitor/local-notifications, каналы
 *   routine_reminders (звук notification_soft) и alarm_channel (morning_light).
 *   Плагин сам восстанавливает их после перезагрузки устройства.
 * - Wake/sleep-будильники «поверх окон» — кастомный AlarmOverlayPlugin
 *   (AlarmManager setRepeating + foreground-сервис + OverlayActivity);
 *   расписание кэшируется нативно и перепланируется BootReceiver'ом.
 *
 * Всё идемпотентно: отменяем все свои уведомления по pending-списку и
 * планируем заново. В браузере (и в тестах без мока) — no-op.
 */

const CHANNELS: Channel[] = [
  {
    id: 'routine_reminders',
    name: 'Напоминания распорядка',
    description: 'Рутины, привычки и задачи',
    importance: 5, // IMPORTANCE_HIGH
    sound: 'notification_soft',
    vibration: true,
    visibility: 1,
  },
  {
    id: 'alarm_channel',
    name: 'Утренний дайджест',
    description: 'Дайджест дня по времени подъёма',
    importance: 5,
    sound: 'morning_light',
    vibration: true,
    visibility: 1,
  },
]

// --- тестовый шов: e2e подменяет плагин моком ---

interface LocalNotificationsLike {
  schedule(o: { notifications: LocalNotificationSchema[] }): Promise<unknown>
  cancel(o: { notifications: { id: number }[] }): Promise<void>
  getPending(): Promise<{ notifications: { id: number }[] }>
  createChannel(c: Channel): Promise<void>
}

let lnOverride: LocalNotificationsLike | null = null
export function __setLocalNotificationsForTest(mock: LocalNotificationsLike | null): void {
  lnOverride = mock
}

function ln(): LocalNotificationsLike | null {
  if (lnOverride) return lnOverride
  return Capacitor.isNativePlatform() ? LocalNotifications : null
}

let channelsReady = false

async function ensureChannels(): Promise<void> {
  if (channelsReady) return
  const plugin = ln()
  if (!plugin) return
  for (const channel of CHANNELS) {
    await plugin.createChannel(channel).catch(() => {})
  }
  channelsReady = true
}

function toSchema(p: PlannedNotification): LocalNotificationSchema {
  return {
    id: p.id,
    title: p.title,
    body: p.body,
    channelId: p.channelId,
    schedule: p.every ? { at: p.at, every: p.every } : { at: p.at },
    extra: p.extra,
  }
}

function gatherData() {
  const owner = session.user?.id
  if (!owner) return null
  const db = getDb()
  const allRoutines = routines.list(owner)
  const allHabits = habits.list(owner)
  const allTasks = tasks.list(owner)
  return {
    routines: allRoutines,
    habits: allHabits,
    tasks: allTasks,
    settings: {
      alarmEnabled: getSetting('alarm_enabled') === '1',
      wakeTime: getSetting('wake_time') ?? '07:00',
    },
  }
}

let reminderTimer: ReturnType<typeof setTimeout> | null = null

/** Перепланировать уведомления рутин/привычек/задач/digest (debounced). */
export function requestRescheduleReminders(): void {
  if (reminderTimer) clearTimeout(reminderTimer)
  reminderTimer = setTimeout(() => void rescheduleReminders(), 500)
}

export async function rescheduleReminders(): Promise<void> {
  const plugin = ln()
  if (!plugin) return
  try {
    const data = gatherData()
    if (!data) return
    await ensureChannels()
    const planned = planAll(data.routines, data.habits, data.tasks, data.settings, new Date())
    // идемпотентность: снимаем всё своё (приложение — единственный планировщик)
    const pending = await plugin.getPending()
    if (pending.notifications.length > 0) {
      await plugin.cancel({ notifications: pending.notifications.map((n) => ({ id: n.id })) })
    }
    if (planned.length > 0) {
      await plugin.schedule({ notifications: planned.map(toSchema) })
    }
  } catch (e) {
    console.error('[notifications] reschedule failed', e)
  }
}

/** Перепланировать нативные будильники wake/sleep (оверлей) + кэш для boot. */
export async function rescheduleAlarms(): Promise<void> {
  if (!Capacitor.isNativePlatform() && !lnOverride) return
  try {
    const data = gatherData()
    if (!data) return
    const sound = getSetting('alarm_sound') ?? 'morning_light'
    const repeating = data.settings.alarmEnabled
      ? [
          {
            id: 'wake',
            hour: Number(data.settings.wakeTime.slice(0, 2)),
            minute: Number(data.settings.wakeTime.slice(3, 5)),
            title: 'Подъём',
            text: 'Начало дня — время вставать',
            sound,
          },
          {
            id: 'sleep',
            hour: Number(getSetting('sleep_time')?.slice(0, 2) ?? '23'),
            minute: Number(getSetting('sleep_time')?.slice(3, 5) ?? '0'),
            title: 'Отбой',
            text: 'Время заканчивать день',
            sound,
          },
        ]
      : []
    await AlarmOverlay.rescheduleAll({ repeating, oneShot: [] })
  } catch (e) {
    console.error('[notifications] rescheduleAlarms failed', e)
  }
}

/**
 * Инициализация при старте приложения: каналы, разрешения, первое
 * планирование, перепланирование после каждого pull-sync.
 */
export function initNotifications(): void {
  if (!Capacitor.isNativePlatform() && !lnOverride) return
  void rescheduleReminders()
  void rescheduleAlarms()
  window.addEventListener('selfflow:synced', () => {
    requestRescheduleReminders()
  })
}
