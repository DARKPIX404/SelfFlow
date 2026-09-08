<script lang="ts">
  import { haptic } from './haptics'
  import Icon from './Icon.svelte'

  interface Props {
    /** 'HH:mm' или null */
    value: string | null
    onchange: (value: string | null) => void
    label?: string
    allowClear?: boolean
  }

  let { value, onchange, label, allowClear = true }: Props = $props()

  let open = $state(false)
  let draftH = $state(8)
  let draftM = $state(0)

  const ITEM_H = 40
  const VISIBLE = 5 // нечётное, центральная строка — выбранная
  const PAD = Math.floor(VISIBLE / 2) * ITEM_H

  const pad2 = (n: number) => String(n).padStart(2, '0')

  function openSheet() {
    const [h, m] = (value ?? '08:00').split(':').map(Number)
    draftH = h
    draftM = m ?? 0
    open = true
  }

  function snap(el: HTMLElement, target: number) {
    el.scrollTo({ top: target * ITEM_H, behavior: 'instant' as ScrollBehavior })
  }

  function onHourMount(el: HTMLElement) {
    snap(el, draftH)
  }

  function onMinMount(el: HTMLElement) {
    snap(el, Math.floor(draftM / 5))
  }

  let hourTimer: ReturnType<typeof setTimeout> | null = null
  let minTimer: ReturnType<typeof setTimeout> | null = null

  function onWheelScroll(e: Event) {
    const el = e.currentTarget as HTMLElement
    if (hourTimer) clearTimeout(hourTimer)
    hourTimer = setTimeout(() => {
      const idx = Math.round(el.scrollTop / ITEM_H)
      const clamped = Math.max(0, Math.min(23, idx))
      if (clamped !== draftH) {
        draftH = clamped
        haptic('selection')
      }
      el.scrollTo({ top: clamped * ITEM_H, behavior: 'smooth' })
    }, 60)
  }

  function onMinScroll(e: Event) {
    const el = e.currentTarget as HTMLElement
    if (minTimer) clearTimeout(minTimer)
    minTimer = setTimeout(() => {
      const idx = Math.round(el.scrollTop / ITEM_H)
      const clamped = Math.max(0, Math.min(11, idx))
      if (clamped * 5 !== draftM) {
        draftM = clamped * 5
        haptic('selection')
      }
      el.scrollTo({ top: clamped * ITEM_H, behavior: 'smooth' })
    }, 60)
  }

  function confirm() {
    haptic('light')
    onchange(`${pad2(draftH)}:${pad2(draftM)}`)
    open = false
  }

  function clear() {
    haptic('light')
    onchange(null)
    open = false
  }

  const hours = Array.from({ length: 24 }, (_, i) => pad2(i))
  const mins = Array.from({ length: 12 }, (_, i) => pad2(i * 5))
</script>

<button type="button" class="time-trigger" onclick={openSheet}>
  <Icon name="clock" size={18} />
  <span class="tv">{value ?? '—:—'}</span>
  {#if allowClear && value}
    <span
      class="clear"
      role="button"
      tabindex="0"
      aria-label="Очистить"
      onkeydown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          e.stopPropagation()
          haptic('light')
          onchange(null)
        }
      }}
      onclick={(e) => {
        e.stopPropagation()
        haptic('light')
        onchange(null)
      }}
    >
      <Icon name="x" size={14} />
    </span>
  {/if}
</button>
{#if label}<span class="hint">{label}</span>{/if}

{#if open}
  <div class="backdrop" role="presentation" onclick={() => (open = false)}></div>
  <div class="wheel-sheet" role="dialog" aria-modal="true" aria-label="Выбор времени">
    <div class="handle"></div>
    <div class="wheels">
      <div class="wheel" use:onHourMount onscroll={onWheelScroll}>
        <div class="spacer" style="height: {PAD}px"></div>
        {#each hours as h (h)}
          <div class="cell" class:sel={Number(h) === draftH}>{h}</div>
        {/each}
        <div class="spacer" style="height: {PAD}px"></div>
      </div>
      <span class="colon">:</span>
      <div class="wheel" use:onMinMount onscroll={onMinScroll}>
        <div class="spacer" style="height: {PAD}px"></div>
        {#each mins as m (m)}
          <div class="cell" class:sel={Number(m) === draftM}>{m}</div>
        {/each}
        <div class="spacer" style="height: {PAD}px"></div>
      </div>
    </div>
    <div class="sheet-actions">
      {#if allowClear}
        <button type="button" class="btn ghost" onclick={clear}>Убрать</button>
      {/if}
      <button type="button" class="btn" onclick={confirm}>Готово</button>
    </div>
  </div>
{/if}

<style>
  .time-trigger {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
    color: var(--text);
    font-size: 15px;
    padding: 10px 14px;
    min-height: 44px;
    cursor: pointer;
    font-variant-numeric: tabular-nums;
  }
  .tv {
    min-width: 44px;
    text-align: left;
  }
  .clear {
    display: inline-flex;
    color: var(--text-muted);
    padding: 4px;
  }
  .hint {
    font-size: 12px;
    color: var(--text-muted);
  }
  .backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    z-index: 90;
  }
  .wheel-sheet {
    position: fixed;
    left: 50%;
    bottom: 0;
    translate: -50% 0;
    width: 100%;
    max-width: 480px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-bottom: none;
    border-radius: 24px 24px 0 0;
    z-index: 100;
    padding: 10px 20px calc(20px + env(safe-area-inset-bottom, 0px));
    animation: rise 280ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .handle {
    width: 40px;
    height: 4px;
    border-radius: 2px;
    background: var(--bg-deep);
    margin: 0 auto 8px;
  }
  .wheels {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    position: relative;
    height: 200px;
    overflow: hidden;
  }
  .wheels::before {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    translate: 0 -50%;
    height: 40px;
    background: var(--accent-soft);
    border-radius: 10px;
    pointer-events: none;
    z-index: 0;
  }
  .wheel {
    height: 200px;
    overflow-y: auto;
    scroll-snap-type: y proximity;
    scrollbar-width: none;
    width: 72px;
    position: relative;
    z-index: 1;
    mask-image: linear-gradient(to bottom, transparent, black 35%, black 65%, transparent);
    -webkit-mask-image: linear-gradient(to bottom, transparent, black 35%, black 65%, transparent);
  }
  .wheel::-webkit-scrollbar {
    display: none;
  }
  .cell {
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    font-variant-numeric: tabular-nums;
    color: var(--text-muted);
  }
  .cell.sel {
    color: var(--text);
    font-weight: 700;
  }
  .colon {
    font-size: 18px;
    font-weight: 700;
    color: var(--text-2);
    position: relative;
    z-index: 1;
  }
  .sheet-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 12px;
  }
  .btn {
    border: none;
    border-radius: 12px;
    padding: 11px 20px;
    font-size: 14px;
    font-weight: 600;
    min-height: 44px;
    cursor: pointer;
    background: var(--accent);
    color: var(--text);
  }
  .btn.ghost {
    background: var(--surface-2);
    color: var(--text-2);
  }
  @keyframes rise {
    from { transform: translateY(100%); }
    to { transform: translateY(0); }
  }
  @media (prefers-reduced-motion: reduce) {
    .wheel-sheet { animation: none; }
  }
</style>
