import { CapacitorSQLite, SQLiteConnection, type SQLiteDBConnection } from '@capacitor-community/sqlite'
import initSqlJs from 'sql.js'
import type { Database } from 'sql.js'
import wasmUrl from 'sql.js/dist/sql-wasm.wasm?url'
import type { DbDriver, SqlParam } from './driver'
import { sanitizeParams } from './driver'
import { schemaStatements, runMigrations } from './migrations'

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
 * Работаем через SQLiteConnection-обёртку с checkConnectionsConsistency() —
 * raw-вызовы createConnection на «протухшем» подключении (после
 * deleteDatabase / пересоздания WebView) бросают непонятные ошибки.
 *
 * Uint8Array-параметры не используются репозиториями — в нативное зеркало
 * уходят только string | number | null.
 */

const DB_NAME = 'selfflow-db'
const DB_VERSION = 1

const sqlite = new SQLiteConnection(CapacitorSQLite)

interface JsonTable {
  name: string
  values?: unknown[][]
}

interface JsonExport {
  export?: {
    tables?: JsonTable[]
  }
}

/** Подключение к нативной БД с восстановлением консистентности состояния плагина. */
async function nativeConnection(): Promise<SQLiteDBConnection> {
  await sqlite.checkConnectionsConsistency().catch((e) => {
    console.error('[db] checkConnectionsConsistency failed (продолжаем)', e)
  })
  const existing = await sqlite.isConnection(DB_NAME, false).catch(() => ({ result: false }))
  if (existing.result) {
    return sqlite.retrieveConnection(DB_NAME, false)
  }
  return sqlite.createConnection(DB_NAME, false, 'no-encryption', DB_VERSION, false)
}

export async function createSqliteDriver(): Promise<DbDriver> {
  let SQL: Awaited<ReturnType<typeof initSqlJs>>
  try {
    SQL = await initSqlJs({ locateFile: () => wasmUrl })
  } catch (e) {
    throw new Error(`[db] sql.js (wasm-движок) не загрузился: ${String(e)}`)
  }
  const memory: Database = new SQL.Database()

  // Нативное подключение: открываем, если ещё не открыто. Ошибки не глотаем
  // молча — без нативной БД зеркало бессмысленно, лучше увидеть причину.
  const conn = await nativeConnection()
  try {
    const isOpen = await conn.isDBOpen().catch(() => ({ result: false }))
    if (!isOpen.result) await conn.open()
  } catch (e) {
    throw new Error(`[db] открытие нативной БД '${DB_NAME}' не удалось: ${String(e)}`)
  }

  // Сериализованная очередь записи в нативную БД — порядок строго тот же,
  // что и у синхронных мутаций памяти. Ошибки зеркала логируем: память уже
  // обновлена, рассинхрон компенсируется полной перезаписью при следующем
  // запуске, но молчать здесь нельзя.
  let queue: Promise<unknown> = Promise.resolve()
  const mirror = (fn: () => Promise<unknown>): void => {
    queue = queue.then(fn, fn).catch((e) => console.error('[db] native mirror failed', e))
  }
  const mirrorRun = (sql: string, params: SqlParam[] = []): void => {
    const values = params.map((p) =>
      p === undefined ? null : typeof p === 'object' && p !== null ? String(p) : p,
    ) as (string | number | null)[]
    mirror(() => conn.run(sql, values))
  }

  const driver: DbDriver = {
    run(sql, params = []) {
      const safe = sanitizeParams(sql, params)
      memory.run(sql, safe)
      mirrorRun(sql, safe)
    },
    query<T>(sql: string, params: SqlParam[] = []): T[] {
      const stmt = memory.prepare(sql)
      try {
        stmt.bind(sanitizeParams(sql, params))
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
      mirrorRun('BEGIN')
      try {
        fn()
        memory.run('COMMIT')
        mirrorRun('COMMIT')
      } catch (e) {
        memory.run('ROLLBACK')
        mirrorRun('ROLLBACK')
        throw e
      }
    },
    reset() {
      memory.close()
      mirror(() =>
        conn
          .close()
          .catch((e) => console.error('[db] close before reset failed', e))
          .then(() => CapacitorSQLite.deleteDatabase({ database: DB_NAME }))
          .catch((e) => console.error('[db] native reset failed', e)),
      )
    },
  }

  // Полная схема памяти (миграции заодно зеркалятся в нативную БД — там тоже
  // CREATE ... IF NOT EXISTS, дубли безвредны).
  runMigrations(driver)

  // Выгружаем нативные данные. exportToJson НЕ должен ронять стартап:
  // пустая/битая/несовместимая выгрузка = трактуем как fresh БД.
  let tables: JsonTable[] = []
  try {
    const exported = (await conn.exportToJson('full')) as unknown as JsonExport
    tables = exported.export?.tables ?? []
  } catch (e) {
    console.error('[db] exportToJson failed, считаем БД пустой', e)
  }

  if (tables.length === 0) {
    // Первый запуск (или выгрузка пустая): создаём схему на диске, чтобы
    // файл сразу был валидной SQLite с полной схемой.
    for (const statement of schemaStatements()) {
      await conn.execute(statement).catch((e) => console.error('[db] native schema failed', e))
    }
  } else {
    // Загружаем строки в память. Схему из export НЕ используем — в v8 плагина
    // JsonTable.schema это JsonColumn[], а не CREATE-строка; схема памяти уже
    // создана миграциями выше.
    for (const table of tables) {
      if (!table.values || table.values.length === 0) continue
      try {
        const stmt = memory.prepare(`SELECT * FROM ${table.name} LIMIT 0`)
        const colNames = stmt.getColumnNames()
        stmt.free()
        const insert = memory.prepare(
          `INSERT OR REPLACE INTO ${table.name} (${colNames.join(', ')}) VALUES (${colNames.map(() => '?').join(', ')})`,
        )
        try {
          // нативная строка обрезается до колонок памяти: рассинхрон схем
          // (старая нативная БД) не должен ронять загрузку целиком
          for (const row of table.values) insert.run((row as SqlParam[]).slice(0, colNames.length))
        } finally {
          insert.free()
        }
      } catch (e) {
        console.error(`[db] загрузка таблицы ${table.name} пропущена`, e)
      }
    }
  }

  return driver
}

/** Полное удаление нативной БД (смена пользователя / сброс). */
export async function deleteSqliteDatabase(): Promise<void> {
  await sqlite.checkConnectionsConsistency().catch((e) => {
    console.error('[db] checkConnectionsConsistency в deleteSqliteDatabase failed', e)
  })
  const isConn = await sqlite.isConnection(DB_NAME, false).catch(() => ({ result: false }))
  if (isConn.result) {
    const conn = await sqlite.retrieveConnection(DB_NAME, false).catch(() => null)
    if (conn) {
      const isOpen = await conn.isDBOpen().catch(() => ({ result: false }))
      if (isOpen.result) await conn.close().catch((e) => console.error('[db] close перед delete failed', e))
      await sqlite.closeConnection(DB_NAME, false).catch((e) => console.error('[db] closeConnection failed', e))
    }
  }
  await CapacitorSQLite.deleteDatabase({ database: DB_NAME }).catch((e) =>
    console.error('[db] deleteDatabase failed', e),
  )
}
