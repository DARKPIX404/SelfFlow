<script lang="ts">
  import { toastState, hideToast } from './toast.svelte'
  import { haptic } from './haptics'

  function action() {
    haptic('light')
    toastState.current?.onAction?.()
    hideToast()
  }
</script>

{#if toastState.current}
  <div class="toast-wrap" role="status">
    <div class="toast">
      <span class="msg">{toastState.current.message}</span>
      {#if toastState.current.actionLabel}
        <button type="button" class="action" onclick={action}>{toastState.current.actionLabel}</button>
      {/if}
    </div>
  </div>
{/if}

<style>
  .toast-wrap {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 96px;
    display: flex;
    justify-content: center;
    z-index: 200;
    pointer-events: none;
    padding: 0 16px;
  }
  .toast {
    pointer-events: auto;
    display: flex;
    align-items: center;
    gap: 16px;
    max-width: 440px;
    width: fit-content;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    border-radius: 14px;
    padding: 12px 16px;
    font-size: 14px;
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.4);
    animation: rise 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .action {
    border: none;
    background: transparent;
    color: var(--accent);
    font-weight: 700;
    font-size: 14px;
    cursor: pointer;
    padding: 4px 8px;
    white-space: nowrap;
  }
  @keyframes rise {
    from { transform: translateY(16px); opacity: 0; }
    to { transform: translateY(0); opacity: 1; }
  }
  @media (prefers-reduced-motion: reduce) {
    .toast { animation: none; }
  }
</style>
