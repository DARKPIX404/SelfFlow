import type { Routine } from './types'
import { getDb } from './db'
import { todayLocal } from './db/id'
import { routineCompletions } from './db/repositories'

/**
 * Расчёт вхождений рутин на дату (клиентская математика повторов).
 * WEEKLY повторяется в день недели, соответствующий дате создания рутины.
 */

export interface RoutineOccurrence {
  routine: Routine
  /** YYYY-MM-DD */
  date: string
  done: boolean
}

export function occursOn(routine: Routine, dateStr: string): boolean {
  if (!routine.is_active) return false
  const rule = routine.recurrence_rule ?? 'NONE'
  if (rule === 'NONE') {
    // одноразовая: в день старта (created)
    return routine.created.slice(0, 10) === dateStr
  }
  if (routine.created.slice(0, 10) > dateStr) return false
  const date = new Date(dateStr + 'T12:00:00')
  const dow = date.getDay() // 0=вс
  if (rule === 'DAILY') return true
  if (rule === 'WEEKDAYS') return dow >= 1 && dow <= 5
  // WEEKLY — день недели из даты создания
  const createdDow = new Date(routine.created.slice(0, 10) + 'T12:00:00').getDay()
  return dow === createdDow
}

export function occurrencesForDate(routines: Routine[], dateStr: string, owner: string): RoutineOccurrence[] {
  const doneSet = completionSet(owner, dateStr)
  return routines
    .filter((r) => occursOn(r, dateStr))
    .map((r) => ({ routine: r, date: dateStr, done: doneSet.has(r.id) }))
    .sort((a, b) => (a.routine.start_time ?? '99:99').localeCompare(b.routine.start_time ?? '99:99'))
}

// --- Трекинг выполнения (синкаемая сущность routine_completions, фаза 3) ---

export function completionSet(owner: string, dateStr: string): Set<string> {
  return new Set(
    getDb()
      .query<{ routine_id: string }>(
        'SELECT routine_id FROM routine_completions WHERE owner = ? AND date = ? AND deleted = 0',
        [owner, dateStr],
      )
      .map((r) => r.routine_id),
  )
}

export function setCompletion(owner: string, routineId: string, dateStr: string, done: boolean): void {
  const existing = getDb().queryOne<{ id: string }>(
    'SELECT id FROM routine_completions WHERE owner = ? AND routine_id = ? AND date = ? AND deleted = 0',
    [owner, routineId, dateStr],
  )
  if (done) {
    if (!existing) routineCompletions.create(owner, { routine_id: routineId, date: dateStr })
  } else if (existing) {
    routineCompletions.remove(existing.id)
  }
}

export function todayOccurrences(routines: Routine[], owner: string): RoutineOccurrence[] {
  return occurrencesForDate(routines, todayLocal(), owner)
}
