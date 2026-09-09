import { Capacitor } from '@capacitor/core'
import { LocalNotifications, type Channel, type LocalNotificationSchema } from '@capacitor/local-notifications'
import { getDb } from '../db'
import { routines, habits, tasks } from '../db/repositories'
import { session } from '../auth/session.svelte'
import { getSetting, alarmSoundKey, notificationSoundKey } from '../settings.svelte'
import { planAll, type PlannedNotification } from './planner'
import { AlarmOverlay } from '../native/alarmOverlay'

/**
 * Планирование нативных уведомлений и будильников.
 *
 * - Рутины/привычки/задачи/digest — @capacitor/local-notifications, каналы
 *   под каждый выбранный звук (id содержит ключ звука: звук канала в Android
 *   нельзя сменить после создания, поэтому меняем звук = создаём канал с новым
 *   id и удаляем старые). Плагин сам восстанавливает уведомления после
 *   перезагрузки устройства.
 * - Wake/sleep-будильники «поверх окон» — кастомный AlarmOverlayPlugin
 *   (AlarmManager exact + foreground-сервис + full-screen intent;
 *   расписание кэшируется нативно и перепланируется BootReceiver'ом).
 *
 * Всё идемпотентно: отменяем все свои уведомления по pending-списку и
 * планируем заново. В браузере (и в тестах без мока) — no-op.
 */

// --- тестовый шов: e2e подменяет плагин моком ---

interface LocalNotificationsLike {
  schedule(o: { notifications: LocalNotificationSchema[] }): Promise<unknown>
  cancel(o: { notifications: { id: number }[] }): Promise<void>
  getPending(): Promise<{ notifications: { id: number }[] }>
  createChannel(c: Channel): Promise<void>
  deleteChannel?(o: { id: string }): Promise<void>
  listChannels?(): Promise<{ channels: Channel[] }>
  requestPermissions?(): Promise<{ display: string }>
  requestExactNotificationSetting?(): Promise<void>
}

let lnOverride: LocalNotificationsLike | null = null
export function __setLocalNotificationsForTest(mock: LocalNotificationsLike | null): void {
  lnOverride = mock
}

function ln(): LocalNotificationsLike | null {
  if (lnOverride) return lnOverride
  return Capacitor.isNativePlatform() ? LocalNotifications : null
}

// --- каналы: id зависит от звука, старые удаляем ---

/** id канала напоминаний под текущий звук уведомлений */
export function reminderChannelId(): string {
  return `routine_reminders__${notificationSoundKey()}`
}

/** id канала дайджеста под текущий звук будильника */
export function alarmChannelId(): string {
  return `alarm_channel__${alarmSoundKey()}`
}

let channelsReadyKey = ''

async function ensureChannels(): Promise<void> {
  const plugin = ln()
  if (!plugin) return
  const reminderId = reminderChannelId()
  const alarmId = alarmChannelId()
  const readyKey = `${reminderId}|${alarmId}`
  if (channelsReadyKey === readyKey) return

  const reminderSound = notificationSoundKey()
  const alarmSound = alarmSoundKey()
  const channels: Channel[] = [
    {
      id: reminderId,
      name: 'Напоминания распорядка',
      description: 'Рутины, привычки и задачи',
      importance: 5, // IMPORTANCE_HIGH
      sound: reminderSound === 'system' ? undefined : reminderSound,
      vibration: true,
      visibility: 1,
    },
    {
      id: alarmId,
      name: 'Утренний дайджест',
      description: 'Дайджест дня по времени подъёма',
      importance: 5,
      sound: alarmSound === 'system' ? undefined : alarmSound,
      vibration: true,
      visibility: 1,
    },
  ]
  for (const channel of channels) {
    await plugin.createChannel(channel).catch((e) => console.error('[notifications] createChannel failed', e))
  }
  // чистим наши каналы с другим звуком (их звук уже не сменить)
  const listed = await plugin.listChannels?.().catch(() => null)
  if (listed) {
    const current = new Set([reminderId, alarmId])
    for (const ch of listed.channels ?? []) {
      if (
        typeof ch.id === 'string' &&
        (ch.id.startsWith('routine_reminders__') || ch.id.startsWith('alarm_channel__')) &&
        !current.has(ch.id)
      ) {
        await plugin.deleteChannel?.({ id: ch.id }).catch(() => {})
      }
    }
  }
  channelsReadyKey = readyKey
}

function toSchema(p: PlannedNotification): LocalNotificationSchema {
  return {
    id: p.id,
    title: p.title,
    body: p.body,
    channelId: p.channelId === 'alarm_channel' ? alarmChannelId() : reminderChannelId(),
    schedule: p.every ? { at: p.at, every: p.every, allowWhileIdle: true } : { at: p.at, allowWhileIdle: true },
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
    const sound = alarmSoundKey()
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
 * Инициализация при старте приложения: разрешения (Android 13+ молча не
 * показывает уведомления без runtime-разрешения), каналы, первое планирование,
 * перепланирование после каждого pull-sync.
 */
export function initNotifications(): void {
  const plugin = ln()
  if (!plugin) return
  // POST_NOTIFICATIONS (API 33+): без запроса уведомления просто не приходят
  plugin.requestPermissions?.().catch((e) => console.error('[notifications] requestPermissions failed', e))
  // точные алярмы (API 31+): планировщик сам спросит при необходимости
  plugin.requestExactNotificationSetting?.().catch(() => {})
  void rescheduleReminders()
  void rescheduleAlarms()
  window.addEventListener('selfflow:synced', () => {
    channelsReadyKey = '' // звук мог смениться на другом устройстве
    requestRescheduleReminders()
  })
}
