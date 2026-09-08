export type SqlParam = string | number | null | Uint8Array

/**
 * Абстракция над локальной SQLite. Реализации:
 * - db/sqljs.ts — браузер (dev): wasm в памяти + persist в localStorage;
 * - db/sqlite.ts — натив: write-through зеркало в @capacitor-community/sqlite
 *   (реальная SQLite на устройстве), синхронные чтения из памяти.
 * Репозитории и синхронизация не меняются.
 */
export interface DbDriver {
  run(sql: string, params?: SqlParam[]): void
  query<T>(sql: string, params?: SqlParam[]): T[]
  queryOne<T>(sql: string, params?: SqlParam[]): T | undefined
  transaction(fn: () => void): void
  /** Полностью стереть локальную БД (вызывается при logout / смене пользователя) */
  reset(): void
}
