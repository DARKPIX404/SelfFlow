<script lang="ts" module>
  export interface DropOption<TValue> {
    value: TValue
    label: string
  }
</script>

<script lang="ts" generics="T">
  import Icon from './Icon.svelte'
  import { haptic } from './haptics'

  interface Props {
    options: DropOption<T>[]
    value: T | null
    onchange: (value: T | null) => void
    placeholder?: string
    allowClear?: boolean
  }

  let { options, value, onchange, placeholder = 'Не выбрано', allowClear = true }: Props = $props()

  let open = $state(false)
  let rootEl = $state<HTMLDivElement | null>(null)

  let selected = $derived(options.find((o) => o.value === value) ?? null)

  function toggle() {
    haptic('light')
    open = !open
  }

  function pick(v: T | null) {
    haptic('selection')
    onchange(v)
    open = false
  }

  $effect(() => {
    if (!open) return
    const onDoc = (e: PointerEvent) => {
      if (rootEl && !rootEl.contains(e.target as Node)) open = false
    }
    document.addEventListener('pointerdown', onDoc)
    return () => document.removeEventListener('pointerdown', onDoc)
  })
</script>

<div class="drop" bind:this={rootEl}>
  <button type="button" class="trigger" class:open aria-haspopup="listbox" aria-expanded={open} onclick={toggle}>
    <span class="tv" class:muted={!selected}>{selected?.label ?? placeholder}</span>
    <Icon name="chevron-right" size={16} strokeWidth={2} />
  </button>
  {#if open}
    <div class="list" role="listbox">
      {#if allowClear}
        <button type="button" class="opt" class:sel={value === null} role="option" aria-selected={value === null} onclick={() => pick(null)}>
          <span class="muted">{placeholder}</span>
        </button>
      {/if}
      {#each options as opt (String(opt.value))}
        <button type="button" class="opt" class:sel={opt.value === value} role="option" aria-selected={opt.value === value} onclick={() => pick(opt.value)}>
          {opt.label}
          {#if opt.value === value}
            <Icon name="check" size={16} />
          {/if}
        </button>
      {/each}
    </div>
  {/if}
</div>

<style>
  .drop {
    position: relative;
  }
  .trigger {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    width: 100%;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
    color: var(--text);
    font-size: 15px;
    padding: 10px 14px;
    min-height: 48px;
    cursor: pointer;
    transition: border-color 120ms;
  }
  .trigger.open {
    border-color: var(--accent);
  }
  .tv {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .muted {
    color: var(--text-muted);
  }
  .list {
    position: absolute;
    top: calc(100% + 4px);
    left: 0;
    right: 0;
    z-index: 60;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 4px;
    max-height: 260px;
    overflow-y: auto;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
    animation: pop 120ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .opt {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    width: 100%;
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 14px;
    text-align: left;
    padding: 11px 12px;
    border-radius: 9px;
    cursor: pointer;
    min-height: 44px;
  }
  .opt:hover {
    background: var(--surface);
  }
  .opt.sel {
    color: var(--accent);
    font-weight: 600;
  }
  @keyframes pop {
    from { opacity: 0; transform: translateY(-4px); }
    to { opacity: 1; transform: translateY(0); }
  }
  @media (prefers-reduced-motion: reduce) {
    .list { animation: none; }
  }
</style>
