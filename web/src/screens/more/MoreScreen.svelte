<script lang="ts">
  import AppBar from '$lib/ui/AppBar.svelte'
  import ListItem from '$lib/ui/ListItem.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import { push } from '$lib/nav.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'

  const items = [
    { icon: 'chart' as const, title: 'Статистика', sub: 'Прогресс задач и распорядка', seg: 'stats', soon: false },
    { icon: 'alarm' as const, title: 'Будильник', sub: 'Подъём и отбой', seg: 'alarm', soon: false },
    { icon: 'flame' as const, title: 'Привычки', sub: 'Ежедневные отметки и серии', seg: 'habits', soon: false },
    { icon: 'target' as const, title: 'Цели', sub: 'Долгосрочные направления', seg: 'goals', soon: false },
    { icon: 'play' as const, title: 'Фокус', sub: 'Таймер фокус-сессий', seg: 'focus', soon: false },
    { icon: 'dots' as const, title: 'Настройки', sub: 'Тема, звуки, безопасность', seg: 'settings', soon: false },
  ]

  function open(seg: string, soon: boolean) {
    haptic('light')
    if (soon) {
      showToast({ message: 'Раздел появится в следующей фазе' })
      return
    }
    push([seg])
  }
</script>

<div class="screen">
  <AppBar title="Ещё" large />
  <div class="screen-body">
    <div class="stack">
      {#each items as item (item.seg)}
        <ListItem
          title={item.title}
          subtitle={item.soon ? 'Скоро — фаза 3' : item.sub}
          icon={item.icon}
          onclick={() => open(item.seg, item.soon)}
        >
          {#snippet trailing()}
            {#if item.soon}
              <span class="soon-badge">скоро</span>
            {:else}
              <Icon name="chevron-right" size={18} />
            {/if}
          {/snippet}
        </ListItem>
      {/each}
    </div>
  </div>
</div>

<style>
  .soon-badge {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 3px 8px;
  }
</style>
