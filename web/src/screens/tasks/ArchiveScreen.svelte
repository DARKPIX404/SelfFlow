<script lang="ts">
  import { getDb } from '$lib/db'
  import { tasks } from '$lib/db/repositories'
  import { session } from '$lib/auth/session.svelte'
  import { dayState, refreshDayState } from '$lib/dayState.svelte'
  import type { Task } from '$lib/types'
  import AppBar from '$lib/ui/AppBar.svelte'
  import Dialog from '$lib/ui/Dialog.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import ListItem from '$lib/ui/ListItem.svelte'
  import Menu, { type MenuItem } from '$lib/ui/Menu.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'
  import { requestRescheduleReminders } from '$lib/notifications'

  type MenuAction = 'restore' | 'purge'
  const menuItems: MenuItem<MenuAction>[] = [
    { label: 'Восстановить', icon: 'refresh', value: 'restore' },
    { label: 'Удалить навсегда', icon: 'trash', value: 'purge', danger: true },
  ]

  let version = $state(0)
  if (typeof window !== 'undefined') {
    window.addEventListener('selfflow:mutated', () => version++)
    window.addEventListener('selfflow:synced', () => version++)
  }

  let items = $derived.by(() => {
    void version
    void dayState.version
    const user = session.user
    if (!user) return []
    return getDb().query<Task>(
      'SELECT * FROM tasks WHERE owner = ? AND deleted = 1 ORDER BY updated DESC',
      [user.id],
    )
  })

  let purgeTarget = $state<Task | null>(null)

  function restore(t: Task) {
    haptic('medium')
    // мягкое восстановление + touch через репозиторий (ставит запись в sync-очередь)
    getDb().run('UPDATE tasks SET deleted = 0 WHERE id = ?', [t.id])
    tasks.update(t.id, {})
    refreshDayState()
    requestRescheduleReminders()
    showToast({ message: 'Задача восстановлена' })
  }

  function confirmPurge() {
    const t = purgeTarget
    if (!t) return
    haptic('medium')
    // серверная копия уже удалена в момент архивации — чистим только локальный tombstone
    getDb().run('DELETE FROM tasks WHERE id = ?', [t.id])
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
    purgeTarget = null
    showToast({ message: 'Задача удалена навсегда' })
  }

  function onMenu(action: MenuAction, t: Task) {
    if (action === 'restore') restore(t)
    else purgeTarget = t
  }

  const statusLabel: Record<Task['status'], string> = {
    TODO: 'К выполнению',
    IN_PROGRESS: 'В работе',
    DONE: 'Выполнено',
  }
</script>

<div class="screen">
  <AppBar title="Архив" subtitle="Свайп или меню — восстановить" />
  <div class="screen-body">
    {#if items.length === 0}
      <EmptyState label="Архив пуст" />
    {:else}
      <div class="stack">
        {#each items as t (t.id)}
          <ListItem
            title={t.title}
            subtitle={statusLabel[t.status] + (t.due_date ? ' · срок: ' + t.due_date.slice(0, 10) : '')}
            done={t.status === 'DONE'}
          >
            {#snippet trailing()}
              <Menu items={menuItems} onselect={(a) => onMenu(a, t)} onclose={() => {}}>
                {#snippet children({ open })}
                  <button type="button" class="kebab-trigger" aria-label="Меню" onclick={open}>
                    <Icon name="dots" size={18} />
                  </button>
                {/snippet}
              </Menu>
            {/snippet}
          </ListItem>
        {/each}
      </div>
    {/if}
  </div>
</div>

<Dialog
  open={purgeTarget !== null}
  title="Удалить навсегда?"
  message={purgeTarget ? '«' + purgeTarget.title + '» нельзя будет восстановить.' : ''}
  confirmLabel="Удалить"
  danger
  onconfirm={confirmPurge}
  oncancel={() => (purgeTarget = null)}
/>

<style>
  .kebab-trigger {
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
  }
</style>
