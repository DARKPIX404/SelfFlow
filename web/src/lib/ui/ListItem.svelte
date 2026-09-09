<script lang="ts">
  import type { Snippet } from 'svelte'
  import Icon from './Icon.svelte'
  import type { IconName } from './icons'

  interface Props {
    title: string
    subtitle?: string | null
    time?: string | null
    icon?: IconName
    leading?: Snippet
    trailing?: Snippet
    onmenu?: (e: MouseEvent | TouchEvent) => void
    onclick?: () => void
    done?: boolean
  }

  let { title, subtitle, time, icon, leading, trailing, onmenu, onclick, done = false }: Props = $props()
</script>

<div
  class="item"
  class:clickable={!!onclick}
  role={onclick ? 'button' : undefined}
  tabindex={onclick ? 0 : undefined}
  {onclick}
  onkeydown={(e) => {
    if (onclick && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault()
      onclick()
    }
  }}
>
  {#if leading}
    <span class="leading">{@render leading()}</span>
  {:else if icon}
    <span class="leading icon"><Icon name={icon} size={20} /></span>
  {/if}
  <div class="texts">
    <span class="title" class:done>{title}</span>
    {#if subtitle}
      <span class="sub">{subtitle}</span>
    {/if}
  </div>
  {#if time}
    <span class="time">{time}</span>
  {/if}
  {#if trailing}
    <span class="trailing">{@render trailing()}</span>
  {/if}
  {#if onmenu}
    <button
      type="button"
      class="kebab"
      aria-label="Меню"
      onclick={(e) => {
        e.stopPropagation()
        onmenu?.(e)
      }}
    >
      <Icon name="dots" size={18} />
    </button>
  {/if}
</div>

<style>
  .item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    min-height: 56px;
    border-radius: 16px;
    background: var(--surface);
    transition: background 120ms;
  }
  .item.clickable {
    cursor: pointer;
  }
  .item.clickable:hover {
    background: var(--surface-2);
  }
  .leading {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    color: var(--text-2);
  }
  .leading.icon {
    width: 38px;
    height: 38px;
    border-radius: 12px;
    background: var(--surface-2);
  }
  .texts {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .title {
    font-size: 15px;
    font-weight: 500;
    color: var(--text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    transition: color 150ms;
  }
  .title.done {
    text-decoration: line-through;
    color: var(--text-muted);
  }
  .sub {
    font-size: 13px;
    color: var(--text-2);
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow-wrap: anywhere;
    word-break: break-word;
  }
  .time {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
    flex-shrink: 0;
  }
  .trailing {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
  }
  .kebab {
    border: none;
    background: transparent;
    color: var(--text-muted);
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    flex-shrink: 0;
  }
  .kebab:hover {
    background: var(--surface-2);
    color: var(--text-2);
  }
</style>
