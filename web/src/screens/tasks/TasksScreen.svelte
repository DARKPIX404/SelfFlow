<script lang="ts">
  import { dayState, refreshDayState } from '$lib/dayState.svelte'
  import { tasks } from '$lib/db/repositories'
  import { getDb } from '$lib/db'
  import { todayLocal } from '$lib/db/id'
  import { fmtDateShort } from '$lib/format'
  import { session } from '$lib/auth/session.svelte'
  import type { Task } from '$lib/types'
  import AppBar from '$lib/ui/AppBar.svelte'
  import BottomSheet from '$lib/ui/BottomSheet.svelte'
  import CheckCircle from '$lib/ui/CheckCircle.svelte'
  import Chip from '$lib/ui/Chip.svelte'
  import DateGrid from '$lib/ui/DateGrid.svelte'
  import Dialog from '$lib/ui/Dialog.svelte'
  import DropList from '$lib/ui/DropList.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import Fab from '$lib/ui/Fab.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import ListItem from '$lib/ui/ListItem.svelte'
  import Menu, { type MenuItem } from '$lib/ui/Menu.svelte'
  import SegmentedControl from '$lib/ui/SegmentedControl.svelte'
  import SwipeableRow from '$lib/ui/SwipeableRow.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import { push } from '$lib/nav.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'
  import { requestRescheduleReminders } from '$lib/notifications'

  type Filter = 'ALL' | 'TODO' | 'IN_PROGRESS' | 'DONE'
  const filters: { value: Filter; label: string }[] = [
    { value: 'ALL', label: 'Все' },
    { value: 'TODO', label: 'К выполнению' },
    { value: 'IN_PROGRESS', label: 'В работе' },
    { value: 'DONE', label: 'Выполнено' },
  ]

  const priorities = [
    { value: 'HIGH', label: 'Высокий' },
    { value: 'MEDIUM', label: 'Средний' },
    { value: 'LOW', label: 'Низкий' },
  ] as const

  type MenuAction = 'progress' | 'todo' | 'reschedule' | 'delete'
  const menuItems: MenuItem<MenuAction>[] = [
    { label: 'В работу', icon: 'play', value: 'progress' },
    { label: 'К выполнению', icon: 'clock', value: 'todo' },
    { label: 'Изменить срок', icon: 'calendar-clock', value: 'reschedule' },
    { label: 'В архив', icon: 'archive', value: 'delete', danger: true },
  ]

  let filter = $state<Filter>('ALL')

  let sections = $derived.by(() => {
    // dayState.version — якорь реактивности: инкрементится на каждый refresh
    void dayState.version
    const all = tasksList()
    const visible = filter === 'ALL' ? all : all.filter((t) => t.status === filter)
    const by = (s: Task['status']) => visible.filter((t) => t.status === s)
    const out: { title: string; items: Task[] }[] = []
    if (filter === 'ALL' || filter === 'TODO') {
      const items = by('TODO').sort((a, b) => {
        const rank = { HIGH: 0, MEDIUM: 1, LOW: 2 } as const
        return rank[a.priority] - rank[b.priority]
      })
      if (items.length) out.push({ title: 'К выполнению', items })
    }
    if (filter === 'ALL' || filter === 'IN_PROGRESS') {
      const items = by('IN_PROGRESS')
      if (items.length) out.push({ title: 'В работе', items })
    }
    if (filter === 'ALL' || filter === 'DONE') {
      const items = by('DONE').sort((a, b) => (b.completed_at ?? '').localeCompare(a.completed_at ?? ''))
      if (items.length) out.push({ title: 'Выполнено', items })
    }
    return out
  })

  // dayState не хранит полный список задач — читаем репозиторий напрямую,
  // а dayState пробрасывает реактивность через selfflow:mutated
  function tasksList(): Task[] {
    const user = session.user
    return user ? tasks.list(user.id) : []
  }

  function complete(t: Task) {
    tasks.update(t.id, { status: 'DONE', completed_at: new Date().toISOString() })
    haptic('medium')
    refreshDayState()
    requestRescheduleReminders()
  }

  function setStatus(t: Task, status: Task['status']) {
    tasks.update(t.id, {
      status,
      completed_at: status === 'DONE' ? new Date().toISOString() : null,
    })
    haptic('light')
    refreshDayState()
    requestRescheduleReminders()
  }

  let pendingDelete = $state<Task | null>(null)
  let rescheduleTarget = $state<Task | null>(null)

  function requestDelete(t: Task) {
    pendingDelete = t
  }

  function confirmDelete() {
    const t = pendingDelete
    if (!t) return
    tasks.remove(t.id)
    haptic('medium')
    showToast({
      message: 'Задача в архиве',
      actionLabel: 'Отменить',
      onAction: () => {
        getDb().run('UPDATE tasks SET deleted = 0 WHERE id = ?', [t.id])
        tasks.update(t.id, { title: t.title })
        refreshDayState()
        requestRescheduleReminders()
      },
    })
    pendingDelete = null
    refreshDayState()
    requestRescheduleReminders()
  }

  function onMenu(action: MenuAction, t: Task) {
    if (action === 'progress') setStatus(t, 'IN_PROGRESS')
    else if (action === 'todo') setStatus(t, 'TODO')
    else if (action === 'reschedule') rescheduleTarget = t
    else requestDelete(t)
  }

  // --- форма ---
  let sheetOpen = $state(false)
  let editing = $state<Task | null>(null)
  let title = $state('')
  let titleError = $state('')
  let description = $state('')
  let priority = $state<Task['priority']>('MEDIUM')
  let dueDate = $state<string | null>(null)
  let routineId = $state<string | null>(null)
  let goalId = $state<string | null>(null)

  function openCreate() {
    editing = null
    title = ''
    titleError = ''
    description = ''
    priority = 'MEDIUM'
    dueDate = null
    routineId = null
    goalId = null
    sheetOpen = true
  }

  function openEdit(t: Task) {
    editing = t
    title = t.title
    titleError = ''
    description = t.description ?? ''
    priority = t.priority
    dueDate = t.due_date
    routineId = t.routine_id
    goalId = t.goal_id
    sheetOpen = true
  }

  let routineOptions = $derived(
    dayState.routines.map((r) => ({ value: r.id, label: r.title })),
  )

  let goalOptions = $derived(
    [...dayState.goals]
      .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
      .map((g) => ({ value: g.id, label: g.title })),
  )

  function save() {
    const user = session.user
    if (!user) return
    if (!title.trim()) {
      titleError = 'Введите название'
      return
    }
    const data = {
      title: title.trim(),
      description: description.trim() || null,
      priority,
      due_date: dueDate ? dueDate + ' 00:00:00.000Z' : null,
      routine_id: routineId,
      goal_id: goalId,
    }
    if (editing) {
      tasks.update(editing.id, data)
    } else {
      tasks.create(user.id, { ...data, status: 'TODO', completed_at: null })
    }
    sheetOpen = false
    haptic('medium')
    refreshDayState()
    requestRescheduleReminders()
  }

  function priorityColor(p: Task['priority']): string {
    if (p === 'HIGH') return 'var(--warn)'
    if (p === 'MEDIUM') return 'var(--accent)'
    return 'var(--border)'
  }
</script>

<div class="screen">
  <AppBar title="Задачи" large>
    {#snippet actions()}
      <button type="button" class="icon-btn" aria-label="Архив" onclick={() => push(['archive'])}>
        <Icon name="archive" size={20} />
      </button>
    {/snippet}
  </AppBar>
  <div class="screen-body">
    <SegmentedControl options={filters} value={filter} onchange={(v) => (filter = v)} />

    {#if sections.length === 0}
      <EmptyState label="Задач нет" cta="Создать задачу" onCta={openCreate} />
    {:else}
      {#each sections as section (section.title)}
        <h2 class="section-title">{section.title}</h2>
        <div class="stack">
          {#each section.items as t (t.id)}
            <SwipeableRow
              right={t.status === 'DONE'
                ? { label: 'В архив', icon: 'trash', onTrigger: () => requestDelete(t) }
                : { label: 'Выполнить', icon: 'check', onTrigger: () => complete(t) }}
              left={t.status === 'DONE'
                ? { label: 'В архив', icon: 'trash', onTrigger: () => requestDelete(t) }
                : { label: 'В работу', icon: 'play', onTrigger: () => setStatus(t, 'IN_PROGRESS') }}
            >
              <div class="task-row">
                <span class="prio" style="background: {priorityColor(t.priority)}"></span>
                <ListItem
                  title={t.title}
                  subtitle={t.description ?? (t.due_date ? 'Срок: ' + fmtDateShort(t.due_date.slice(0, 10)) : null)}
                  done={t.status === 'DONE'}
                >
                  {#snippet leading()}
                    <CheckCircle
                      checked={t.status === 'DONE'}
                      onclick={() => (t.status === 'DONE' ? setStatus(t, 'TODO') : complete(t))}
                    />
                  {/snippet}
                  {#snippet trailing()}
                    <Menu items={menuItems} onselect={(a) => onMenu(a, t)} onclose={() => {}}>
                      {#snippet children({ open })}
                        <button type="button" class="kebab-trigger" aria-label="Меню" onclick={open}>
                          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
                            <circle cx="5.5" cy="12" r="1.6" /><circle cx="12" cy="12" r="1.6" /><circle cx="18.5" cy="12" r="1.6" />
                          </svg>
                        </button>
                      {/snippet}
                    </Menu>
                  {/snippet}
                </ListItem>
              </div>
            </SwipeableRow>
          {/each}
        </div>
      {/each}
    {/if}
  </div>
  <Fab onclick={openCreate} />
</div>

<BottomSheet open={sheetOpen} onclose={() => (sheetOpen = false)} title={editing ? 'Изменить задачу' : 'Новая задача'}>
  <div class="form">
    <TextField label="Название" placeholder="Что нужно сделать?" value={title} oninput={(v) => (title = v)} error={titleError} autofocus />
    <TextField label="Описание" placeholder="Детали (необязательно)" value={description} oninput={(v) => (description = v)} multiline />
    <div class="field-group">
      <span class="f-label">Приоритет</span>
      <div class="chips">
        {#each priorities as p (p.value)}
          <Chip selected={priority === p.value} label={p.label} onclick={() => (priority = p.value)} />
        {/each}
      </div>
    </div>
    <div class="field-group">
      <span class="f-label">Срок</span>
      <DateGrid value={dueDate} onchange={(v) => (dueDate = v)} markedDates={[todayLocal()]} />
    </div>
    <div class="field-group">
      <span class="f-label">Привязка к рутине</span>
      <DropList options={routineOptions} value={routineId} onchange={(v) => (routineId = v)} placeholder="Без привязки" />
    </div>
    <div class="field-group">
      <span class="f-label">Цель</span>
      <DropList options={goalOptions} value={goalId} onchange={(v) => (goalId = v)} placeholder="Без цели" />
    </div>
    <button type="button" class="save-btn" onclick={save}>{editing ? 'Сохранить' : 'Создать'}</button>
  </div>
</BottomSheet>

<Dialog
  open={pendingDelete !== null}
  title="В архив?"
  message={pendingDelete ? '«' + pendingDelete.title + '» будет перенесена в архив.' : ''}
  confirmLabel="В архив"
  danger
  onconfirm={confirmDelete}
  oncancel={() => (pendingDelete = null)}
/>

<BottomSheet open={rescheduleTarget !== null} onclose={() => (rescheduleTarget = null)} title="Изменить срок">
  {#if rescheduleTarget}
    <div class="form">
      <div class="field-group">
        <span class="f-label">Новый срок для «{rescheduleTarget.title}»</span>
        <DateGrid
          value={rescheduleTarget.due_date?.slice(0, 10) ?? null}
          onchange={(v) => {
            if (rescheduleTarget) {
              tasks.update(rescheduleTarget.id, {
                due_date: v ? v + ' 00:00:00.000Z' : null,
              })
              refreshDayState()
            }
          }}
        />
      </div>
      <button type="button" class="save-btn" onclick={() => (rescheduleTarget = null)}>Готово</button>
    </div>
  {/if}
</BottomSheet>

<style>
  .task-row {
    display: flex;
    align-items: stretch;
    border-radius: 16px;
    overflow: hidden;
  }
  .task-row :global(.item) {
    flex: 1;
    border-radius: 0;
  }
  .prio {
    width: 4px;
    flex-shrink: 0;
  }
  .form {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding-top: 4px;
  }
  .field-group {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .f-label {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-2);
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .save-btn {
    border: none;
    background: var(--accent);
    color: var(--text);
    font-size: 15px;
    font-weight: 700;
    border-radius: 14px;
    padding: 14px;
    min-height: 50px;
    cursor: pointer;
    margin-top: 4px;
  }
  .save-btn:active {
    filter: brightness(1.15);
  }
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
