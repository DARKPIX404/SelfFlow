<script lang="ts">
  import { session } from '$lib/auth/session.svelte'
  import { focusSessions, routines } from '$lib/db/repositories'
  import { pop } from '$lib/nav.svelte'
  import Chip from '$lib/ui/Chip.svelte'
  import DropList from '$lib/ui/DropList.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import ProgressRing from '$lib/ui/ProgressRing.svelte'
  import { haptic } from '$lib/ui/haptics'

  type Phase = 'setup' | 'running' | 'paused' | 'done'

  const DURATIONS = [15, 25, 45, 60]

  let phase = $state<Phase>('setup')
  let plannedMin = $state(25)
  let routineId = $state<string | null>(null)

  let startedAt = 0
  let pausedAccum = 0
  let pausedAt = 0
  let remainingMs = $state(25 * 60_000)
  let timer: ReturnType<typeof setInterval> | null = null
  let baseTitle = document.title

  const routineOptions = $derived(
    session.user
      ? routines
          .list(session.user.id)
          .map((r) => ({ value: r.id, label: r.title }))
      : [],
  )

  let mmss = $derived.by(() => {
    const total = Math.max(0, Math.ceil(remainingMs / 1000))
    const m = Math.floor(total / 60)
    const s = total % 60
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  })

  let progress = $derived(1 - remainingMs / (plannedMin * 60_000))

  function tick() {
    if (phase !== 'running') return
    remainingMs = Math.max(0, endAt() - Date.now())
    if (remainingMs <= 0) finish()
  }

  function endAt(): number {
    return startedAt + plannedMin * 60_000 + pausedAccum
  }

  function ensureAudio(): AudioContext | null {
    try {
      return new AudioContext()
    } catch {
      return null
    }
  }

  function playDoneSound() {
    const ctx = ensureAudio()
    if (!ctx) return
    const t0 = ctx.currentTime
    for (const [freq, delay] of [
      [880, 0],
      [1174.7, 0.18],
    ] as const) {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.frequency.value = freq
      osc.type = 'sine'
      gain.gain.setValueAtTime(0.0001, t0 + delay)
      gain.gain.exponentialRampToValueAtTime(0.25, t0 + delay + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, t0 + delay + 0.35)
      osc.connect(gain).connect(ctx.destination)
      osc.start(t0 + delay)
      osc.stop(t0 + delay + 0.4)
    }
  }

  function recordSession(minutes: number) {
    const user = session.user
    if (!user) return
    focusSessions.create(user.id, {
      started_at: new Date(startedAt).toISOString().replace('T', ' '),
      minutes,
      task_id: null,
      routine_id: routineId,
    })
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  async function start() {
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      try {
        await Notification.requestPermission()
      } catch {
        // отказ — остаёмся на звуке и заголовке вкладки
      }
    }
    ensureAudio()?.resume().catch(() => {})
    startedAt = Date.now()
    pausedAccum = 0
    remainingMs = plannedMin * 60_000
    phase = 'running'
    haptic('medium')
    restartTimer()
  }

  function restartTimer() {
    if (timer) clearInterval(timer)
    timer = setInterval(tick, 250)
  }

  function pause() {
    phase = 'paused'
    pausedAt = Date.now()
    haptic('light')
  }

  function resume() {
    pausedAccum += Date.now() - pausedAt
    phase = 'running'
    haptic('light')
  }

  /** Стоп до завершения: фиксируем фактически отработанные минуты. */
  function stop() {
    const elapsedMin = Math.max(1, Math.round((plannedMin * 60_000 - remainingMs) / 60_000))
    recordSession(elapsedMin)
    cleanup()
    phase = 'setup'
    remainingMs = plannedMin * 60_000
    haptic('light')
  }

  function finish() {
    recordSession(plannedMin)
    playDoneSound()
    haptic('notification')
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted' && document.hidden) {
      try {
        new Notification('Фокус-сессия завершена', { body: `${plannedMin} минут — отличная работа` })
      } catch {
        // headless/ограниченные окружения — игнорируем
      }
    }
    document.title = '✔ Сессия завершена — SelfFlow'
    cleanup()
    phase = 'done'
  }

  function cleanup() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function doneClose() {
    document.title = baseTitle
    phase = 'setup'
    remainingMs = plannedMin * 60_000
  }

  function back() {
    if (phase === 'running' || phase === 'paused') stop()
    pop()
  }
</script>

<svelte:window
  onbeforeunload={() => {
    if (phase === 'running' || phase === 'paused') stop()
  }}
/>

<div class="focus-screen">
  <header class="focus-header">
    <button type="button" class="icon-btn" aria-label="Назад" onclick={back}>
      <Icon name="chevron-left" size={22} />
    </button>
    <span class="focus-title">Фокус</span>
    <span class="focus-spacer"></span>
  </header>

  <div class="focus-body">
    {#if phase === 'setup'}
      <div class="setup">
        <h2 class="setup-label">Длительность сессии</h2>
        <div class="dur-chips">
          {#each DURATIONS as d (d)}
            <button
              type="button"
              class="dur"
              class:sel={plannedMin === d}
              onclick={() => { haptic('selection'); plannedMin = d; remainingMs = d * 60_000 }}
            >
              {d}<span class="dur-unit">мин</span>
            </button>
          {/each}
        </div>
        <div class="setup-task">
          <span class="setup-label">Рутина (необязательно)</span>
          <DropList options={routineOptions} value={routineId} onchange={(v) => (routineId = v)} placeholder="Без привязки" />
        </div>
        <button type="button" class="start-btn" onclick={start}>
          <Icon name="play" size={20} /> Начать фокус
        </button>
      </div>
    {:else}
      <div class="timer">
        <div class="ring-wrap">
          <ProgressRing value={Math.max(0, Math.min(1, progress))} size={264} stroke={12} />
          <span class="timer-time">{mmss}</span>
        </div>
        <span class="timer-state">{phase === 'paused' ? 'Пауза' : 'Фокус'} · {plannedMin} мин</span>
        {#if routineId}
          <span class="timer-task">{routineOptions.find((o) => o.value === routineId)?.label ?? ''}</span>
        {/if}
        <div class="timer-controls">
          {#if phase === 'running'}
            <button type="button" class="ctl" aria-label="Пауза" onclick={pause}>
              <Icon name="pause" size={26} />
            </button>
          {:else}
            <button type="button" class="ctl" aria-label="Продолжить" onclick={resume}>
              <Icon name="play" size={26} />
            </button>
          {/if}
          <button type="button" class="ctl stop" aria-label="Стоп" onclick={stop}>
            <Icon name="x" size={24} />
          </button>
        </div>
      </div>
    {/if}
  </div>
</div>

{#if phase === 'done'}
  <div class="done-overlay" role="presentation">
    <div class="done-overlay-inner">
      <span class="done-ring big"><Icon name="check" size={56} strokeWidth={2.2} /></span>
      <h2 class="done-title">Сессия завершена</h2>
      <p class="done-sub">{plannedMin} минут в фокусе</p>
      <button type="button" class="start-btn overlay-btn" onclick={doneClose}>Готово</button>
    </div>
  </div>
{/if}

<style>
  .focus-screen {
    min-height: 100dvh;
    background: var(--bg-deep);
    display: flex;
    flex-direction: column;
    padding-bottom: 96px;
  }
  .focus-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: calc(12px + env(safe-area-inset-top, 0px)) 8px 12px;
  }
  .focus-title {
    font-size: 17px;
    font-weight: 600;
    color: var(--text);
  }
  .focus-spacer {
    width: 42px;
  }
  .focus-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 24px 20px;
  }
  .setup {
    width: 100%;
    max-width: 360px;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  .setup-label {
    font-size: 13px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
  }
  .dur-chips {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 10px;
  }
  .dur {
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text-2);
    border-radius: 14px;
    padding: 14px 0;
    font-size: 20px;
    font-weight: 700;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    min-height: 64px;
    transition: background 120ms, color 120ms, border-color 120ms;
  }
  .dur-unit {
    font-size: 11px;
    font-weight: 500;
    color: var(--text-muted);
  }
  .dur.sel {
    background: var(--accent-soft);
    border-color: transparent;
    color: var(--accent);
  }
  .dur.sel .dur-unit {
    color: var(--accent);
    opacity: 0.7;
  }
  .setup-task {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .start-btn {
    border: none;
    background: var(--accent);
    color: var(--text);
    font-size: 16px;
    font-weight: 700;
    border-radius: 16px;
    padding: 16px;
    min-height: 54px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
  }
  .timer {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 18px;
    width: 100%;
  }
  .ring-wrap {
    position: relative;
    width: 264px;
    height: 264px;
  }
  .timer-time {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 56px;
    font-weight: 800;
    letter-spacing: -0.02em;
    color: var(--text);
    font-variant-numeric: tabular-nums;
  }
  .timer-state {
    font-size: 13px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--text-muted);
  }
  .timer-task {
    font-size: 14px;
    color: var(--text-2);
    text-align: center;
    max-width: 280px;
  }
  .timer-controls {
    display: flex;
    gap: 16px;
    margin-top: 8px;
  }
  .ctl {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: transform 120ms;
  }
  .ctl:active {
    transform: scale(0.93);
  }
  .ctl.stop {
    color: var(--accent);
    background: var(--accent-soft);
    border-color: transparent;
  }
  .done-ring {
    width: 88px;
    height: 88px;
    border-radius: 50%;
    background: var(--accent-soft);
    color: var(--accent);
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  .done-ring.big {
    width: 112px;
    height: 112px;
  }
  .done-title {
    font-size: 24px;
    font-weight: 800;
    color: var(--text);
  }
  .done-sub {
    font-size: 14px;
    color: var(--text-2);
  }
  .done-overlay {
    position: fixed;
    inset: 0;
    z-index: 200;
    background: color-mix(in srgb, var(--bg-deep) 88%, var(--accent) 12%);
    display: flex;
    align-items: center;
    justify-content: center;
    animation: fadeIn 300ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .done-overlay-inner {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    padding: 24px;
    text-align: center;
  }
  .overlay-btn {
    min-width: 200px;
    margin-top: 12px;
  }
  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  @media (prefers-reduced-motion: reduce) {
    .done-overlay { animation: none; }
  }
</style>
