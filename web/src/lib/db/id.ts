const BASE36 = '0123456789abcdefghijklmnopqrstuvwxyz'

function randomBase36(length: number): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  let out = ''
  for (const b of bytes) out += BASE36[b % 36]
  return out
}

/**
 * Локальный id записи. PocketBase ограничивает id 15 символами [a-z0-9],
 * поэтому UUIDv7 не подходит. Формат: base36-таймстамп (8 символов, монотонный)
 * + 7 случайных символов.
 */
export function newId(): string {
  return Date.now().toString(36).padStart(8, '0') + randomBase36(7)
}

/**
 * Текущее время в формате сервера PocketBase: "YYYY-MM-DD HH:mm:ss.sssZ".
 * Важно: pull-фильтр `updated > last_pull` сравнивает строки лексикографически,
 * формат должен совпадать с серверным (с пробелом, не с "T").
 */
export function nowIso(): string {
  return new Date().toISOString().replace('T', ' ')
}

export function todayLocal(): string {
  const d = new Date()
  const off = d.getTimezoneOffset()
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10)
}
