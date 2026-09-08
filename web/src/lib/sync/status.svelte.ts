import { getDb } from '../db'

export const syncStatus = $state({
  pending: 0,
  running: false,
  lastError: null as string | null,
  lastSyncAt: null as string | null,
})

export function updatePendingCount(): void {
  try {
    syncStatus.pending =
      getDb().queryOne<{ n: number }>('SELECT COUNT(*) AS n FROM _sync_queue')?.n ?? 0
  } catch {
    // БД ещё не инициализирована
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('selfflow:mutated', updatePendingCount)
}
