<script lang="ts">
  import { haptic, type HapticKind } from './haptics'

  interface Props {
    label: string
    cta?: string
    onCta?: () => void
    small?: boolean
  }

  let { label, cta, onCta, small = false }: Props = $props()

  function fire(kind: HapticKind = 'light') {
    haptic(kind)
    onCta?.()
  }
</script>

<div class="empty" class:small>
  <svg class="art" viewBox="0 0 96 64" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <rect x="16" y="10" width="64" height="44" rx="8" />
    <path d="M16 22h64" />
    <path d="m30 38 5 5 10-11" />
    <circle cx="66" cy="40" r="1.4" />
  </svg>
  <p>{label}</p>
  {#if cta && onCta}
    <button type="button" class="cta" onclick={() => fire('medium')}>{cta}</button>
  {/if}
</div>

<style>
  .empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 40px 16px;
    color: var(--text-muted);
    text-align: center;
  }
  .empty.small {
    padding: 20px 8px;
  }
  .art {
    width: 72px;
    height: 48px;
    opacity: 0.6;
  }
  p {
    margin: 0;
    font-size: 14px;
  }
  .cta {
    margin-top: 4px;
    border: none;
    background: var(--accent-soft);
    color: var(--accent);
    border-radius: 12px;
    padding: 10px 20px;
    font-size: 14px;
    font-weight: 600;
    min-height: 44px;
    cursor: pointer;
  }
</style>
