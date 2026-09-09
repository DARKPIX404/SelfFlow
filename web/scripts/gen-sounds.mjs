// Генерация звуков уведомлений в res/raw: 16-bit PCM mono WAV.
// Мелодии будильника — внешние mp3 (res/raw/*.mp3), этим скриптом не генерятся.
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
