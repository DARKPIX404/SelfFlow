import { BACKUP_TABLES, BACKUP_VERSION, type BackupFile, type BackupRow, type BackupTable } from './format'
import { isV1Backup, migrateV1 } from './migrate'
import { repositories } from '../db/repositories'
import { newId, nowIso } from '../db/id'
import type { DbDriver, SqlParam } from '../db/driver'

/**
 * Импорт бэкапа: парсинг файла (v2 / legacy v1) и атомарная замена всех
 * данных текущего owner. Всё — в одной транзакции: при ошибке на любом
 * шаге локальная БД откатывается, пользователь получает toast об ошибке.
 *
 * Id всех записей заменяются на новые 15-символьные (PocketBase-формат),
 * внешние ключи переписываются через старый→новый маппинг — связи
 * task→routine, note→tag, habit→log, goal→task, routine→completion и
 * task→focus_session сохраняются.
 *
 * Синхронизация: импортированные записи встают в очередь на upsert, а все
 * прежние id текущего owner — на delete (иначе pull вернул бы старые
 * данные с сервера и импорт «расползся» бы обратно).
 */

export class BackupError extends Error {}

/** JSON-текст файла → нормализованный BackupFile (v2 как есть, v1 — миграция). */
export function parseBackup(text: string): BackupFile {
  let json: unknown
  try {
    json = JSON.parse(text)
  } catch {
    throw new BackupError('Файл не является JSON')
  }
  if (typeof json !== 'object' || json === null) {
    throw new BackupError('Неподдерживаемый формат бэкапа')
  }
  const o = json as Record<string, unknown>
  if ('version' in o || 'data' in o) {
    if (o.version !== BACKUP_VERSION) {
      throw new BackupError(
        o.version == null
          ? 'Неподдерживаемый формат бэкапа'
          : `Неподдерживаемая версия бэкапа: ${String(o.version)}`,
      )
    }
    if (typeof o.data !== 'object' || o.data === null) {
      throw new BackupError('Неподдерживаемый формат бэкапа: нет секции data')
    }
    const raw = o.data as Record<string, unknown>
    const data = {} as BackupFile['data']
    for (const table of BACKUP_TABLES) {
      const rows = raw[table]
      if (rows === undefined) {
        data[table] = []
        continue
      }
      if (!Array.isArray(rows)) {
        throw new BackupError(`Неподдерживаемый формат бэкапа: ${table} не массив`)
      }
      for (const row of rows) {
        if (typeof row !== 'object' || row === null || typeof (row as BackupRow).id !== 'string') {
          throw new BackupError(`Неподдерживаемый формат бэкапа: битая запись в ${table}`)
        }
      }
      data[table] = rows as BackupRow[]
    }
    return { version: BACKUP_VERSION, exportedAt: typeof o.exportedAt === 'string' ? o.exportedAt : nowIso(), data }
  }
  if (isV1Backup(json)) return migrateV1(json)
  throw new BackupError('Неподдерживаемый формат бэкапа')
}

/** внешние ключи: поле → таблица, на которую ссылается */
const FOREIGN_KEYS: Partial<Record<BackupTable, Record<string, BackupTable>>> = {
  routine_completions: { routine_id: 'routines' },
  tasks: { routine_id: 'routines', goal_id: 'goals' },
  note_tags: { note_id: 'notes' },
  habit_logs: { habit_id: 'habits' },
  focus_sessions: { task_id: 'tasks' },
}

const ENUM_FIELDS: Record<string, Set<string>> = {
  recurrence_rule: new Set(['NONE', 'DAILY', 'WEEKDAYS', 'WEEKLY']),
  status: new Set(['TODO', 'IN_PROGRESS', 'DONE']),
  priority: new Set(['LOW', 'MEDIUM', 'HIGH']),
}

const ENUM_DEFAULTS: Record<string, string> = {
  recurrence_rule: 'NONE',
  status: 'TODO',
  priority: 'MEDIUM',
}

function normalizeValue(field: string, value: unknown): SqlParam {
  if (value === undefined || value === null) return null
  if (typeof value === 'boolean') return value ? 1 : 0
  if (typeof value === 'number') return value
  if (typeof value === 'string') return value
  return String(value)
}

export interface ImportStats {
  imported: number
  /** сколько прежних записей owner помечено на удаление на сервере */
  removed: number
}

export function applyBackup(db: DbDriver, file: BackupFile, owner: string): ImportStats {
  const now = nowIso()

  // 1) маппинг старый id → новый id (все таблицы в одном пространстве имён)
  const idMap = new Map<string, string>()
  for (const table of BACKUP_TABLES) {
    for (const row of file.data[table]) {
      idMap.set(`${table}:${row.id}`, newId())
    }
  }
  const remap = (table: BackupTable, value: unknown): string | null => {
    if (typeof value !== 'string' || value === '') return null
    return idMap.get(`${table}:${value}`) ?? null
  }

  // 2) подготовка строк: новые id, owner текущего пользователя, валидные enum'ы
  const prepared = new Map<BackupTable, SqlParam[][]>()
  for (const table of BACKUP_TABLES) {
    const repo = repositories[table]
    const fk = FOREIGN_KEYS[table] ?? {}
    const rows: SqlParam[][] = []
    for (const row of file.data[table]) {
      const values: SqlParam[] = [idMap.get(`${table}:${row.id}`)!, owner]
      for (const field of repo.fields) {
        if (field in fk) {
          values.push(remap(fk[field], row[field]))
          continue
        }
        const raw = row[field]
        if (field in ENUM_FIELDS) {
          const s = typeof raw === 'string' && ENUM_FIELDS[field].has(raw) ? raw : ENUM_DEFAULTS[field]
          values.push(s)
          continue
        }
        values.push(normalizeValue(field, raw))
      }
      const created = typeof row.created === 'string' && row.created ? row.created : now
      const updated = typeof row.updated === 'string' && row.updated ? row.updated : created
      values.push(created, updated, row.deleted ? 1 : 0)
      rows.push(values)
    }
    prepared.set(table, rows)
  }

  // 3) замена данных + постановка в sync-очередь — одна транзакция
  let imported = 0
  let removed = 0
  db.transaction(() => {
    for (const table of BACKUP_TABLES) {
      const repo = repositories[table]
      const oldIds = db
        .query<{ id: string }>(`SELECT id FROM ${table} WHERE owner = ?`, [owner])
        .map((r) => r.id)
      db.run(`DELETE FROM ${table} WHERE owner = ?`, [owner])
      const cols = ['id', 'owner', ...repo.fields, 'created', 'updated', 'deleted']
      const stmt = `INSERT INTO ${table} (${cols.join(', ')}) VALUES (${cols.map(() => '?').join(', ')})`
      for (const values of prepared.get(table)!) {
        db.run(stmt, values)
        imported++
      }
      for (const oldId of oldIds) {
        db.run('INSERT INTO _sync_queue (collection, entity_id, op, created_at) VALUES (?, ?, ?, ?)', [
          table,
          oldId,
          'delete',
          now,
        ])
        removed++
      }
      for (const values of prepared.get(table)!) {
        db.run('INSERT INTO _sync_queue (collection, entity_id, op, created_at) VALUES (?, ?, ?, ?)', [
          table,
          values[0] as string,
          'upsert',
          now,
        ])
      }
    }
  })
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  return { imported, removed }
}
