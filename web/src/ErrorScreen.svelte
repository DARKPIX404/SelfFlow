<script lang="ts">
  /**
   * Экран фатальной ошибки. Монтируется из main.ts, если стартап упал
   * или после старта прилетел глобальный error/unhandledrejection.
   * Показывает текст ошибки + stack — без этого чёрный экран неотличим
   * от зависания, и реальный баг на устройстве не диагностируется.
   */

  interface Props {
    error: unknown
  }

  let { error }: Props = $props()

  let text = $derived(
    error instanceof Error
      ? `${error.name}: ${error.message}\n\n${error.stack ?? '(stack недоступен)'}`
      : String(error),
  )
</script>

<div class="error-screen" role="alert">
  <div class="card">
    <h1>Не удалось запустить приложение</h1>
    <p class="hint">Сообщите текст ниже разработчику — он поможет быстро починить.</p>
    <pre>{text}</pre>
    <button type="button" class="reload" onclick={() => location.reload()}>Перезапустить</button>
  </div>
</div>

<style>
  .error-screen {
    position: fixed;
    inset: 0;
    z-index: 9999;
    background: var(--bg);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    overflow: auto;
  }
  .card {
    max-width: 560px;
    width: 100%;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  h1 {
    font-size: 20px;
    font-weight: 700;
    color: var(--text);
  }
  .hint {
    font-size: 13px;
    color: var(--text-muted);
  }
  pre {
    font-size: 12px;
    line-height: 1.5;
    color: var(--text);
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 12px;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 45vh;
    overflow: auto;
    margin: 0;
    user-select: text;
  }
  .reload {
    border: none;
    background: var(--accent);
    color: #f0e6cc;
    font-size: 15px;
    font-weight: 700;
    border-radius: 14px;
    padding: 14px;
    min-height: 48px;
    cursor: pointer;
  }
  .reload:active {
    filter: brightness(1.15);
  }
</style>
