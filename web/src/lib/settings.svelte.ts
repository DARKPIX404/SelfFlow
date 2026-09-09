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

// --- Звуки (ключи совпадают с res/raw нативного проекта; 'system' = системный по умолчанию) ---

export interface RingtoneOption {
  key: string
  label: string
}

/** будильник: системный по умолчанию + длительные LoFi-мелодии */
export const alarmRingtoneOptions: RingtoneOption[] = [
  { key: 'system', label: 'Системный по умолчанию' },
  { key: 'lofi_morning', label: 'LoFi: утро' },
  { key: 'lofi_clouds', label: 'LoFi: облака' },
  { key: 'lofi_night', label: 'LoFi: вечер' },
]

/** уведомления распорядка/задач: короткие мягкие звуки */
export const notificationSoundOptions: RingtoneOption[] = [
  { key: 'system', label: 'Системный по умолчанию' },
  { key: 'lofi_chime', label: 'LoFi: колокольчик' },
  { key: 'lofi_pluck', label: 'LoFi: щелчок' },
]

const ALARM_KEYS = new Set(alarmRingtoneOptions.map((o) => o.key))
const NOTIFICATION_KEYS = new Set(notificationSoundOptions.map((o) => o.key))

// старые/удалённые пресеты → ближайший подходящий новый, чтобы выбранный звук не молчал
const LEGACY_ALARM: Record<string, string> = {
  alarm_standard: 'system',
  digital_beep: 'system',
  classic_bell: 'system',
  morning_light: 'lofi_morning',
  lofi_chime: 'lofi_morning',
  lofi_pluck: 'lofi_night',
}
const LEGACY_NOTIFICATION: Record<string, string> = {
  morning_light: 'lofi_chime',
  notification_soft: 'lofi_chime',
  alarm_standard: 'lofi_pluck',
}

export function alarmSoundKey(): string {
  const key = getSetting('alarm_sound') ?? 'system'
  if (ALARM_KEYS.has(key)) return key
  return LEGACY_ALARM[key] ?? 'system'
}

export function notificationSoundKey(): string {
  const key = getSetting('notification_sound') ?? 'lofi_chime'
  if (NOTIFICATION_KEYS.has(key)) return key
  return LEGACY_NOTIFICATION[key] ?? 'lofi_chime'
}

interface Synth {
  /** длительность превью, мс — для закрытия AudioContext */
  ms: number
  fn: (ctx: AudioContext, gain: GainNode) => void
}

const RINGTONES: Record<string, Synth> = {
  lofi_morning: {
    ms: 7000,
    fn: (ctx, gain) => {
      const chords = [
        [220.0, 261.63, 329.63, 392.0],
        [174.61, 220.0, 261.63, 329.63],
        [130.81, 196.0, 246.94, 329.63],
        [196.0, 246.94, 293.66, 392.0],
      ]
      const beat = 60 / 66
      const tone = (f: number, t0: number, dec: number, amp: number) => {
        for (const [freq, a] of [
          [f, 0.65],
          [f + 2.4, 0.18],
          [f * 2, 0.08],
        ] as const) {
          const osc = ctx.createOscillator()
          osc.type = 'sine'
          osc.frequency.value = freq
          const g = ctx.createGain()
          g.gain.setValueAtTime(0, t0)
          g.gain.linearRampToValueAtTime(amp * a, t0 + 0.03)
          g.gain.exponentialRampToValueAtTime(0.001, t0 + 1 / dec)
          osc.connect(g).connect(gain)
          osc.start(t0)
          osc.stop(t0 + 1.2)
        }
      }
      chords.forEach((chord, ci) => {
        const t0 = ctx.currentTime + ci * beat * 4
        tone(chord[1], t0, 1.1, 0.5)
        tone(chord[2], t0 + beat, 1.0, 0.4)
        tone(chord[3], t0 + beat * 2, 0.9, 0.42)
        tone(chord[2], t0 + beat * 3, 1.2, 0.35)
      })
    },
  },
  lofi_clouds: {
    ms: 7000,
    fn: (ctx, gain) => {
      const chords = [
        [130.81, 164.81, 196.0, 246.94, 293.66],
        [123.47, 196.0, 246.94, 293.66],
        [110.0, 164.81, 196.0, 261.63, 329.63],
        [82.41, 164.81, 196.0, 246.94, 293.66],
      ]
      const beat = 60 / 58
      chords.forEach((chord, ci) => {
        const t0 = ctx.currentTime + ci * beat * 4
        for (const [idx, off, amp] of [
          [2, 0, 0.45],
          [3, beat * 1.5, 0.38],
          [4, beat * 2.5, 0.4],
        ] as const) {
          for (const [mult, a] of [
            [1, 0.7],
            [2, 0.08],
          ] as const) {
            const osc = ctx.createOscillator()
            osc.type = 'sine'
            osc.frequency.value = chord[idx] * mult
            const g = ctx.createGain()
            g.gain.setValueAtTime(0, t0 + off)
            g.gain.linearRampToValueAtTime(amp * a, t0 + off + 0.03)
            g.gain.exponentialRampToValueAtTime(0.001, t0 + off + 1.6)
            osc.connect(g).connect(gain)
            osc.start(t0 + off)
            osc.stop(t0 + off + 1.8)
          }
        }
      })
    },
  },
  lofi_night: {
    ms: 7000,
    fn: (ctx, gain) => {
      const chords = [
        [146.83, 174.61, 220.0, 261.63, 329.63],
        [98.0, 196.0, 233.08, 293.66, 349.23],
        [130.81, 164.81, 196.0, 246.94, 293.66],
        [110.0, 138.59, 220.0, 261.63, 329.63],
      ]
      const beat = 60 / 62
      chords.forEach((chord, ci) => {
        const t0 = ctx.currentTime + ci * beat * 4
        for (const [idx, off, amp, dec] of [
          [1, 0, 0.42, 1.2],
          [2, beat * 2, 0.36, 1.0],
          [3, beat * 3, 0.33, 1.2],
        ] as const) {
          const osc = ctx.createOscillator()
          osc.type = 'sine'
          osc.frequency.value = chord[idx]
          const g = ctx.createGain()
          g.gain.setValueAtTime(0, t0 + off)
          g.gain.linearRampToValueAtTime(amp, t0 + off + 0.03)
          g.gain.exponentialRampToValueAtTime(0.001, t0 + off + 1 / dec)
          osc.connect(g).connect(gain)
          osc.start(t0 + off)
          osc.stop(t0 + off + 1.4)
        }
      })
    },
  },
  lofi_chime: {
    ms: 2000,
    fn: (ctx, gain) => {
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
      }
      note(659.25, ctx.currentTime, 3.2, 0.45)
      note(493.88, ctx.currentTime + 0.45, 2.8, 0.4)
    },
  },
  lofi_pluck: {
    ms: 2000,
    fn: (ctx, gain) => {
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
  },
}

export function playRingtone(key: string): void {
  // 'system' — звук устройства, в WebView его не воспроизвести
  if (key === 'system') return
  const synth = RINGTONES[key]
  if (!synth) return
  const ctx = new AudioContext()
  const gain = ctx.createGain()
  gain.gain.value = 0.6
  gain.connect(ctx.destination)
  synth.fn(ctx, gain)
  setTimeout(() => void ctx.close(), synth.ms + 500)
}
