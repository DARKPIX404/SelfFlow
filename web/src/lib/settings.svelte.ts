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
  { key: 'alarm_standard', label: 'Стандартный будильник' },
  { key: 'lofi_chime', label: 'LoFi: колокольчик' },
  { key: 'lofi_pluck', label: 'LoFi: щелчок' },
  { key: 'digital_beep', label: 'Цифровой сигнал' },
  { key: 'classic_bell', label: 'Классический звонок' },
]

const RINGTONE_KEYS = new Set(ringtoneOptions.map((o) => o.key))
// ключи старых пресетов → ближайший из новых (установленные звуки не молчат)
const LEGACY_RINGTONES: Record<string, string> = {
  morning_light: 'lofi_chime',
  notification_soft: 'lofi_pluck',
}

export function alarmSoundKey(): string {
  const key = getSetting('alarm_sound') ?? 'alarm_standard'
  if (RINGTONE_KEYS.has(key)) return key
  return LEGACY_RINGTONES[key] ?? 'alarm_standard'
}

const RINGTONES: Record<string, (ctx: AudioContext, gain: GainNode) => void> = {
  alarm_standard: (ctx, gain) => {
    // серия коротких настойчивых бипов, как у классического цифрового будильника
    for (let i = 0; i < 6; i++) {
      const t0 = ctx.currentTime + i * 0.4
      for (const [freq, amp] of [
        [1046.5, 0.5],
        [2093, 0.12],
        [523.25, 0.14],
      ] as const) {
        const osc = ctx.createOscillator()
        osc.type = 'sine'
        osc.frequency.value = freq
        const g = ctx.createGain()
        g.gain.setValueAtTime(0, t0)
        g.gain.linearRampToValueAtTime(amp, t0 + 0.008)
        g.gain.exponentialRampToValueAtTime(0.001, t0 + 0.22)
        osc.connect(g).connect(gain)
        osc.start(t0)
        osc.stop(t0 + 0.24)
      }
    }
  },
  lofi_chime: (ctx, gain) => {
    const note = (freq: number, t0: number, dec: number, amp: number) => {
      for (const detune of [0, 2.7]) {
        const osc = ctx.createOscillator()
        osc.type = 'sine'
        osc.frequency.value = freq + detune
        const g = ctx.createGain()
        g.gain.setValueAtTime(0, t0)
        g.gain.linearRampToValueAtTime(detune === 0 ? amp : amp * 0.3, t0 + 0.015)
        g.gain.exponentialRampToValueAtTime(0.001, t0 + 1 / dec)
        osc.connect(g).connect(gain)
        osc.start(t0)
        osc.stop(t0 + 1.2)
      }
      const harm = ctx.createOscillator()
      harm.type = 'sine'
      harm.frequency.value = freq * 2
      const hg = ctx.createGain()
      hg.gain.setValueAtTime(0, t0)
      hg.gain.linearRampToValueAtTime(amp * 0.15, t0 + 0.01)
      hg.gain.exponentialRampToValueAtTime(0.001, t0 + 0.5)
      harm.connect(hg).connect(gain)
      harm.start(t0)
      harm.stop(t0 + 0.6)
    }
    note(659.25, ctx.currentTime, 3.2, 0.45)
    note(493.88, ctx.currentTime + 0.45, 2.8, 0.4)
  },
  lofi_pluck: (ctx, gain) => {
    for (const [mult, amp] of [
      [1, 0.5],
      [2.01, 0.09],
    ] as const) {
      const osc = ctx.createOscillator()
      osc.type = 'sine'
      const t0 = ctx.currentTime
      osc.frequency.setValueAtTime(396 * mult, t0)
      osc.frequency.linearRampToValueAtTime(382 * mult, t0 + 0.2)
      const g = ctx.createGain()
      g.gain.setValueAtTime(0, t0)
      g.gain.linearRampToValueAtTime(amp, t0 + 0.006)
      g.gain.exponentialRampToValueAtTime(0.001, t0 + 1.05)
      osc.connect(g).connect(gain)
      osc.start(t0)
      osc.stop(t0 + 1.1)
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
}

export function playRingtone(key: string): void {
  const fn = RINGTONES[key]
  if (!fn) return
  const ctx = new AudioContext()
  const gain = ctx.createGain()
  gain.gain.value = 0.6
  gain.connect(ctx.destination)
  fn(ctx, gain)
  setTimeout(() => void ctx.close(), 3500)
}
