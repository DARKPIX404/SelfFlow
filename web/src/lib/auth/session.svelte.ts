import { Capacitor } from '@capacitor/core'
import { Preferences } from '@capacitor/preferences'
import { pb } from './pb'
import { DB_STORAGE_KEY } from '../db/sqljs'
import { deleteSqliteDatabase } from '../db/sqlite'
import { getDb } from '../db'

const OWNER_KEY = 'selfflow.db.owner'
const AUTH_KEY = 'selfflow.pb.auth'
const MODE_KEY = 'selfflow.mode'

export interface SessionUser {
  id: string
  email: string
}

/**
 * Гостевой режим: данные живут только на устройстве, синхронизация выключена.
 * owner гостя — константа; БД отделяется от аккаунтных через OWNER_KEY.
 */
export const GUEST_OWNER = 'local-guest'

export const session = $state<{ user: SessionUser | null }>({ user: null })

let nativeGuestMode = false

/** Защита от «успешной» авторизации без токена: record без id не считается. */
function hasValidId(record: unknown): record is { id: string } {
  return (
    typeof record === 'object' &&
    record !== null &&
    typeof (record as { id?: unknown }).id === 'string' &&
    (record as { id: string }).id.length > 0
  )
}

/**
 * Натив: восстанавливаем сессию PocketBase из @capacitor/preferences и
 * подписываемся на изменения, чтобы токен жил там, а не в localStorage WebView.
 * В браузере SDK сам хранит authStore в localStorage — ничего не делаем.
 * Гостевой режим токен не восстанавливает — данных на сервере нет.
 * Вызывается в main.ts ДО prepareDbForUser()/initDb().
 */
export async function restoreNativeSession(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return

  const { value: mode } = await Preferences.get({ key: MODE_KEY })
  nativeGuestMode = mode === 'guest'

  const { value } = await Preferences.get({ key: AUTH_KEY })
  if (value && !nativeGuestMode) {
    try {
      const data = JSON.parse(value) as { token?: string; record?: Parameters<typeof pb.authStore.save>[1] }
      // запасной фильтр против «фейковых» сессий прошлых версий (record без id)
      if (data?.token && hasValidId(data.record)) pb.authStore.save(data.token, data.record)
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
  const owner = hasValidId(record) ? record.id : nativeGuestMode ? GUEST_OWNER : null
  const { value: storedOwner } = await Preferences.get({ key: OWNER_KEY })
  if (!owner) {
    if (storedOwner) {
      await Preferences.remove({ key: OWNER_KEY })
      await deleteSqliteDatabase()
    }
    return
  }
  if (storedOwner !== owner) {
    await deleteSqliteDatabase()
    await Preferences.set({ key: OWNER_KEY, value: owner })
  }
}

/** Текущий владелец локальной БД: аккаунт или гость. */
function currentOwner(): string | null {
  const record = pb.authStore.record
  if (hasValidId(record)) return record.id
  if (typeof window !== 'undefined' && localStorage.getItem(MODE_KEY) === 'guest') return GUEST_OWNER
  return null
}

function syncSession(): void {
  const record = pb.authStore.record
  session.user = hasValidId(record)
    ? { id: record.id, email: String(record.email ?? '') }
    : null
  // Фиксируем владельца локальной БД при каждой смене авторизации.
  // Важно: без этого OWNER_KEY не записывался при регистрации в сессии,
  // и reload стирал БД (см. prepareDbForUser).
  if (typeof window === 'undefined') return
  const owner = currentOwner()
  const storedOwner = localStorage.getItem(OWNER_KEY)
  if (!owner) {
    if (storedOwner) {
      localStorage.removeItem(DB_STORAGE_KEY)
      localStorage.removeItem(OWNER_KEY)
    }
    return
  }
  if (storedOwner !== owner) {
    localStorage.removeItem(DB_STORAGE_KEY)
    localStorage.setItem(OWNER_KEY, owner)
  }
}

pb.authStore.onChange(() => syncSession())
syncSession()

export async function login(email: string, password: string): Promise<void> {
  await pb.collection('users').authWithPassword(email, password)
  // Защита: сервер WebView может ответить 200 без JSON — SDK отдаст пустой
  // объект, и authStore останется невалидным. Без токена входа нет.
  if (!pb.authStore.isValid || !hasValidId(pb.authStore.record)) {
    pb.authStore.clear()
    throw new Error('Failed to authenticate.')
  }
  // Вход под гостем → аккаунт: гостевой режим выключаем, нативную БД
  // перепривязываем к owner аккаунта (веб-хранилище — через syncSession).
  if (typeof window !== 'undefined') localStorage.removeItem(MODE_KEY)
  nativeGuestMode = false
  await Preferences.remove({ key: MODE_KEY }).catch(() => {})
  await prepareDbForUserNative()
  // очередь синка гостевых изменений не должна уходить на сервер аккаунта
  try {
    getDb().run('DELETE FROM _sync_queue')
  } catch {
    // db ещё не инициализирован — чистить нечего
  }
}

export async function register(email: string, password: string): Promise<void> {
  await pb.collection('users').create({ email, password, passwordConfirm: password })
  await login(email, password)
}

/**
 * Гостевой вход: локальные данные без аккаунта. Перезагрузка — чтобы
 * стартапный конвейер (owner БД, миграции, dayState) прошёл под гостем.
 */
export function enterGuestMode(): void {
  pb.authStore.clear()
  if (typeof window !== 'undefined') localStorage.setItem(MODE_KEY, 'guest')
  void Preferences.set({ key: MODE_KEY, value: 'guest' })
  session.user = { id: GUEST_OWNER, email: '' }
  window.location.hash = '#/today'
  window.location.reload()
}

/** Стартап: если сохранён гостевой режим — поднимаем гостевую сессию. */
export function initGuestIfNeeded(): void {
  if (session.user) return
  const guest = Capacitor.isNativePlatform()
    ? nativeGuestMode
    : typeof window !== 'undefined' && localStorage.getItem(MODE_KEY) === 'guest'
  if (guest) session.user = { id: GUEST_OWNER, email: '' }
}

export async function logout(): Promise<void> {
  pb.authStore.clear()
  // выход из гостевого режима тоже: следующий стартап покажет экран входа
  if (typeof window !== 'undefined') {
    localStorage.removeItem(DB_STORAGE_KEY)
    localStorage.removeItem(OWNER_KEY)
    localStorage.removeItem(MODE_KEY)
  }
  await Preferences.remove({ key: MODE_KEY }).catch(() => {})
  window.location.hash = '#/login'
  window.location.reload()
}

/**
 * При старте: если в локальном хранилище данные другого пользователя — стереть.
 * Вызывается до initDb().
 */
export function prepareDbForUser(): void {
  const owner = currentOwner()
  const storedOwner = localStorage.getItem(OWNER_KEY)
  if (!owner) {
    if (storedOwner) {
      localStorage.removeItem(DB_STORAGE_KEY)
      localStorage.removeItem(OWNER_KEY)
    }
    return
  }
  if (storedOwner !== owner) {
    localStorage.removeItem(DB_STORAGE_KEY)
    localStorage.setItem(OWNER_KEY, owner)
  }
}
