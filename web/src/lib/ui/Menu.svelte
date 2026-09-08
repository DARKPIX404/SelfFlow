<script lang="ts" module>
  export interface MenuItem<TValue> {
    label: string
    icon?: import('./icons').IconName
    value: TValue
    danger?: boolean
  }
</script>

<script lang="ts" generics="T">
  import type { Snippet } from 'svelte'
  import Icon from './Icon.svelte'
  import type { IconName } from './icons'
  import { haptic } from './haptics'

  interface Props {
    items: MenuItem<T>[]
    onselect: (value: T) => void
    onclose: () => void
    children: Snippet<[{ open: (e: MouseEvent | TouchEvent) => void }]>
  }

  let { items, onselect, onclose, children }: Props = $props()

  let anchor = $state<{ x: number; y: number } | null>(null)
  let menuEl = $state<HTMLDivElement | null>(null)

  function openMenu(e: MouseEvent | TouchEvent) {
    e.stopPropagation()
    haptic('light')
    const cx = 'clientX' in e ? e.clientX : 0
    const cy = 'clientY' in e ? e.clientY : 0
    anchor = { x: cx, y: cy }
  }

  function select(value: T) {
    haptic('light')
    close()
    onselect(value)
  }

  function close() {
    anchor = null
    onclose()
  }

  function menuStyle(): string {
    if (!anchor) return 'display: none'
    const vw = window.innerWidth
    const vh = window.innerHeight
    const mw = Math.min(240, vw - 16)
    const mh = (menuEl?.offsetHeight || 0) || items.length * 46
    const x = Math.min(Math.max(8, anchor.x - mw + 8), vw - mw - 8)
    const y = Math.min(anchor.y + 4, vh - mh - 16)
    return `left: ${x}px; top: ${y}px; width: ${mw}px`
  }

  let style = $state('display: none')

  $effect(() => {
    if (!anchor) {
      style = 'display: none'
      return
    }
    // позиционируем после монтирования меню, чтобы знать его высоту
    void menuEl
    queueMicrotask(() => {
      style = menuStyle()
    })
    const onDoc = (e: PointerEvent) => {
      if (menuEl && !menuEl.contains(e.target as Node)) close()
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close()
    }
    document.addEventListener('pointerdown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  })
</script>

{@render children({ open: openMenu })}

{#if anchor}
  <div class="menu" role="menu" bind:this={menuEl} style={style}>
    {#each items as item (item.label)}
      <button
        type="button"
        role="menuitem"
        class="item"
        class:danger={item.danger}
        onclick={() => select(item.value)}
      >
        {#if item.icon}
          <Icon name={item.icon} size={19} />
        {/if}
        <span>{item.label}</span>
      </button>
    {/each}
  </div>
{/if}

<style>
  .menu {
    position: fixed;
    z-index: 150;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 4px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
    animation: pop 120ms cubic-bezier(0.2, 0.8, 0.2, 1);
    transform-origin: top right;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 14px;
    font-weight: 500;
    padding: 11px 12px;
    border-radius: 9px;
    cursor: pointer;
    text-align: left;
    min-height: 44px;
    transition: background 120ms;
  }
  .item:hover {
    background: var(--surface);
  }
  .item.danger {
    color: var(--accent);
  }
  @keyframes pop {
    from { opacity: 0; transform: scale(0.94); }
    to { opacity: 1; transform: scale(1); }
  }
  @media (prefers-reduced-motion: reduce) {
    .menu { animation: none; }
  }
</style>
