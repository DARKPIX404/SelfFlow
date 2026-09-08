import { getDb } from './db'
import { habitLogs } from './db/repositories'
import type { HabitLog } from './types'

/**
 * Логика привычек: отметки (habit_logs), streak, прогресс недели,
 * статистика месяца и данные для Heatmap.
 */

const fmt = (d: Date): string => {
  const off = d.getTimezoneOffset()
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10)
}

export function logsForHabit(owner: string, habitId: string): HabitLog[] {
  return getDb().query<HabitLog>(
    'SELECT * FROM habit_logs WHERE owner = ? AND habit_id = ? AND deleted = 0 ORDER BY date',
    [owner, habitId],
  )
}

/** активные отметки привычки — множество дат 'YYYY-MM-DD' */
export function logDateSet(owner: string, habitId: string): Set<string> {
  return new Set(logsForHabit(owner, habitId).map((l) => l.date))
}

/**
 * Переключение отметки за дату: создаёт habit_log либо мягко удаляет
 * существующую запись (повторный тап снимает отметку).
 */
export function toggleLog(owner: string, habitId: string, dateStr: string): void {
  const existing = getDb().queryOne<{ id: string }>(
    'SELECT id FROM habit_logs WHERE owner = ? AND habit_id = ? AND date = ? AND deleted = 0',
    [owner, habitId, dateStr],
  )
  if (existing) {
    habitLogs.remove(existing.id)
  } else {
    habitLogs.create(owner, { habit_id: habitId, date: dateStr })
  }
}

/**
 * Текущий streak: сколько дней подряд до сегодня (включительно).
 * Если сегодня ещё не отмечено — считаем от вчера (день ещё идёт).
 */
export function currentStreak(dates: Set<string>, today: string): number {
  const d = new Date(today + 'T12:00:00')
  if (!dates.has(today)) d.setDate(d.getDate() - 1)
  let n = 0
  while (dates.has(fmt(d))) {
    n++
    d.setDate(d.getDate() - 1)
  }
  return n
}

/** Лучший streak за всю историю отметок. */
export function bestStreak(dates: Set<string>): number {
  if (dates.size === 0) return 0
  const sorted = [...dates].sort()
  let best = 1
  let cur = 1
  for (let i = 1; i < sorted.length; i++) {
    const prev = new Date(sorted[i - 1] + 'T12:00:00')
    prev.setDate(prev.getDate() + 1)
    if (fmt(prev) === sorted[i]) {
      cur++
      if (cur > best) best = cur
    } else {
      cur = 1
    }
  }
  return best
}

/** Отметки текущей недели (пн–вс, локальное время). */
export function weekProgress(dates: Set<string>, today: string): number {
  const d = new Date(today + 'T12:00:00')
  const monday = new Date(d)
  monday.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  let n = 0
  for (let cur = new Date(monday); cur <= sunday; cur.setDate(cur.getDate() + 1)) {
    if (dates.has(fmt(cur))) n++
  }
  return n
}

/** Процент выполнения за текущий месяц: отметки / прошедшие дни месяца. */
export function monthPercent(dates: Set<string>, today: string): number {
  const [, m] = today.split('-').map(Number)
  const elapsed = Number(today.slice(8, 10))
  if (elapsed === 0) return 0
  const prefix = today.slice(0, 7)
  let n = 0
  for (const d of dates) {
    if (d.slice(0, 7) === prefix) n++
  }
  return Math.round((n / elapsed) * 100)
}

/**
 * Данные для Heatmap: N колонок-недель (пн–вс), с конца текущей недели
 * назад. Значения 0/1 — день отмечен или нет; дни в будущем — 0.
 */
export function heatmapWeeks(dates: Set<string>, today: string, weeksCount = 12): number[][] {
  const now = new Date(today + 'T12:00:00')
  const monday = new Date(now)
  monday.setDate(now.getDate() - ((now.getDay() + 6) % 7))
  const weeks: number[][] = []
  for (let w = weeksCount - 1; w >= 0; w--) {
    const col: number[] = []
    for (let day = 0; day < 7; day++) {
      const d = new Date(monday)
      d.setDate(monday.getDate() - w * 7 + day)
      col.push(d.getTime() <= now.getTime() && dates.has(fmt(d)) ? 1 : 0)
    }
    weeks.push(col)
  }
  return weeks
}

export const HABIT_COLORS = ['#c9403b', '#7ba05b', '#d9a441', '#5b8ca0', '#9c7ba0', '#c97b5b']
