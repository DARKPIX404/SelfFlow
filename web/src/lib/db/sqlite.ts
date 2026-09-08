import { CapacitorSQLite } from '@capacitor-community/sqlite'
import initSqlJs from 'sql.js'
import type { Database } from 'sql.js'
import wasmUrl from 'sql.js/dist/sql-wasm.wasm?url'
import type { DbDriver, SqlParam } from './driver'
import { schemaStatements } from './migrations'

/**
 * Нативный драйвер БД (Android). Репозитории синхронные, поэтому здесь
 * write-through зеркало:
 *
 * - синхронный движок — sql.js в памяти WebView (все чтения/записи мгновенны,
 *   интерфейс DbDriver не меняется);
 * - реальная SQLite на устройстве — @capacitor-community/sqlite: при старте
 *   данные загружаются из нативной БД в память, каждая мутация зеркалится
 *   в нативную БД через сериализованную очередь (источник правды между
 *   запусками и защита от eviction WebView-storage).
 *
 * Uint8Array-параметры не используются репозиториями — в нативное зеркало
 * уходят только string | number | null.
 */

const DB_NAME = 'selfflow-db'
const DB_VERSION = 1

interface JsonTable {
  name: string
  schema?: string
  values?: unknown[][]
}

interface JsonExport {
  tables?: JsonTable[]
}

/** Полное удаление нативной БД (смена пользователя / сброс). */
export async function deleteSqliteDatabase(): Promise<void> {
  await CapacitorSQLite.close({ database: DB_NAME }).catch(() => {})
  await CapacitorSQLite.deleteDatabase({ database: DB_NAME }).catch(() => {})
}

export async function createSqliteDriver(): Promise<DbDriver> {
  const SQL = await initSqlJs({ locateFile: () => wasmUrl })
  const memory: Database = new SQL.Database()

  // Нативное подключение (idempotent — может уже существовать).
  await CapacitorSQLite.createConnection({ database: DB_NAME, version: DB_VERSION }).catch(() => {})
  await CapacitorSQLite.open({ database: DB_NAME }).catch(() => {})

  // Сериализованная очередь записи в нативную БД — порядок строго тот же,
  // что и у синхронных мутаций памяти.
  let queue: Promise<unknown> = Promise.resolve()
  const native = (fn: () => Promise<unknown>): void => {
    queue = queue.then(fn, fn)
  }
  const nativeRun = (sql: string, params: SqlParam[] = []): void => {
    const values = params.map((p) => (typeof p === 'object' && p !== null ? String(p) : p)) as (string | number | null)[]
    native(() => CapacitorSQLite.run({ database: DB_NAME, statement: sql, values }))
  }

  // 1) Если нативная БД пустая (первый запуск) — создаём там схему.
  //    Миграции памяти (runMigrations в initDb) её продублируют в памяти
  //    (все CREATE ... IF NOT EXISTS), поэтому отдельная загрузка не нужна.
  // 2) Иначе загружаем нативные данные в память — runMigrations увидит
  //    _migrations из экспорта и станет no-op.
  const exported = (await CapacitorSQLite.exportToJson({
    database: DB_NAME,
    jsonexportmode: 'full',
  })) as unknown as JsonExport

  const isFresh = !exported.tables || exported.tables.length === 0
  if (isFresh) {
    for (const statement of schemaStatements()) {
      await CapacitorSQLite.execute({ database: DB_NAME, statements: statement })
    }
  }

  for (const table of exported.tables ?? []) {
    if (table.schema) memory.run(table.schema)
    const stmt = memory.prepare(`SELECT * FROM ${table.name} LIMIT 0`)
    const colNames = stmt.getColumnNames()
    stmt.free()
    if (!table.values || table.values.length === 0) continue
    const insert = memory.prepare(
      `INSERT OR REPLACE INTO ${table.name} (${colNames.join(', ')}) VALUES (${colNames.map(() => '?').join(', ')})`,
    )
    try {
      for (const row of table.values) insert.run(row as SqlParam[])
    } finally {
      insert.free()
    }
  }

  const driver: DbDriver = {
    run(sql, params = []) {
      memory.run(sql, params)
      nativeRun(sql, params)
    },
    query<T>(sql: string, params: SqlParam[] = []): T[] {
      const stmt = memory.prepare(sql)
      try {
        stmt.bind(params)
        const rows: T[] = []
        while (stmt.step()) rows.push(stmt.getAsObject() as T)
        return rows
      } finally {
        stmt.free()
      }
    },
    queryOne<T>(sql: string, params: SqlParam[] = []): T | undefined {
      return driver.query<T>(sql, params)[0]
    },
    transaction(fn) {
      memory.run('BEGIN')
      nativeRun('BEGIN')
      try {
        fn()
        memory.run('COMMIT')
        nativeRun('COMMIT')
      } catch (e) {
        memory.run('ROLLBACK')
        nativeRun('ROLLBACK')
        throw e
      }
    },
    reset() {
      memory.close()
      native(() =>
        CapacitorSQLite.close({ database: DB_NAME })
          .catch(() => {})
          .then(() => CapacitorSQLite.deleteDatabase({ database: DB_NAME }))
          .catch((e) => console.error('[db] native reset failed', e)),
      )
    },
  }

  return driver
}
