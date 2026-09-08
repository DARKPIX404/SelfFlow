<script lang="ts">
  import { haptic } from './haptics'

  interface Props {
    checked: boolean
    onclick?: () => void
    size?: number
    ariaLabel?: string
  }

  let { checked, onclick, size = 24, ariaLabel }: Props = $props()

  function handle() {
    haptic(checked ? 'light' : 'medium')
    onclick?.()
  }
</script>

<button
  type="button"
  class="check-circle"
  class:on={checked}
  style="--size: {size}px"
  aria-label={ariaLabel ?? (checked ? 'Отменить выполнение' : 'Отметить выполненным')}
  onclick={handle}
>
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <circle class="ring" cx="12" cy="12" r="9" />
    <path class="tick" d="m7.5 12.4 3 3 6-6.5" />
  </svg>
</button>

<style>
  .check-circle {
    width: var(--size);
    height: var(--size);
    padding: 0;
    border: none;
    background: transparent;
    cursor: pointer;
    color: var(--text-muted);
    flex-shrink: 0;
    transition: color 120ms;
  }
  .check-circle .ring {
    transition: stroke 150ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .check-circle .tick {
    stroke-dasharray: 16;
    stroke-dashoffset: 16;
    transition: stroke-dashoffset 150ms cubic-bezier(0.2, 0.8, 0.2, 1) 30ms;
  }
  .check-circle.on {
    color: var(--success);
  }
  .check-circle.on .tick {
    stroke-dashoffset: 0;
  }
</style>
