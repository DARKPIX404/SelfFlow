<script lang="ts">
  import { haptic } from './haptics'

  interface Props {
    selected?: boolean
    label: string
    onclick?: () => void
    disabled?: boolean
  }

  let { selected = false, label, onclick, disabled = false }: Props = $props()

  function handleClick() {
    if (disabled) return
    haptic('selection')
    onclick?.()
  }
</script>

<button type="button" class="chip" class:selected class:disabled onclick={handleClick}>
  {label}
</button>

<style>
  .chip {
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text-2);
    border-radius: 12px;
    padding: 8px 14px;
    font-size: 13px;
    font-weight: 500;
    min-height: 36px;
    cursor: pointer;
    transition:
      background 120ms cubic-bezier(0.2, 0.8, 0.2, 1),
      color 120ms cubic-bezier(0.2, 0.8, 0.2, 1),
      border-color 120ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .chip.selected {
    background: var(--accent-soft);
    color: var(--accent);
    border-color: transparent;
  }
  .chip:active {
    background: var(--surface-2);
  }
  .chip.selected:active {
    background: var(--accent-soft);
  }
  .chip.disabled {
    opacity: 0.5;
  }
</style>
