<script lang="ts">
  import Icon from './Icon.svelte'

  interface Props {
    value: string
    oninput: (value: string) => void
    placeholder?: string
    onclear?: () => void
  }

  let { value, oninput, placeholder = 'Поиск', onclear }: Props = $props()

  let inputEl = $state<HTMLInputElement | null>(null)
</script>

<div class="search">
  <Icon name="search" size={18} />
  <input
    bind:this={inputEl}
    type="text"
    {placeholder}
    aria-label={placeholder}
    {value}
    oninput={(e) => oninput((e.target as HTMLInputElement).value)}
  />
  {#if value}
    <button
      type="button"
      class="clear"
      aria-label="Очистить поиск"
      onclick={() => {
        oninput('')
        onclear?.()
        inputEl?.focus()
      }}
    >
      <Icon name="x" size={16} />
    </button>
  {/if}
</div>

<style>
  .search {
    display: flex;
    align-items: center;
    gap: 10px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 14px;
    padding: 0 12px;
    min-height: 46px;
    color: var(--text-muted);
  }
  input {
    flex: 1;
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 15px;
    outline: none;
    min-height: 44px;
    font-family: inherit;
  }
  input::placeholder {
    color: var(--text-muted);
  }
  .clear {
    border: none;
    background: var(--surface-2);
    color: var(--text-2);
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    flex-shrink: 0;
  }
</style>
