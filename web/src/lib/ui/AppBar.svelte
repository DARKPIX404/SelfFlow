<script lang="ts">
  import type { Snippet } from 'svelte'
  import Icon from './Icon.svelte'
  import { haptic } from './haptics'
  import { pop, route } from '$lib/nav.svelte'

  interface Props {
    title: string
    subtitle?: string
    actions?: Snippet
    large?: boolean
  }

  let { title, subtitle, actions, large = true }: Props = $props()

  let canBack = $derived(route.stack.length > 0)

  function back() {
    haptic('light')
    pop()
  }
</script>

<header class="appbar" class:large>
  {#if canBack}
    <button type="button" class="back" aria-label="Назад" onclick={back}>
      <Icon name="chevron-left" size={24} />
    </button>
  {/if}
  <div class="titles">
    <h1>{title}</h1>
    {#if subtitle}
      <span class="subtitle">{subtitle}</span>
    {/if}
  </div>
  {#if actions}
    <div class="actions">{@render actions()}</div>
  {/if}
</header>

<style>
  .appbar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: calc(14px + env(safe-area-inset-top, 0px)) 16px 10px;
    position: sticky;
    top: 0;
    z-index: 20;
    background: var(--bg);
  }
  .appbar.large h1 {
    font-size: 24px;
  }
  .titles {
    flex: 1;
    min-width: 0;
  }
  h1 {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: var(--text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .subtitle {
    font-size: 13px;
    color: var(--text-2);
  }
  .back {
    border: none;
    background: transparent;
    color: var(--text-2);
    width: 40px;
    height: 40px;
    border-radius: 12px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    flex-shrink: 0;
    margin-left: -8px;
  }
  .back:hover {
    background: var(--surface);
  }
  .actions {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
  }
</style>
