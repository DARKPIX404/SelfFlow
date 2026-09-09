<script lang="ts">
  import { updateState, downloadAndInstall, dismissUpdate } from '$lib/update.svelte'
  import { haptic } from './haptics'

  function onLater() {
    haptic('light')
    dismissUpdate()
  }

  function onInstall() {
    haptic('medium')
    void downloadAndInstall()
  }
</script>

{#if updateState.available && !updateState.dismissed}
  <div class="upd-backdrop" role="presentation">
    <div class="upd-card" role="dialog" aria-modal="true" aria-label="Доступно обновление">
      <span class="upd-icon">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 12a9 9 0 1 1-3-6.7" />
          <path d="M21 3v6h-6" />
        </svg>
      </span>
      <h3>Доступно обновление {updateState.available.tag}</h3>
      {#if updateState.available.notes}
        <p class="notes">{updateState.available.notes}</p>
      {/if}
      <div class="upd-actions">
        <button type="button" class="btn later" onclick={onLater} disabled={updateState.downloading}>Позже</button>
        <button type="button" class="btn primary" onclick={onInstall} disabled={updateState.downloading}>
          {#if updateState.downloading}
            Скачивание… {updateState.progress}%
          {:else}
            Скачать и установить
          {/if}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .upd-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    z-index: 190;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }
  .upd-card {
    width: 100%;
    max-width: 420px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 24px;
    padding: 22px 20px 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    animation: pop 280ms var(--ease);
  }
  .upd-icon {
    width: 48px;
    height: 48px;
    border-radius: 16px;
    background: var(--accent-soft);
    color: var(--accent);
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .upd-card h3 {
    font-size: 18px;
    font-weight: 700;
    color: var(--text);
  }
  .notes {
    font-size: 13px;
    line-height: 1.5;
    color: var(--text-2);
    max-height: 160px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .upd-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 6px;
  }
  .btn {
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-size: 14px;
    font-weight: 600;
    border-radius: 12px;
    padding: 11px 16px;
    min-height: 44px;
    cursor: pointer;
  }
  .btn.primary {
    background: var(--accent);
    border-color: var(--accent);
    color: var(--bg);
  }
  .btn:disabled {
    opacity: 0.6;
  }
  @keyframes pop {
    from { transform: translateY(12px) scale(0.97); opacity: 0; }
    to { transform: translateY(0) scale(1); opacity: 1; }
  }
  @media (prefers-reduced-motion: reduce) {
    .upd-card { animation: none; }
  }
</style>
