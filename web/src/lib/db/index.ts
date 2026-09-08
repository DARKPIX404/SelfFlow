import { Capacitor } from '@capacitor/core'
import type { DbDriver } from './driver'
import { createSqlJsDriver } from './sqljs'
import { createSqliteDriver } from './sqlite'
import { runMigrations } from './migrations'

let driver: DbDriver | null = null

export async function initDb(): Promise<DbDriver> {
  if (driver) return driver
  // На нативе — реальная SQLite на устройстве (@capacitor-community/sqlite),
  // в браузере (dev) — sql.js + localStorage.
  driver = Capacitor.isNativePlatform() ? await createSqliteDriver() : await createSqlJsDriver()
  runMigrations(driver)
  return driver
}

export function getDb(): DbDriver {
  if (!driver) throw new Error('[db] initDb() not called')
  return driver
}
