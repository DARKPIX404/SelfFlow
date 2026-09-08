<script lang="ts">
  import Icon from './Icon.svelte'
  import type { IconName } from './icons'
  import { haptic } from './haptics'
  import { route, navigate } from '$lib/nav.svelte'

  const tabs: { id: string; label: string; icon: IconName }[] = [
    { id: 'today', label: 'Сегодня', icon: 'home' },
    { id: 'routines', label: 'Распорядок', icon: 'calendar-clock' },
    { id: 'tasks', label: 'Задачи', icon: 'check-square' },
    { id: 'notes', label: 'Заметки', icon: 'note' },
    { id: 'more', label: 'Ещё', icon: 'dots' },
  ]

  let active = $derived(route.tab)

  function go(id: string) {
    if (id === active) return
    haptic('selection')
    navigate(id)
  }
</script>

<nav class="tabbar" aria-label="Основная навигация">
  {#each tabs as tab (tab.id)}
    <button
      type="button"
      class="tab"
      class:active={active === tab.id}
      aria-current={active === tab.id ? 'page' : undefined}
      aria-label={tab.label}
      onclick={() => go(tab.id)}
    >
      <span class="ic"><Icon name={tab.icon} size={23} /></span>
      {#if active === tab.id}
        <span class="lbl">{tab.label}</span>
      {/if}
    </button>
  {/each}
</nav>

<style>
  .tabbar {
    position: fixed;
    left: 50%;
    bottom: 0;
    translate: -50% 0;
    width: 100%;
    max-width: 480px;
    display: flex;
    background: var(--bg-deep);
    border-top: 1px solid var(--border);
    padding: 6px 8px calc(8px + env(safe-area-inset-bottom, 0px));
    z-index: 50;
  }
  .tab {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    border: none;
    background: transparent;
    color: var(--text-muted);
    cursor: pointer;
    padding: 6px 0;
    border-radius: 12px;
    min-height: 52px;
    justify-content: center;
    transition: color 120ms;
    position: relative;
  }
  .tab .ic {
    display: inline-flex;
    transition: transform 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .tab.active {
    color: var(--accent);
  }
  .tab.active .ic {
    transform: translateY(-1px) scale(1.06);
  }
  .tab:active .ic {
    transform: scale(0.9);
  }
  .lbl {
    font-size: 11px;
    font-weight: 600;
    animation: fadeIn 150ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(3px); }
    to { opacity: 1; transform: translateY(0); }
  }
  @media (prefers-reduced-motion: reduce) {
    .lbl { animation: none; }
    .tab .ic { transition: none; }
  }
</style>
