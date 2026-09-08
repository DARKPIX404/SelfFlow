/**
 * Формат файла бэкапа SelfFlow v2.
 *
 * Записи — строки локальной БД как есть: id (15 символов [a-z0-9]),
 * created/updated в серверном формате "YYYY-MM-DD HH:mm:ss.sssZ",
 * deleted 0/1 (включая tombstone — сервер тоже хранит мягкие удаления).
 */

export const BACKUP_VERSION = 2

export const BACKUP_TABLES = [
  'routines',
  'routine_completions',
  'tasks',
  'notes',
  'note_tags',
  'habits',
  'habit_logs',
  'goals',
  'focus_sessions',
  'routine_templates',
  'settings',
] as const

export type BackupTable = (typeof BACKUP_TABLES)[number]

export interface BackupRow {
  id: string
  created: string
  updated: string
  /** 0 | 1 */
  deleted: number
  [field: string]: unknown
}

export interface BackupFile {
  version: number
  exportedAt: string
  data: Record<BackupTable, BackupRow[]>
}

export function emptyBackupData(): Record<BackupTable, BackupRow[]> {
  const data = {} as Record<BackupTable, BackupRow[]>
  for (const t of BACKUP_TABLES) data[t] = []
  return data
}
