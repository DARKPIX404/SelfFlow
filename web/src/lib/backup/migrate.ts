import { BACKUP_VERSION, emptyBackupData, type BackupFile, type BackupRow } from './format'

/**
 * Миграция бэкапа SelfFlow v1 (нативное Android-приложение, Gson).
 * Формат v1: `{ "routines": [...], "tasks": [...], "notes": [...] }`
 * без version; camelCase-поля, время — epoch millis (Long), notes.tags —
 * массив строк, id — числа Long.
 *
 * Функция чистая (без обращения к БД): на выходе тот же BackupFile v2,
 * что и у экспорта v2 (связи сохраняются через старые id — их маппинг на
 * новые 15-символьные происходит в applyBackup наравне с v2-импортом).
 */

const RECURRENCE_RULES = new Set(['NONE', 'DAILY', 'WEEKDAYS', 'WEEKLY'])
const STATUSES = new Set(['TODO', 'IN_PROGRESS', 'DONE'])
const PRIORITIES = new Set(['LOW', 'MEDIUM', 'HIGH'])

/** epoch millis → "YYYY-MM-DD HH:mm:ss.sssZ" (UTC, формат сервера PocketBase) */
export function epochToIso(ms: unknown): string | null {
  const n = typeof ms === 'number' ? ms : Number(ms)
  if (!Number.isFinite(n) || n <= 0) return null
  return new Date(n).toISOString().replace('T', ' ')
}

function str(v: unknown): string {
  return typeof v === 'string' ? v : v == null ? '' : String(v)
}

function opt(v: unknown): string | null {
  const s = str(v).trim()
  return s === '' ? null : s
}

function enumOr<T extends string>(v: unknown, allowed: Set<string>, fallback: T): T {
  const s = str(v)
  return (allowed.has(s) ? s : fallback) as T
}

interface V1Shape {
  routines?: unknown[]
  tasks?: unknown[]
  notes?: unknown[]
}

export function isV1Backup(json: unknown): json is V1Shape {
  if (typeof json !== 'object' || json === null) return false
  const o = json as Record<string, unknown>
  if ('version' in o || 'data' in o) return false
  return Array.isArray(o.routines) || Array.isArray(o.tasks) || Array.isArray(o.notes)
}

export function migrateV1(json: V1Shape): BackupFile {
  const data = emptyBackupData()
  const now = new Date().toISOString().replace('T', ' ')

  for (const raw of json.routines ?? []) {
    const r = (raw ?? {}) as Record<string, unknown>
    data.routines.push({
      id: str(r.id),
      title: str(r.title) || 'Рутина',
      description: opt(r.description),
      // в v1 startTime обязателен; при потере — ставим «сейчас», чтобы не ломать планировщик
      start_time: epochToIso(r.startTime) ?? now,
      end_time: epochToIso(r.endTime),
      recurrence_rule: enumOr(r.recurrenceRule, RECURRENCE_RULES, 'NONE'),
      category: opt(r.category),
      notification_text: opt(r.notificationText),
      is_active: r.isActive === false ? 0 : 1,
      created: epochToIso(r.startTime) ?? now,
      updated: now,
      deleted: 0,
    } satisfies BackupRow)
  }

  for (const raw of json.tasks ?? []) {
    const t = (raw ?? {}) as Record<string, unknown>
    data.tasks.push({
      id: str(t.id),
      title: str(t.title) || 'Задача',
      description: opt(t.description),
      status: enumOr(t.status, STATUSES, 'TODO'),
      priority: enumOr(t.priority, PRIORITIES, 'MEDIUM'),
      due_date: epochToIso(t.dueDate),
      // связь с рутиной: старый numeric id, маппится в applyBackup
      routine_id: t.routineId == null ? null : str(t.routineId),
      goal_id: null,
      completed_at: epochToIso(t.completedAt),
      created: epochToIso(t.createdAt) ?? now,
      updated: epochToIso(t.createdAt) ?? now,
      deleted: 0,
    } satisfies BackupRow)
  }

  for (const raw of json.notes ?? []) {
    const n = (raw ?? {}) as Record<string, unknown>
    const noteId = str(n.id)
    data.notes.push({
      id: noteId,
      title: opt(n.title),
      content: str(n.content),
      created: epochToIso(n.createdAt) ?? now,
      updated: epochToIso(n.updatedAt) ?? epochToIso(n.createdAt) ?? now,
      deleted: 0,
    } satisfies BackupRow)
    const tags = Array.isArray(n.tags) ? n.tags : []
    for (const tag of tags) {
      const value = opt(tag)
      if (!value) continue
      data.note_tags.push({
        id: `v1tag${noteId}_${value}`,
        note_id: noteId,
        tag: value,
        created: epochToIso(n.createdAt) ?? now,
        updated: epochToIso(n.updatedAt) ?? now,
        deleted: 0,
      } satisfies BackupRow)
    }
  }

  return { version: BACKUP_VERSION, exportedAt: now, data }
}
