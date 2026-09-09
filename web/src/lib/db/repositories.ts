import { getDb } from './index'
import { newId, nowIso } from './id'
import type { BaseEntity } from '../types'
import type { SqlParam } from './driver'

type Row = Record<string, SqlParam>

function normalize(value: unknown): SqlParam {
  if (value === undefined || value === null) return null
  if (typeof value === 'boolean') return value ? 1 : 0
  if (typeof value === 'number') return value
  if (typeof value === 'string') return value
  return String(value)
}

export class Repository<T extends BaseEntity> {
  constructor(
    readonly table: string,
    /** поля данных (без id/owner/created/updated/deleted) */
    readonly fields: string[],
  ) {}

  private insertOrReplace(row: Row): void {
    const db = getDb()
    const cols = Object.keys(row)
    const placeholders = cols.map(() => '?').join(', ')
    const updateSets = cols.filter((c) => c !== 'id').map((c) => `${c}=excluded.${c}`)
    db.run(
      `INSERT INTO ${this.table} (${cols.join(', ')}) VALUES (${placeholders}) ` +
        `ON CONFLICT(id) DO UPDATE SET ${updateSets.join(', ')}`,
      cols.map((c) => row[c]),
    )
  }

  private enqueue(entityId: string, op: 'upsert' | 'delete'): void {
    getDb().run('INSERT INTO _sync_queue (collection, entity_id, op, created_at) VALUES (?, ?, ?, ?)', [
      this.table,
      entityId,
      op,
      nowIso(),
    ])
    if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  list(owner: string): T[] {
    return getDb().query<T>(
      `SELECT * FROM ${this.table} WHERE owner = ? AND deleted = 0 ORDER BY created DESC`,
      [owner],
    )
  }

  /** включая tombstone — нужно для push */
  getRaw(id: string): T | undefined {
    return getDb().queryOne<T>(`SELECT * FROM ${this.table} WHERE id = ?`, [id])
  }

  create(owner: string, data: Partial<T>): T {
    const db = getDb()
    const id = newId()
    const now = nowIso()
    const row: Row = { id, owner, created: now, updated: now, deleted: 0 }
    for (const f of this.fields) row[f] = normalize(data[f as keyof T])
    db.transaction(() => {
      this.insertOrReplace(row)
      this.enqueue(id, 'upsert')
    })
    return row as unknown as T
  }

  update(id: string, patch: Partial<T>): void {
    const db = getDb()
    const sets: string[] = ['updated = ?']
    const params: SqlParam[] = [nowIso()]
    for (const f of this.fields) {
      if (f in patch) {
        sets.push(`${f} = ?`)
        params.push(normalize(patch[f as keyof T]))
      }
    }
    db.transaction(() => {
      db.run(`UPDATE ${this.table} SET ${sets.join(', ')} WHERE id = ?`, [...params, id])
      this.enqueue(id, 'upsert')
    })
  }

  /** мягкое удаление локально + жёсткое на сервере */
  remove(id: string): void {
    const db = getDb()
    db.transaction(() => {
      db.run(`UPDATE ${this.table} SET deleted = 1, updated = ? WHERE id = ?`, [nowIso(), id])
      this.enqueue(id, 'delete')
    })
  }

  /**
   * Запись из pull. Last-write-wins: побеждает более поздний server `updated`.
   */
  upsertFromServer(record: Record<string, unknown>): void {
    const existing = this.getRaw(String(record.id))
    const serverUpdated = String(record.updated ?? '')
    if (existing && existing.updated >= serverUpdated) return
    const row: Row = {
      id: String(record.id),
      owner: String(record.owner ?? ''),
      created: String(record.created ?? serverUpdated),
      updated: serverUpdated,
      deleted: record.deleted ? 1 : 0,
    }
    for (const f of this.fields) row[f] = normalize(record[f])
    this.insertOrReplace(row)
  }

  /** поля для отправки на сервер (без служебных) */
  toServerPayload(row: T): Record<string, unknown> {
    const payload: Record<string, unknown> = {}
    for (const f of this.fields) {
      const v = normalize(row[f as keyof T])
      payload[f] = typeof v === 'number' && (f === 'is_active') ? v === 1 : v
    }
    return payload
  }
}

import type {
  Routine,
  Task,
  Note,
  NoteTag,
  Habit,
  HabitLog,
  Goal,
  FocusSession,
  Setting,
  RoutineTemplate,
  RoutineCompletion,
} from '../types'

export const routines = new Repository<Routine>('routines', [
  'title',
  'description',
  'start_time',
  'end_time',
  'recurrence_rule',
  'category',
  'notification_text',
  'is_active',
])
export const tasks = new Repository<Task>('tasks', [
  'title',
  'description',
  'status',
  'priority',
  'due_date',
  'routine_id',
  'goal_id',
  'completed_at',
])
export const notes = new Repository<Note>('notes', ['title', 'content'])
export const noteTags = new Repository<NoteTag>('note_tags', ['note_id', 'tag'])
export const habits = new Repository<Habit>('habits', [
  'title',
  'color',
  'icon',
  'target_per_week',
  'sort',
  'reminder_time',
])
export const habitLogs = new Repository<HabitLog>('habit_logs', ['habit_id', 'date'])
export const goals = new Repository<Goal>('goals', ['title', 'deadline', 'note', 'sort'])
export const focusSessions = new Repository<FocusSession>('focus_sessions', [
  'started_at',
  'minutes',
  'task_id',
  'routine_id',
])
export const settings = new Repository<Setting>('settings', ['key', 'value'])
export const routineTemplates = new Repository<RoutineTemplate>('routine_templates', [
  'title',
  'items_json',
])
export const routineCompletions = new Repository<RoutineCompletion>('routine_completions', [
  'routine_id',
  'date',
])

export const repositories: Record<string, Repository<BaseEntity>> = {
  routines: routines as Repository<BaseEntity>,
  tasks: tasks as Repository<BaseEntity>,
  notes: notes as Repository<BaseEntity>,
  note_tags: noteTags as Repository<BaseEntity>,
  habits: habits as Repository<BaseEntity>,
  habit_logs: habitLogs as Repository<BaseEntity>,
  goals: goals as Repository<BaseEntity>,
  focus_sessions: focusSessions as Repository<BaseEntity>,
  settings: settings as Repository<BaseEntity>,
  routine_templates: routineTemplates as Repository<BaseEntity>,
  routine_completions: routineCompletions as Repository<BaseEntity>,
}
