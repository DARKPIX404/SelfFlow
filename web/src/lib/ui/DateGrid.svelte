<script lang="ts">
  import { haptic } from './haptics'
  import Icon from './Icon.svelte'

  interface Props {
    /** YYYY-MM-DD или null */
    value: string | null
    onchange: (value: string | null) => void
    label?: string
    /** даты (YYYY-MM-DD) с точками-событиями */
    markedDates?: string[]
    allowClear?: boolean
  }

  let { value, onchange, label, markedDates = [], allowClear = true }: Props = $props()

  let open = $state(false)
  let viewYear = $state(new Date().getFullYear())
  let viewMonth = $state(new Date().getMonth())

  const MONTHS = [
    'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
    'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
  ]
  const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

  const toStr = (d: Date) => {
    const off = d.getTimezoneOffset()
    return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10)
  }

  function parse(s: string): { y: number; m: number; d: number } {
    const [y, m, d] = s.split('-').map(Number)
    return { y, m: m - 1, d }
  }

  function openGrid() {
    if (value) {
      const p = parse(value)
      viewYear = p.y
      viewMonth = p.m
    }
    open = true
  }

  let cells = $derived.by(() => {
    const first = new Date(viewYear, viewMonth, 1)
    const startOffset = (first.getDay() + 6) % 7 // понедельник = 0
    const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate()
    const out: ({ day: number; iso: string } | null)[] = []
    for (let i = 0; i < startOffset; i++) out.push(null)
    for (let d = 1; d <= daysInMonth; d++) {
      out.push({ day: d, iso: toStr(new Date(viewYear, viewMonth, d)) })
    }
    return out
  })

  function shiftMonth(delta: number) {
    haptic('selection')
    const m = viewMonth + delta
    viewMonth = ((m % 12) + 12) % 12
    viewYear += Math.floor(m / 12)
  }

  function pick(iso: string) {
    haptic('light')
    onchange(iso)
    open = false
  }

  const markedSet = $derived(new Set(markedDates))
  const fmt = (iso: string | null) => {
    if (!iso) return '—'
    const p = parse(iso)
    return `${p.d} ${MONTHS[p.m].slice(0, 3).toLowerCase()} ${p.y}`
  }
</script>

<button type="button" class="date-trigger" onclick={openGrid}>
  <Icon name="calendar-clock" size={18} />
  <span class="dv">{fmt(value)}</span>
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
  <div class="grid-sheet" role="dialog" aria-modal="true" aria-label="Выбор даты">
    <div class="handle"></div>
    <div class="month-nav">
      <button type="button" class="nav-btn" aria-label="Предыдущий месяц" onclick={() => shiftMonth(-1)}>
        <Icon name="chevron-left" size={20} />
      </button>
      <span class="month-title">{MONTHS[viewMonth]} {viewYear}</span>
      <button type="button" class="nav-btn" aria-label="Следующий месяц" onclick={() => shiftMonth(1)}>
        <Icon name="chevron-right" size={20} />
      </button>
    </div>
    <div class="grid">
      {#each WEEKDAYS as wd (wd)}
        <span class="wd">{wd}</span>
      {/each}
      {#each cells as cell, i (cell ? cell.iso : 'pad-' + i)}
        {#if cell}
          <button
            type="button"
            class="day"
            class:selected={value === cell.iso}
            class:today={toStr(new Date()) === cell.iso}
            onclick={() => pick(cell.iso)}
          >
            <span>{cell.day}</span>
            {#if markedSet.has(cell.iso)}
              <i class="dot"></i>
            {:else}
              <i class="dot invisible"></i>
            {/if}
          </button>
        {:else}
          <span class="day empty"></span>
        {/if}
      {/each}
    </div>
    <div class="sheet-actions">
      {#if allowClear}
        <button
          type="button"
          class="btn ghost"
          onclick={() => {
            haptic('light')
            onchange(null)
            open = false
          }}
        >
          Убрать
        </button>
      {/if}
      <button type="button" class="btn" onclick={() => (open = false)}>Готово</button>
    </div>
  </div>
{/if}

<style>
  .date-trigger {
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
  .grid-sheet {
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
    margin: 0 auto 10px;
  }
  .month-nav {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }
  .nav-btn {
    border: none;
    background: var(--surface-2);
    color: var(--text-2);
    border-radius: 10px;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
  }
  .month-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--text);
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 2px;
  }
  .wd {
    text-align: center;
    font-size: 12px;
    font-weight: 500;
    color: var(--text-muted);
    padding: 6px 0;
  }
  .day {
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 14px;
    border-radius: 50%;
    aspect-ratio: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    gap: 2px;
    transition: background 120ms;
  }
  .day:hover {
    background: var(--surface-2);
  }
  .day.selected {
    background: var(--accent-soft);
    color: var(--accent);
    font-weight: 700;
  }
  .day.today > span {
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .day.empty {
    pointer-events: none;
  }
  .dot {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--accent);
  }
  .dot.invisible {
    background: transparent;
  }
  .sheet-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 14px;
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
    .grid-sheet { animation: none; }
  }
</style>
