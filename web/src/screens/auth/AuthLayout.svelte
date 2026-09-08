<script lang="ts">
  interface Props {
    title: string
    submitLabel: string
    busy: boolean
    error: string
    onsubmit: () => void
    fields: import('svelte').Snippet
    children?: import('svelte').Snippet
  }

  let { title, submitLabel, busy, error, onsubmit, fields, children }: Props = $props()
</script>

<div class="auth">
  <div class="brand">
    <span class="logo">SF</span>
    <h1>SelfFlow</h1>
    <p>{title}</p>
  </div>
  <form
    onsubmit={(e) => {
      e.preventDefault()
      onsubmit()
    }}
  >
    {@render fields()}
    {#if error}
      <p class="error" role="alert">{error}</p>
    {/if}
    <button type="submit" class="submit" disabled={busy}>{busy ? 'Подождите…' : submitLabel}</button>
  </form>
  {#if children}
    {@render children()}
  {/if}
</div>

<style>
  .auth {
    max-width: 480px;
    margin: 0 auto;
    min-height: 100dvh;
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 32px 24px calc(32px + env(safe-area-inset-bottom, 0px));
    gap: 28px;
  }
  .brand {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }
  .logo {
    width: 72px;
    height: 72px;
    border-radius: 22px;
    background: var(--accent);
    color: var(--text);
    font-size: 26px;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;
    letter-spacing: -0.02em;
  }
  .brand h1 {
    font-size: 24px;
    font-weight: 800;
    letter-spacing: -0.02em;
    color: var(--text);
  }
  .brand p {
    font-size: 14px;
    color: var(--text-muted);
  }
  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  .error {
    font-size: 13px;
    color: var(--accent);
    text-align: center;
  }
  .submit {
    border: none;
    background: var(--accent);
    color: var(--text);
    font-size: 15px;
    font-weight: 700;
    border-radius: 14px;
    padding: 14px;
    min-height: 50px;
    cursor: pointer;
    margin-top: 6px;
  }
  .submit:disabled {
    opacity: 0.6;
  }
</style>
