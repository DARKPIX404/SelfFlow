<script lang="ts">
  interface Props {
    label?: string
    placeholder?: string
    value: string
    oninput: (value: string) => void
    type?: 'text' | 'email' | 'password'
    error?: string
    multiline?: boolean
    autofocus?: boolean
    autocomplete?: string
    inputmode?: 'text' | 'numeric' | 'decimal' | 'tel' | 'email' | 'url'
  }

  let {
    label,
    placeholder,
    value,
    oninput,
    type = 'text',
    error,
    multiline = false,
    autofocus = false,
    autocomplete,
    inputmode,
  }: Props = $props()
</script>

<label class="field">
  {#if label}
    <span class="label">{label}</span>
  {/if}
  {#if multiline}
    <textarea
      class="input"
      class:error
      rows="5"
      placeholder={placeholder}
      {value}
      oninput={(e) => oninput((e.target as HTMLTextAreaElement).value)}
      {autofocus}
    ></textarea>
  {:else}
    <input
      class="input"
      class:error
      {type}
      placeholder={placeholder}
      {value}
      oninput={(e) => oninput((e.target as HTMLInputElement).value)}
      {autofocus}
      autocomplete={(autocomplete ?? 'off') as 'on' | 'off'}
      inputmode={inputmode ?? 'text'}
    />
  {/if}
  {#if error}
    <span class="err">{error}</span>
  {/if}
</label>

<style>
  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .label {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-2);
  }
  .input {
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
    color: var(--text);
    font-size: 15px;
    padding: 13px 14px;
    min-height: 48px;
    box-sizing: border-box;
    width: 100%;
    font-family: inherit;
    outline: none;
    transition: border-color 120ms;
    resize: vertical;
  }
  .input::placeholder {
    color: var(--text-muted);
  }
  .input:focus {
    border-color: var(--accent);
  }
  .input.error {
    border-color: var(--accent);
  }
  .err {
    font-size: 12px;
    color: var(--accent);
  }
</style>
