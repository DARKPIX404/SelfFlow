import { session } from './auth/session.svelte'
import { routines, tasks, notes, habits, goals } from './db/repositories'
import { todayLocal } from './db/id'
import { todayOccurrences, type RoutineOccurrence } from './routines'
import type { Routine, Task, Habit, Goal } from './types'

/**
 * Общий реактивный снапшот данных за «сегодня».
 * Перечитывается по событиям мутаций/синка.
 */

export interface DayState {
  /** инкрементится на каждый перезагруз — якорь реактивности для $derived */
  version: number
  routines: Routine[]
  occurrences: RoutineOccurrence[]
  tasksDue: Task[]
  tasksNoDate: Task[]
  taskCounts: { total: number; done: number }
  noteCount: number
  habits: Habit[]
  goals: Goal[]
}

let versionCounter = 0

const EMPTY: DayState = {
  version: 0,
  routines: [],
  occurrences: [],
  tasksDue: [],
  tasksNoDate: [],
  taskCounts: { total: 0, done: 0 },
  noteCount: 0,
  habits: [],
  goals: [],
}

function load(): DayState {
  const user = session.user
  if (!user) {
    return { ...EMPTY, version: 0 }
  }
  let allRoutines: Routine[]
  let allTasks: Task[]
  let noteCount: number
  let allHabits: Habit[]
  let allGoals: Goal[]
  try {
    allRoutines = routines.list(user.id)
    allTasks = tasks.list(user.id)
    noteCount = notes.list(user.id).length
    allHabits = habits.list(user.id)
    allGoals = goals.list(user.id)
  } catch {
    // db ещё не инициализирован (модуль загрузился раньше initDb) —
    // перечитаем после старта, см. main.ts
    return { ...EMPTY, version: 0 }
  }
  const today = todayLocal()

  const open = allTasks.filter((t) => t.status !== 'DONE')
  const tasksDue = open
    .filter((t) => t.due_date?.slice(0, 10) === today)
    .sort((a, b) => (a.due_date ?? '').localeCompare(b.due_date ?? ''))
    .slice(0, 5)
  const tasksNoDate = open.filter((t) => !t.due_date).slice(0, 5)

  return {
    version: ++versionCounter,
    routines: allRoutines,
    occurrences: todayOccurrences(allRoutines, user.id),
    tasksDue,
    tasksNoDate,
    taskCounts: { total: allTasks.length, done: allTasks.filter((t) => t.status === 'DONE').length },
    noteCount,
    habits: allHabits,
    goals: allGoals,
  }
}

export const dayState = $state<DayState>({ ...EMPTY })
refreshDayState()

export function refreshDayState(): void {
  const next = load()
  dayState.version = next.version
  dayState.routines = next.routines
  dayState.occurrences = next.occurrences
  dayState.tasksDue = next.tasksDue
  dayState.tasksNoDate = next.tasksNoDate
  dayState.taskCounts = next.taskCounts
  dayState.noteCount = next.noteCount
  dayState.habits = next.habits
  dayState.goals = next.goals
}

if (typeof window !== 'undefined') {
  window.addEventListener('selfflow:mutated', refreshDayState)
  window.addEventListener('selfflow:synced', refreshDayState)
}
