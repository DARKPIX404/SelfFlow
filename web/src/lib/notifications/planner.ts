import type { Habit, Routine, Task } from '../types'
import { occursOn } from '../routines'

/**
 * Чистый расчёт локальных уведомлений-напоминаний.
 * Никаких вызовов плагинов — вход: данные БД + настройки + текущее время,
 * выход: список PlannedNotification. Проверяется тестами без устройства.
 */

export interface PlannedNotification {
  /** стабильный числовой id (для отмены/замены по id) */
  id: number
  title: string
  body: string
  at: Date
  /** 'day' — ежедневный повтор с момента at */
  every?: 'day'
  channelId: 'routine_reminders' | 'alarm_channel'
  extra: Record<string, unknown>
}

export interface AlarmSettings {
  alarmEnabled: boolean
  /** 'HH:MM' */
  wakeTime: string
}

// Числовые диапазоны id по видам (31-битное пространство capacitor):
const ROUTINE_RANGE = 1_000_000_000
const HABIT_BASE = 1_000_000_000
const HABIT_RANGE = 500_000_000
const TASK_BASE = 1_500_000_000
const TASK_RANGE = 500_000_000
const DIGEST_ID = 900_000_000

/** FNV-1a → неотрицательное число, пригодное для id уведомления */
function hashId(key: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < key.length; i++) {
    h ^= key.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return (h >>> 0) & 0x3fffffff
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date.getTime())
  d.setDate(d.getDate() + days)
  return d
}

/** YYYY-MM-DD в локальной зоне */
function dateKey(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function atTime(base: Date, hhmm: string): Date {
  const d = new Date(base.getTime())
  const [h, m] = hhmm.split(':').map(Number)
  d.setHours(h, m, 0, 0)
  return d
}

const HORIZON_DAYS = 14

/**
 * Рутины: ближайшие occurrence'ы на горизонте 14 дней.
 * NONE — одно на start_time дня старта; DAILY/WEEKDAYS/WEEKLY — по occursOn.
 */
export function planRoutines(routines: Routine[], now: Date): PlannedNotification[] {
  const out: PlannedNotification[] = []
  for (const r of routines) {
    if (!r.is_active || !r.start_time) continue
    const hhmm = r.start_time.slice(11, 16)
    if (!/^\d{2}:\d{2}$/.test(hhmm)) continue

    const dates: string[] = []
    if ((r.recurrence_rule ?? 'NONE') === 'NONE') {
      dates.push(r.created.slice(0, 10))
    } else {
      for (let i = 0; i < HORIZON_DAYS; i++) {
        const key = dateKey(addDays(now, i))
        if (occursOn(r, key)) dates.push(key)
      }
    }

    for (const key of dates) {
      const at = atTime(new Date(`${key}T00:00:00`), hhmm)
      if (at.getTime() <= now.getTime()) continue
      out.push({
        id: hashId(`routine:${r.id}:${key}`) % ROUTINE_RANGE,
        title: r.notification_text || r.title,
        body: r.title,
        at,
        channelId: 'routine_reminders',
        extra: { kind: 'routine', routineId: r.id, date: key },
      })
    }
  }
  return out
}

/** Привычки с reminder_time: ежедневное уведомление в это время */
export function planHabits(habits: Habit[], now: Date): PlannedNotification[] {
  const out: PlannedNotification[] = []
  for (const h of habits) {
    if (!h.reminder_time || !/^\d{2}:\d{2}$/.test(h.reminder_time)) continue
    let at = atTime(now, h.reminder_time)
    if (at.getTime() <= now.getTime()) at = addDays(at, 1)
    out.push({
      id: HABIT_BASE + (hashId(`habit:${h.id}`) % HABIT_RANGE),
      title: h.title,
      body: 'Не забудьте отметить привычку',
      at,
      every: 'day',
      channelId: 'routine_reminders',
      extra: { kind: 'habit', habitId: h.id },
    })
  }
  return out
}

/** Задачи с due_date (не DONE): 09:00 дня дедлайна */
export function planTasks(tasks: Task[], now: Date): PlannedNotification[] {
  const out: PlannedNotification[] = []
  for (const t of tasks) {
    if (!t.due_date || t.status === 'DONE') continue
    // due_date хранится как 'YYYY-MM-DD 00:00:00.000Z' — берём только дату,
    // иначе `${due_date}T00:00:00` даёт Invalid Date
    const at = atTime(new Date(`${t.due_date.slice(0, 10)}T00:00:00`), '09:00')
    if (at.getTime() <= now.getTime()) continue
    out.push({
      id: TASK_BASE + (hashId(`task:${t.id}`) % TASK_RANGE),
      title: 'Дедлайн сегодня',
      body: t.title,
      at,
      channelId: 'routine_reminders',
      extra: { kind: 'task', taskId: t.id },
    })
  }
  return out
}

/**
 * Digest по wake_time: «Сегодня: N пунктов распорядка, M задач».
 * Числа — на момент планирования.
 */
export function planDigest(
  routines: Routine[],
  tasks: Task[],
  settings: AlarmSettings,
  now: Date,
): PlannedNotification[] {
  if (!settings.alarmEnabled || !/^\d{2}:\d{2}$/.test(settings.wakeTime)) return []
  let at = atTime(now, settings.wakeTime)
  if (at.getTime() <= now.getTime()) at = addDays(at, 1)

  const today = dateKey(now)
  const routineCount = routines.filter((r) => r.is_active && occursOn(r, today)).length
  const openTasks = tasks.filter((t) => t.status !== 'DONE').length

  return [
    {
      id: DIGEST_ID,
      title: 'Сегодня',
      body: `Сегодня: ${routineCount} пунктов распорядка, ${openTasks} задач`,
      at,
      every: 'day',
      channelId: 'alarm_channel',
      extra: { kind: 'digest' },
    },
  ]
}

export function planAll(
  routines: Routine[],
  habits: Habit[],
  tasks: Task[],
  settings: AlarmSettings,
  now: Date,
): PlannedNotification[] {
  return [
    ...planRoutines(routines, now),
    ...planHabits(habits, now),
    ...planTasks(tasks, now),
    ...planDigest(routines, tasks, settings, now),
  ]
}
