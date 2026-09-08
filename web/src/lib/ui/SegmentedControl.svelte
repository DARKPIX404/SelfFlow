<script lang="ts" generics="T extends string">
  import { haptic } from './haptics'

  interface Props {
    options: { value: T; label: string }[]
    value: T
    onchange?: (value: T) => void
  }

  let { options, value, onchange }: Props = $props()

  function select(v: T) {
    if (v === value) return
    haptic('selection')
    onchange?.(v)
  }

  let activeIndex = $derived(options.findIndex((o) => o.value === value))
</script>

<div class="segmented" role="tablist">
  {#if activeIndex >= 0}
    <span class="thumb" style="transform: translateX({activeIndex * 100}%); width: {100 / options.length}%"></span>
  {/if}
  {#each options as opt (opt.value)}
    <button
      type="button"
      role="tab"
      aria-selected={opt.value === value}
      class="seg"
      class:active={opt.value === value}
      onclick={() => select(opt.value)}
    >
      {opt.label}
    </button>
  {/each}
</div>

<style>
  .segmented {
    display: flex;
    position: relative;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 3px;
  }
  .thumb {
    position: absolute;
    top: 3px;
    bottom: 3px;
    left: 0;
    background: var(--accent-soft);
    border-radius: 9px;
    transition: transform 200ms cubic-bezier(0.2, 0.8, 0.2, 1), width 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
    pointer-events: none;
  }
  .seg {
    flex: 1;
    position: relative;
    z-index: 1;
    border: none;
    background: transparent;
    color: var(--text-2);
    font-size: 13px;
    font-weight: 500;
    padding: 8px 4px;
    min-height: 36px;
    border-radius: 9px;
    cursor: pointer;
    transition: color 120ms;
    white-space: nowrap;
  }
  .seg.active {
    color: var(--accent);
  }
</style>
