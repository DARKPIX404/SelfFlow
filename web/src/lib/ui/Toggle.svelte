<script lang="ts">
  import { haptic } from './haptics'

  interface Props {
    checked: boolean
    onchange?: (checked: boolean) => void
    disabled?: boolean
    ariaLabel?: string
  }

  let { checked, onchange, disabled = false, ariaLabel }: Props = $props()

  function toggle() {
    if (disabled) return
    haptic('light')
    onchange?.(!checked)
  }
</script>

<button
  type="button"
  class="toggle"
  class:on={checked}
  class:disabled
  role="switch"
  aria-checked={checked}
  aria-label={ariaLabel}
  onclick={toggle}
>
  <span class="knob"></span>
</button>

<style>
  .toggle {
    --w: 48px;
    --h: 28px;
    --pad: 3px;
    width: var(--w);
    height: var(--h);
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    cursor: pointer;
    position: relative;
    padding: 0;
    transition: background 200ms cubic-bezier(0.2, 0.8, 0.2, 1), border-color 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
    flex-shrink: 0;
  }
  .toggle.on {
    background: var(--accent);
    border-color: var(--accent);
  }
  .toggle.disabled {
    opacity: 0.5;
    cursor: default;
  }
  .knob {
    position: absolute;
    top: var(--pad);
    left: var(--pad);
    width: calc(var(--h) - var(--pad) * 2 - 2px);
    height: calc(var(--h) - var(--pad) * 2 - 2px);
    border-radius: 50%;
    background: var(--text);
    transition: transform 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  }
  .toggle.on .knob {
    transform: translateX(calc(var(--w) - var(--h)));
    background: var(--surface);
  }
</style>
