import { Capacitor } from '@capacitor/core'
import { Preferences } from '@capacitor/preferences'
import { pb } from './pb'
import { DB_STORAGE_KEY } from '../db/sqljs'
import { deleteSqliteDatabase } from '../db/sqlite'

const OWNER_KEY = 'selfflow.db.owner'
const AUTH_KEY = 'selfflow.pb.auth'

export interface SessionUser {
  id: string
  email: string
}

export const session = $state<{ user: SessionUser | null }>({ user: null })

/**
 * Натив: восстанавливаем сессию PocketBase из @capacitor/preferences и
 * подписываемся на изменения, чтобы токен жил там, а не в localStorage WebView.
 * В браузере SDK сам хранит authStore в localStorage — ничего не делаем.
 * Вызывается в main.ts ДО prepareDbForUser()/initDb().
 */
export async function restoreNativeSession(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return

  const { value } = await Preferences.get({ key: AUTH_KEY })
  if (value) {
    try {
      const data = JSON.parse(value) as { token?: string; record?: Parameters<typeof pb.authStore.save>[1] }
      if (data?.token) pb.authStore.save(data.token, data.record ?? undefined)
    } catch {
      await Preferences.remove({ key: AUTH_KEY })
    }
  }

  pb.authStore.onChange((token) => {
    if (token) {
      void Preferences.set({
        key: AUTH_KEY,
        value: JSON.stringify({ token, record: pb.authStore.record }),
      })
    } else {
      void Preferences.remove({ key: AUTH_KEY })
    }
  })
}

/** Нативный аналог prepareDbForUser(): защита нативной БД по owner. */
export async function prepareDbForUserNative(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  const record = pb.authStore.record
  const { value: storedOwner } = await Preferences.get({ key: OWNER_KEY })
  if (!record) {
    if (storedOwner) {
      await Preferences.remove({ key: OWNER_KEY })
      await deleteSqliteDatabase()
    }
    return
  }
  if (storedOwner !== record.id) {
    await deleteSqliteDatabase()
    await Preferences.set({ key: OWNER_KEY, value: record.id })
  }
}

function syncSession(): void {
  const record = pb.authStore.record
  session.user = record ? { id: record.id, email: String(record.email ?? '') } : null
  // Фиксируем владельца локальной БД при каждой смене авторизации.
  // Важно: без этого OWNER_KEY не записывался при регистрации в сессии,
  // и reload стирал БД (см. prepareDbForUser).
  if (typeof window === 'undefined') return
  const storedOwner = localStorage.getItem(OWNER_KEY)
  if (!record) {
    if (storedOwner) {
      localStorage.removeItem(DB_STORAGE_KEY)
      localStorage.removeItem(OWNER_KEY)
    }
    return
  }
  if (storedOwner !== record.id) {
    localStorage.removeItem(DB_STORAGE_KEY)
    localStorage.setItem(OWNER_KEY, record.id)
  }
}

pb.authStore.onChange(() => syncSession())
syncSession()

export async function login(email: string, password: string): Promise<void> {
  await pb.collection('users').authWithPassword(email, password)
}

export async function register(email: string, password: string): Promise<void> {
  await pb.collection('users').create({ email, password, passwordConfirm: password })
  await login(email, password)
}

export async function logout(): Promise<void> {
  pb.authStore.clear()
  localStorage.removeItem(DB_STORAGE_KEY)
  localStorage.removeItem(OWNER_KEY)
  window.location.hash = '#/login'
  window.location.reload()
}

/**
 * При старте: если в локальном хранилище данные другого пользователя — стереть.
 * Вызывается до initDb().
 */
export function prepareDbForUser(): void {
  const record = pb.authStore.record
  const storedOwner = localStorage.getItem(OWNER_KEY)
  if (!record) {
    if (storedOwner) {
      localStorage.removeItem(DB_STORAGE_KEY)
      localStorage.removeItem(OWNER_KEY)
    }
    return
  }
  if (storedOwner !== record.id) {
    localStorage.removeItem(DB_STORAGE_KEY)
    localStorage.setItem(OWNER_KEY, record.id)
  }
}
