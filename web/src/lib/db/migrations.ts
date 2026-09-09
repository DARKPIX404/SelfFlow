import type { DbDriver } from './driver'
import { pb } from '../auth/pb'
import { newId } from './id'

interface Migration {
  id: number
  name: string
  /** скрипт — выполняется построчно (sql.js умеет один statement за раз) */
  sql?: string
  /** код — для условной логики, которую не выразить чистым SQL */
  fn?: (db: DbDriver) => void
}

const V1_SCHEMA = `
CREATE TABLE routines (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  start_time TEXT,
  end_time TEXT,
  recurrence_rule TEXT DEFAULT 'NONE',
  category TEXT,
  notification_text TEXT,
  is_active INTEGER DEFAULT 1,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE tasks (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  status TEXT DEFAULT 'TODO',
  priority TEXT DEFAULT 'MEDIUM',
  due_date TEXT,
  routine_id TEXT,
  goal_id TEXT,
  completed_at TEXT,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE notes (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  title TEXT,
  content TEXT NOT NULL,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE note_tags (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  note_id TEXT NOT NULL,
  tag TEXT NOT NULL,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE habits (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  title TEXT NOT NULL,
  color TEXT,
  icon TEXT,
  target_per_week REAL,
  sort REAL,
  reminder_time TEXT,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE habit_logs (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  habit_id TEXT NOT NULL,
  date TEXT NOT NULL,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE goals (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  title TEXT NOT NULL,
  deadline TEXT,
  note TEXT,
  sort REAL,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE focus_sessions (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  started_at TEXT NOT NULL,
  minutes REAL NOT NULL,
  task_id TEXT,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE settings (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  key TEXT NOT NULL,
  value TEXT,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE routine_templates (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  title TEXT NOT NULL,
  items_json TEXT,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE TABLE _sync_queue (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  collection TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  op TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE TABLE _sync_state (
  collection TEXT PRIMARY KEY,
  last_pull_iso TEXT NOT NULL
);
CREATE INDEX idx_tasks_owner ON tasks(owner, deleted);
CREATE INDEX idx_notes_owner ON notes(owner, deleted);
CREATE INDEX idx_note_tags_note ON note_tags(note_id, deleted);
CREATE INDEX idx_habit_logs_habit ON habit_logs(habit_id, date, deleted);
CREATE INDEX idx_sync_queue_id ON _sync_queue(id);
`

const V2_ROUTINE_COMPLETIONS_SCHEMA = `
CREATE TABLE routine_completions (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  routine_id TEXT NOT NULL,
  date TEXT NOT NULL,
  created TEXT NOT NULL,
  updated TEXT NOT NULL,
  deleted INTEGER DEFAULT 0
);
CREATE INDEX idx_routine_completions_routine ON routine_completions(routine_id, date, deleted);
`

/**
 * Фаза 2 хранила выполнение рутин в таблице без sync-полей
 * (routine_id, date, PK(routine_id+date)). Переводим на полноценную сущность,
 * сохранив уже накопленные отметки.
 */
function migrateV2RoutineCompletions(db: DbDriver): void {
  const cols = db.query<{ name: string }>('PRAGMA table_info(routine_completions)')
  if (cols.some((c) => c.name === 'id')) return // уже новая схема
  if (cols.length > 0) {
    db.run('ALTER TABLE routine_completions RENAME TO routine_completions_legacy')
  }
  for (const statement of V2_ROUTINE_COMPLETIONS_SCHEMA.split(';').map((s) => s.trim()).filter(Boolean)) {
    db.run(statement)
  }
  if (cols.length > 0) {
    const owner = pb.authStore.record?.id ?? ''
    const now = new Date().toISOString().replace('T', ' ')
    const legacy = db.query<{ routine_id: string; date: string }>(
      'SELECT routine_id, date FROM routine_completions_legacy',
    )
    for (const row of legacy) {
      db.run(
        'INSERT INTO routine_completions (id, owner, routine_id, date, created, updated, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)',
        [newId(), owner, row.routine_id, row.date, now, now],
      )
    }
    db.run('DROP TABLE routine_completions_legacy')
  }
}

const migrations: Migration[] = [
  { id: 1, name: 'init', sql: V1_SCHEMA },
  { id: 2, name: 'routine_completions_sync', fn: migrateV2RoutineCompletions },
  {
    id: 3,
    name: 'focus_routine_link',
    fn: (db) => {
      // фокус-сессии привязываются к блоку распорядка дня, а не к задаче
      try {
        db.run('ALTER TABLE focus_sessions ADD COLUMN routine_id TEXT')
      } catch {
        // колонка уже есть (повторный прогон/свежая нативная БД) — ок
      }
    },
  },
]

/**
 * Чисто-SQL куски всех миграций — для нативного зеркала на
 * @capacitor-community/sqlite (см. db/sqlite.ts). fn-миграции сюда не входят:
 * на свежей нативной БД их эффект эквивалентен применению их SQL-схемы.
 */
export function schemaStatements(): string[] {
  return [V1_SCHEMA, V2_ROUTINE_COMPLETIONS_SCHEMA, 'ALTER TABLE focus_sessions ADD COLUMN routine_id TEXT']
    .flatMap((script) => script.split(';').map((s) => s.trim()).filter(Boolean))
}

export function runMigrations(db: DbDriver): void {
  db.run(
    'CREATE TABLE IF NOT EXISTS _migrations (id INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL)',
  )
  const applied = new Set(db.query<{ id: number }>('SELECT id FROM _migrations').map((r) => r.id))
  for (const m of migrations) {
    if (applied.has(m.id)) continue
    db.transaction(() => {
      if (m.sql) {
        // sql.js db.run() выполняет только один statement — разбиваем скрипт
        for (const statement of m.sql.split(';').map((s) => s.trim()).filter(Boolean)) {
          db.run(statement)
        }
      }
      m.fn?.(db)
      db.run('INSERT INTO _migrations (id, name, applied_at) VALUES (?, ?, ?)', [
        m.id,
        m.name,
        new Date().toISOString(),
      ])
    })
  }
}
