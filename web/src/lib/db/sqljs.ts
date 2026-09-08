import initSqlJs from 'sql.js'
import type { Database } from 'sql.js'
import wasmUrl from 'sql.js/dist/sql-wasm.wasm?url'
import type { DbDriver, SqlParam } from './driver'
import { sanitizeParams } from './driver'

export const DB_STORAGE_KEY = 'selfflow.db.v1'

const SAVE_DEBOUNCE_MS = 300

function toBase64(u8: Uint8Array): string {
  let bin = ''
  const chunk = 0x8000
  for (let i = 0; i < u8.length; i += chunk) {
    bin += String.fromCharCode(...u8.subarray(i, i + chunk))
  }
  return btoa(bin)
}

function fromBase64(b64: string): Uint8Array {
  const bin = atob(b64)
  const u8 = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i)
  return u8
}

export async function createSqlJsDriver(): Promise<DbDriver> {
  const SQL = await initSqlJs({ locateFile: () => wasmUrl })

  const saved = localStorage.getItem(DB_STORAGE_KEY)
  const db: Database = saved ? new SQL.Database(fromBase64(saved)) : new SQL.Database()

  let saveTimer: ReturnType<typeof setTimeout> | null = null
  const persist = () => {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => {
      try {
        localStorage.setItem(DB_STORAGE_KEY, toBase64(db.export()))
      } catch (e) {
        console.error('[db] persist failed', e)
      }
    }, SAVE_DEBOUNCE_MS)
  }

  const driver: DbDriver = {
    run(sql, params = []) {
      const safe = sanitizeParams(sql, params)
      db.run(sql, safe)
      persist()
    },
    query<T>(sql: string, params: SqlParam[] = []): T[] {
      const stmt = db.prepare(sql)
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
      db.run('BEGIN')
      try {
        fn()
        db.run('COMMIT')
      } catch (e) {
        db.run('ROLLBACK')
        throw e
      }
      persist()
    },
    reset() {
      if (saveTimer) clearTimeout(saveTimer)
      localStorage.removeItem(DB_STORAGE_KEY)
      db.close()
    },
  }

  return driver
}
