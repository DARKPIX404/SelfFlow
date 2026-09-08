import { pb } from '../auth/pb'
import { getDb } from '../db'
import { nowIso } from '../db/id'
import { repositories, type Repository } from '../db/repositories'
import type { BaseEntity } from '../types'
import { syncStatus, updatePendingCount } from './status.svelte'

const PER_PAGE = 200
const SYNC_INTERVAL_MS = 60_000

let running = false

function isNotFound(e: unknown): boolean {
  return typeof e === 'object' && e !== null && 'status' in e && (e as { status: number }).status === 404
}

function errorMessage(e: unknown): string {
  if (typeof e === 'object' && e !== null && 'data' in e) {
    const data = (e as { data?: { data?: Record<string, { message?: string }>; message?: string } }).data
    const fields = data?.data
      ? Object.entries(data.data).map(([k, f]) => `${k}: ${f.message ?? ''}`)
      : []
    if (fields.length) return fields.join('; ')
    if (data?.message) return data.message
  }
  if (typeof e === 'object' && e !== null && 'message' in e) return String((e as Error).message)
  return String(e)
}

async function pushQueue(): Promise<void> {
  const db = getDb()
  const queue = db.query<{ id: number; collection: string; entity_id: string; op: string }>(
    'SELECT id, collection, entity_id, op FROM _sync_queue ORDER BY id',
  )
  for (const item of queue) {
    const repo: Repository<BaseEntity> | undefined = repositories[item.collection]
    if (!repo) {
      db.run('DELETE FROM _sync_queue WHERE id = ?', [item.id])
      continue
    }
    try {
      if (item.op === 'delete') {
        try {
          await pb.collection(item.collection).delete(item.entity_id)
        } catch (e) {
          if (!isNotFound(e)) throw e
        }
      } else {
        const local = repo.getRaw(item.entity_id)
        if (!local) {
          // локально записи нет — нечего отправлять
          db.run('DELETE FROM _sync_queue WHERE id = ?', [item.id])
          continue
        }
        const payload = repo.toServerPayload(local)
        try {
          await pb.collection(item.collection).update(item.entity_id, payload)
        } catch (e) {
          if (isNotFound(e)) {
            // записи нет на сервере — создаём с тем же id и owner
            await pb.collection(item.collection).create({
              ...payload,
              id: item.entity_id,
              owner: pb.authStore.record!.id,
            })
          } else {
            throw e
          }
        }
      }
      db.run('DELETE FROM _sync_queue WHERE id = ?', [item.id])
    } catch (e) {
      // сохраняем порядок очереди: останавливаемся на первой ошибке
      console.error(`[sync] push failed for ${item.collection}/${item.entity_id}`, e)
      throw e
    }
  }
}

async function pullCollection(collection: string): Promise<void> {
  const db = getDb()
  const repo = repositories[collection]
  const last =
    db.queryOne<{ last_pull_iso: string }>('SELECT last_pull_iso FROM _sync_state WHERE collection = ?', [
      collection,
    ])?.last_pull_iso ?? ''
  let page = 1
  for (;;) {
    const res = await pb.collection(collection).getList(page, PER_PAGE, {
      filter: `updated>'${last}'`,
      sort: 'updated',
    })
    for (const record of res.items) {
      repo.upsertFromServer(record as unknown as Record<string, unknown>)
    }
    if (page >= res.totalPages) break
    page++
  }
  db.run(
    'INSERT INTO _sync_state (collection, last_pull_iso) VALUES (?, ?) ' +
      'ON CONFLICT(collection) DO UPDATE SET last_pull_iso = excluded.last_pull_iso',
    [collection, nowIso()],
  )
}

async function pullAll(): Promise<void> {
  for (const collection of Object.keys(repositories)) {
    await pullCollection(collection)
  }
}

export async function syncAll(): Promise<void> {
  if (running || !pb.authStore.isValid || !navigator.onLine) return
  running = true
  syncStatus.running = true
  syncStatus.lastError = null
  try {
    await pushQueue()
    await pullAll()
    syncStatus.lastSyncAt = new Date().toISOString()
    window.dispatchEvent(new CustomEvent('selfflow:synced'))
  } catch (e) {
    syncStatus.lastError = errorMessage(e)
  } finally {
    running = false
    syncStatus.running = false
    updatePendingCount()
  }
}

/** Триггеры: online-событие + интервал 60 с при активном окне */
export function initSync(): void {
  window.addEventListener('online', () => void syncAll())
  updatePendingCount()
  setInterval(() => {
    if (document.visibilityState === 'visible' && navigator.onLine) void syncAll()
  }, SYNC_INTERVAL_MS)
}
