// Генерация рингтонов в res/raw: 16-bit PCM mono WAV.
// Запуск: node scripts/gen-sounds.mjs  (из корня web/)
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const OUT = join(dirname(fileURLToPath(import.meta.url)), '../android/app/src/main/res/raw')
const SR = 22050

function wav(name, seconds, fn) {
  const n = Math.floor(seconds * SR)
  const data = new Float32Array(n)
  for (let i = 0; i < n; i++) data[i] = fn(i / SR)
  let peak = 0
  for (const s of data) peak = Math.max(peak, Math.abs(s))
  const scale = peak > 0 ? 0.8 / peak : 0
  const buf = Buffer.alloc(44 + n * 2)
  buf.write('RIFF', 0)
  buf.writeUInt32LE(36 + n * 2, 4)
  buf.write('WAVE', 8)
  buf.write('fmt ', 12)
  buf.writeUInt32LE(16, 16)
  buf.writeUInt16LE(1, 20)
  buf.writeUInt16LE(1, 22)
  buf.writeUInt32LE(SR, 24)
  buf.writeUInt32LE(SR * 2, 28)
  buf.writeUInt16LE(2, 32)
  buf.writeUInt16LE(16, 34)
  buf.write('data', 36)
  buf.writeUInt32LE(n * 2, 40)
  for (let i = 0; i < n; i++) {
    buf.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(data[i] * scale * 32767))), 44 + i * 2)
  }
  mkdirSync(OUT, { recursive: true })
  writeFileSync(join(OUT, name), buf)
  console.log(name, seconds + 's')
}

// --- утилиты синтеза ---

/** мягкий «лофай»-тон: синус + лёгкая расстройка, длинное затухание */
function softTone(freq, t, t0, dec, amp) {
  const dt = t - t0
  if (dt < 0) return 0
  const attack = Math.min(1, dt / 0.03)
  const env = attack * Math.exp(-dt * dec)
  return (
    Math.sin(2 * Math.PI * freq * dt) * 0.8 +
    Math.sin(2 * Math.PI * (freq + 2.4) * dt) * 0.22 +
    Math.sin(2 * Math.PI * freq * 2 * dt) * 0.1
  ) * env * amp
}

/** аккорд: несколько тонов с одним envelope */
function chord(freqs, t, t0, dec, amp) {
  let s = 0
  for (const f of freqs) s += softTone(f, t, t0, dec, amp / freqs.length)
  return s
}

/**
 * Генератор длинной LoFi-мелодии: прогрессия аккордов, на каждый — арпеджио
 * тёплых тонов с длинными затуханиями. Только низко-средние частоты, без резких
 * атак — будильник будит мягко.
 */
function lofiMelody(seconds, bpm, progression, pattern) {
  const beat = 60 / bpm
  const bar = beat * 4
  const events = []
  progression.forEach((chordFreqs, ci) => {
    pattern.forEach(([beatOff, toneIdx, dec, amp]) => {
      events.push({
        t0: ci * bar + beatOff * beat,
        freq: chordFreqs[toneIdx % chordFreqs.length],
        dec,
        amp,
      })
    })
  })
  events.sort((a, b) => a.t0 - b.t0)
  // глобальный fade-out последних 2 секунд
  return (t) => {
    let s = 0
    for (const e of events) {
      if (t - e.t0 > 6) continue
      s += softTone(e.freq, t, e.t0, e.dec, e.amp)
    }
    const fadeStart = seconds - 2.5
    const fade = t > fadeStart ? Math.max(0, 1 - (t - fadeStart) / 2.5) : 1
    return s * fade
  }
}

// Am7 → Fmaj7 → Cmaj7 → G6 — спокойное утро (~17 с)
wav('lofi_morning.wav', 17, lofiMelody(17, 66, [
  [220.0, 261.63, 329.63, 392.0],   // Am7
  [174.61, 220.0, 261.63, 329.63],  // Fmaj7
  [130.81, 196.0, 246.94, 329.63],  // Cmaj7 (низ)
  [196.0, 246.94, 293.66, 392.0],   // G6
], [
  [0, 1, 1.1, 0.9], [1, 2, 1.0, 0.75], [2, 3, 0.9, 0.8], [3, 2, 1.2, 0.7],
  [0.5, 0, 1.4, 0.5], [2.5, 1, 1.4, 0.45],
]))

// Cmaj9 → G/B → Am9 → Em9 — «облака», мягче и медленнее (~19 с)
wav('lofi_clouds.wav', 19, lofiMelody(19, 58, [
  [130.81, 164.81, 196.0, 246.94, 293.66], // Cmaj9
  [123.47, 196.0, 246.94, 293.66],         // G/B
  [110.0, 164.81, 196.0, 261.63, 329.63],  // Am9
  [82.41, 164.81, 196.0, 246.94, 293.66],  // Em9
], [
  [0, 2, 0.9, 0.85], [1.5, 3, 0.8, 0.7], [2.5, 4, 1.0, 0.75], [3.5, 3, 1.1, 0.6],
  [0.75, 0, 1.5, 0.5],
]))

// Dm9 → G13 → Cmaj9 → A7 — вечерний отбой, самый тихий (~17 с)
wav('lofi_night.wav', 17, lofiMelody(17, 62, [
  [146.83, 174.61, 220.0, 261.63, 329.63], // Dm9
  [98.0, 196.0, 233.08, 293.66, 349.23],   // G13
  [130.81, 164.81, 196.0, 246.94, 293.66], // Cmaj9
  [110.0, 138.59, 220.0, 261.63, 329.63],  // A7
], [
  [0, 1, 1.2, 0.8], [2, 2, 1.0, 0.7], [3, 3, 1.2, 0.65],
  [1, 0, 1.6, 0.45], [2.5, 4, 1.5, 0.4],
]))

// LoFi-колокольчик: два мягких затухающих тона (~1.5 с) — уведомления
wav('lofi_chime.wav', 1.5, (t) => {
  const note = (f, t0, dec) => {
    const dt = t - t0
    if (dt < 0) return 0
    const attack = Math.min(1, dt / 0.015)
    const env = attack * Math.exp(-dt * dec)
    return (
      Math.sin(2 * Math.PI * f * dt) * 0.8 +
      Math.sin(2 * Math.PI * (f + 2.7) * dt) * 0.25 +
      Math.sin(2 * Math.PI * f * 2 * dt) * 0.12
    ) * env
  }
  return note(659.25, 0, 3.2) + note(493.88, 0.45, 2.8) * 0.9
})

// LoFi-щелчок: короткий призвук-плак с падением тона (~1.1 с) — уведомления
wav('lofi_pluck.wav', 1.1, (t) => {
  const f = 396 - 14 * Math.min(1, t * 5)
  const env = Math.min(1, t / 0.006) * Math.exp(-t * 5.2)
  return (
    Math.sin(2 * Math.PI * f * t) * 0.9 +
    Math.sin(2 * Math.PI * f * 2.01 * t) * 0.15
  ) * env
})
