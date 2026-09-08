<script lang="ts">
  interface Props {
    open: boolean
    title: string
    message?: string
    confirmLabel?: string
    cancelLabel?: string
    danger?: boolean
    onconfirm: () => void
    oncancel: () => void
  }

  let {
    open,
    title,
    message,
    confirmLabel = 'Подтвердить',
    cancelLabel = 'Отмена',
    danger = false,
    onconfirm,
    oncancel,
  }: Props = $props()
</script>

{#if open}
  <div class="dlg-backdrop" role="presentation" onclick={oncancel}></div>
  <div class="dlg" role="alertdialog" aria-modal="true" aria-label={title}>
    <h3>{title}</h3>
    {#if message}
      <p>{message}</p>
    {/if}
    <div class="actions">
      <button type="button" class="btn ghost" onclick={oncancel}>{cancelLabel}</button>
      <button type="button" class="btn" class:danger onclick={onconfirm}>{confirmLabel}</button>
    </div>
  </div>
{/if}

<style>
  .dlg-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    z-index: 160;
    animation: fade 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .dlg {
    position: fixed;
    left: 50%;
    top: 50%;
    translate: -50% -50%;
    width: min(320px, calc(100vw - 48px));
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 20px;
    z-index: 170;
    box-shadow: 0 12px 48px rgba(0, 0, 0, 0.5);
    animation: pop 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  h3 {
    margin: 0 0 8px;
    font-size: 17px;
    font-weight: 600;
    color: var(--text);
  }
  p {
    margin: 0 0 16px;
    font-size: 14px;
    color: var(--text-2);
  }
  .actions {
    display: flex;
    gap: 10px;
    justify-content: flex-end;
  }
  .btn {
    border: none;
    border-radius: 12px;
    padding: 10px 16px;
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
  .btn.danger {
    background: var(--accent);
  }
  @keyframes fade {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  @keyframes pop {
    from { opacity: 0; transform: scale(0.95); }
    to { opacity: 1; transform: scale(1); }
  }
  @media (prefers-reduced-motion: reduce) {
    .dlg, .dlg-backdrop { animation: none; }
  }
</style>
