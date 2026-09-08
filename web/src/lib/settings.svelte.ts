import { settings } from './db/repositories'
import { getDb } from './db'
import { session } from './auth/session.svelte'

/** Простой key-value поверх settings-репозитория (значения — строки) */

function owner(): string | null {
  return session.user?.id ?? null
}

export function getSetting(key: string): string | null {
  const o = owner()
  if (!o) return null
  try {
    const row = getDb().queryOne<{ value: string | null }>(
      'SELECT value FROM settings WHERE owner = ? AND key = ? AND deleted = 0',
      [o, key],
    )
    return row?.value ?? null
  } catch {
    // db ещё не инициализирован (модуль загрузился раньше initDb при
    // восстановленной сессии) — вернём значение по умолчанию
    return null
  }
}

export function setSetting(key: string, value: string | null): void {
  const o = owner()
  if (!o) return
  const db = getDb()
  const existing = db.queryOne<{ id: string }>(
    'SELECT id FROM settings WHERE owner = ? AND key = ? AND deleted = 0',
    [o, key],
  )
  if (existing) {
    settings.update(existing.id, { key, value } as never)
  } else {
    settings.create(o, { key, value } as never)
  }
}

// --- Тема ---

export type Theme = 'dark' | 'light'
export const theme = $state<{ value: Theme }>({ value: getSetting('theme') === 'light' ? 'light' : 'dark' })

export function applyTheme(value: Theme): void {
  theme.value = value
  document.documentElement.dataset.theme = value
  setSetting('theme', value)
}

export function initTheme(): void {
  document.documentElement.dataset.theme = theme.value
}

// --- PIN (WebCrypto PBKDF2, храним hash+salt в settings) ---

const PBKDF2_ITERATIONS = 100_000

function toBase64(u8: Uint8Array): string {
  let bin = ''
  for (const b of u8) bin += String.fromCharCode(b)
  return btoa(bin)
}

function fromBase64(b64: string): Uint8Array {
  const bin = atob(b64)
  const u8 = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i)
  return u8
}

async function pbkdf2(pin: string, salt: Uint8Array): Promise<Uint8Array> {
  const enc = new TextEncoder()
  const key = await crypto.subtle.importKey('raw', enc.encode(pin), 'PBKDF2', false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: salt as BufferSource, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    key,
    256,
  )
  return new Uint8Array(bits)
}

export function pinEnabled(): boolean {
  return getSetting('pin_hash') !== null
}

export async function setPin(pin: string): Promise<void> {
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const hash = await pbkdf2(pin, salt)
  setSetting('pin_salt', toBase64(salt))
  setSetting('pin_hash', toBase64(hash))
}

export async function verifyPin(pin: string): Promise<boolean> {
  const saltB64 = getSetting('pin_salt')
  const hashB64 = getSetting('pin_hash')
  if (!saltB64 || !hashB64) return false
  const hash = await pbkdf2(pin, fromBase64(saltB64))
  const expected = fromBase64(hashB64)
  if (hash.length !== expected.length) return false
  let diff = 0
  for (let i = 0; i < hash.length; i++) diff |= hash[i] ^ expected[i]
  return diff === 0
}

export function clearPin(): void {
  setSetting('pin_salt', null)
  setSetting('pin_hash', null)
}

// --- Оверлей-блокировка PIN ---

export const pinLock = $state({ locked: false })

// --- Прочие ключи (будильник, звук) — читаются напрямую через getSetting ---

// --- Звук будильника (ключи совпадают с res/raw нативного проекта) ---

export interface RingtoneOption {
  key: string
  label: string
}

export const ringtoneOptions: RingtoneOption[] = [
  { key: 'morning_light', label: 'Мягкий рассвет' },
  { key: 'digital_beep', label: 'Цифровой сигнал' },
  { key: 'classic_bell', label: 'Классический звонок' },
  { key: 'notification_soft', label: 'Мягкое уведомление' },
]

export function alarmSoundKey(): string {
  return getSetting('alarm_sound') ?? 'morning_light'
}

const RINGTONES: Record<string, (ctx: AudioContext, gain: GainNode) => void> = {
  morning_light: (ctx, gain) => {
    for (let i = 0; i < 3; i++) {
      const osc = ctx.createOscillator()
      osc.type = 'sine'
      osc.frequency.value = 660
      const g = ctx.createGain()
      g.gain.setValueAtTime(0, ctx.currentTime + i * 0.35)
      g.gain.linearRampToValueAtTime(0.5, ctx.currentTime + i * 0.35 + 0.05)
      g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.35 + 0.3)
      osc.connect(g).connect(gain)
      osc.start(ctx.currentTime + i * 0.35)
      osc.stop(ctx.currentTime + i * 0.35 + 0.32)
    }
  },
  digital_beep: (ctx, gain) => {
    for (let i = 0; i < 2; i++) {
      const osc = ctx.createOscillator()
      osc.type = 'square'
      osc.frequency.value = 440
      const g = ctx.createGain()
      g.gain.setValueAtTime(0, ctx.currentTime + i * 0.5)
      g.gain.linearRampToValueAtTime(0.25, ctx.currentTime + i * 0.5 + 0.02)
      g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.5 + 0.45)
      osc.connect(g).connect(gain)
      osc.start(ctx.currentTime + i * 0.5)
      osc.stop(ctx.currentTime + i * 0.5 + 0.48)
    }
  },
  classic_bell: (ctx, gain) => {
    for (let i = 0; i < 3; i++) {
      const osc = ctx.createOscillator()
      osc.type = 'triangle'
      osc.frequency.value = 880
      const g = ctx.createGain()
      g.gain.setValueAtTime(0, ctx.currentTime + i * 0.4)
      g.gain.linearRampToValueAtTime(0.4, ctx.currentTime + i * 0.4 + 0.02)
      g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.4 + 0.38)
      osc.connect(g).connect(gain)
      osc.start(ctx.currentTime + i * 0.4)
      osc.stop(ctx.currentTime + i * 0.4 + 0.4)
    }
  },
  notification_soft: (ctx, gain) => {
    const osc = ctx.createOscillator()
    osc.type = 'sine'
    osc.frequency.setValueAtTime(440, ctx.currentTime)
    osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.9)
    const g = ctx.createGain()
    g.gain.setValueAtTime(0, ctx.currentTime)
    g.gain.linearRampToValueAtTime(0.4, ctx.currentTime + 0.1)
    g.gain.linearRampToValueAtTime(0, ctx.currentTime + 1.0)
    osc.connect(g).connect(gain)
    osc.start()
    osc.stop(ctx.currentTime + 1.05)
  },
}

export function playRingtone(key: string): void {
  const fn = RINGTONES[key]
  if (!fn) return
  const ctx = new AudioContext()
  const gain = ctx.createGain()
  gain.gain.value = 0.6
  gain.connect(ctx.destination)
  fn(ctx, gain)
  setTimeout(() => void ctx.close(), 2000)
}
