<script lang="ts">
  import { haptic } from './haptics'
  import Icon from './Icon.svelte'

  interface Props {
    onclick: () => void
    icon?: 'plus' | 'pencil'
    ariaLabel?: string
  }

  let { onclick, icon = 'plus', ariaLabel = 'Добавить' }: Props = $props()

  function handle() {
    haptic('light')
    onclick()
  }
</script>

<button type="button" class="fab" aria-label={ariaLabel} onclick={handle}>
  <Icon name={icon} size={26} strokeWidth={2} />
</button>

<style>
  .fab {
    position: fixed;
    right: max(16px, calc(50% - 240px + 16px));
    bottom: 88px;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    border: none;
    background: var(--accent);
    color: var(--text);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
    transition: transform 120ms cubic-bezier(0.2, 0.8, 0.2, 1), background 120ms;
    z-index: 30;
  }
  .fab:active {
    transform: scale(0.92);
    background: var(--accent);
    filter: brightness(1.15);
  }
</style>
