export type SqlParam = string | number | null | Uint8Array

/**
 * sql.js бросает нечитаемое "tried to bind a value of an unknown type
 * (undefined)" и валит приложение, если в параметры попал undefined.
 * undefined означает «нет значения» — приводим к NULL (та же семантика,
 * что у normalize() в репозиториях) и оставляем диагностику в консоль,
 * чтобы по логам найти источник.
 */
export function sanitizeParams(sql: string, params: SqlParam[]): SqlParam[] {
  let warned = false
  return params.map((raw: SqlParam | undefined) => {
    if (raw !== undefined) return raw
    if (!warned) {
      warned = true
      console.error(`[db] undefined-параметр приведён к NULL: ${sql}`, params)
    }
    return null
  })
}

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
