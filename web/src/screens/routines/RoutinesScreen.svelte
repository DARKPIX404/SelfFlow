<script lang="ts">
  import { dayState, refreshDayState } from '$lib/dayState.svelte'
  import { routines } from '$lib/db/repositories'
  import { getDb } from '$lib/db'
  import { todayLocal } from '$lib/db/id'
  import { fmtTime } from '$lib/format'
  import { setCompletion } from '$lib/routines'
  import { session } from '$lib/auth/session.svelte'
  import type { Routine } from '$lib/types'
  import AppBar from '$lib/ui/AppBar.svelte'
  import BottomSheet from '$lib/ui/BottomSheet.svelte'
  import CheckCircle from '$lib/ui/CheckCircle.svelte'
  import Chip from '$lib/ui/Chip.svelte'
  import Dialog from '$lib/ui/Dialog.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import Fab from '$lib/ui/Fab.svelte'
  import ListItem from '$lib/ui/ListItem.svelte'
  import Menu, { type MenuItem } from '$lib/ui/Menu.svelte'
  import SwipeableRow from '$lib/ui/SwipeableRow.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import TimeWheel from '$lib/ui/TimeWheel.svelte'
  import DateGrid from '$lib/ui/DateGrid.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'
  import { requestRescheduleReminders } from '$lib/notifications'

  type Rule = Routine['recurrence_rule']

  const RULE_CHIPS: { value: Rule; label: string }[] = [
    { value: 'NONE', label: 'Нет' },
    { value: 'DAILY', label: 'Ежедневно' },
    { value: 'WEEKDAYS', label: 'По будням' },
    { value: 'WEEKLY', label: 'Еженедельно' },
  ]

  const CATEGORIES = ['Утро', 'Работа', 'Здоровье', 'Дом', 'Отдых', 'Учёба']

  const today = todayLocal()

  // --- форма ---
  let sheetOpen = $state(false)
  let editing = $state<Routine | null>(null)
  let title = $state('')
  let titleError = $state('')
  let startTime = $state<string | null>(null)
  let endTime = $state<string | null>(null)
  let rule = $state<Rule>('NONE')
  let notifyText = $state('')
  let startDate = $state<string | null>(null)
  let category = $state<string | null>(null)

  function openCreate() {
    editing = null
    title = ''
    titleError = ''
    startTime = '08:00'
    endTime = null
    rule = 'NONE'
    notifyText = ''
    startDate = today
    category = null
    sheetOpen = true
  }

  function openEdit(r: Routine) {
    editing = r
    title = r.title
    titleError = ''
    startTime = r.start_time ? r.start_time.slice(11, 16) : null
    endTime = r.end_time ? r.end_time.slice(11, 16) : null
    rule = r.recurrence_rule ?? 'NONE'
    notifyText = r.notification_text ?? ''
    startDate = (r.start_time ?? r.created).slice(0, 10)
    category = r.category
    sheetOpen = true
  }

  function save() {
    const user = session.user
    if (!user) return
    if (!title.trim()) {
      titleError = 'Введите название'
      return
    }
    const date = startDate ?? today
    // сервер хранит start_time/end_time как datetime — собираем из даты и времени
    const data = {
      title: title.trim(),
      start_time: startTime ? `${date} ${startTime}:00.000Z` : null,
      end_time: endTime ? `${date} ${endTime}:00.000Z` : null,
      recurrence_rule: rule,
      notification_text: notifyText.trim() || null,
      category,
      is_active: 1,
    }
    if (editing) {
      routines.update(editing.id, data)
    } else {
      routines.create(user.id, data)
    }
    sheetOpen = false
    haptic('medium')
    requestRescheduleReminders()
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  // --- действия ---
  function toggleDone(r: Routine, done: boolean) {
    const user = session.user
    if (!user) return
    setCompletion(user.id, r.id, today, done)
    haptic('medium')
    refreshDayState()
  }

  function duplicate(r: Routine) {
    const user = session.user
    if (!user) return
    routines.create(user.id, {
      title: r.title,
      start_time: r.start_time,
      end_time: r.end_time,
      recurrence_rule: r.recurrence_rule,
      notification_text: r.notification_text,
      category: r.category,
      is_active: 1,
    })
    haptic('light')
    requestRescheduleReminders()
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  let pendingDelete = $state<Routine | null>(null)

  function requestDelete(r: Routine) {
    pendingDelete = r
  }

  function confirmDelete() {
    const r = pendingDelete
    if (!r) return
    routines.remove(r.id)
    haptic('medium')
    showToast({
      message: '«' + r.title + '» удалена',
      actionLabel: 'Отменить',
      onAction: () => {
        // снимаем локальный tombstone и ставим запись обратно в очередь синка
        getDb().run('UPDATE routines SET deleted = 0 WHERE id = ?', [r.id])
        routines.update(r.id, { title: r.title })
        requestRescheduleReminders()
        window.dispatchEvent(new CustomEvent('selfflow:mutated'))
      },
    })
    pendingDelete = null
    requestRescheduleReminders()
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  type MenuAction = 'edit' | 'duplicate' | 'delete'
  const menuItems: MenuItem<MenuAction>[] = [
    { label: 'Редактировать', icon: 'pencil', value: 'edit' },
    { label: 'Дублировать', icon: 'copy', value: 'duplicate' },
    { label: 'Удалить', icon: 'trash', value: 'delete', danger: true },
  ]

  function onMenu(action: MenuAction, r: Routine) {
    if (action === 'edit') openEdit(r)
    else if (action === 'duplicate') duplicate(r)
    else requestDelete(r)
  }

  let sorted = $derived(
    [...dayState.routines].sort((a, b) => (a.start_time ?? '99:99').localeCompare(b.start_time ?? '99:99')),
  )
</script>

<div class="screen">
  <AppBar title="Распорядок" subtitle="Таймлайн дня" large />
  <div class="screen-body">
    {#if sorted.length === 0}
      <EmptyState
        label="Пока пусто — добавьте первый пункт дня"
        cta="Добавить пункт"
        onCta={openCreate}
      />
    {:else}
      <div class="stack">
        {#each sorted as r (r.id)}
          <SwipeableRow
            right={{ label: 'Выполнено', icon: 'check', onTrigger: () => toggleDone(r, true) }}
            left={{ label: 'Удалить', icon: 'trash', onTrigger: () => requestDelete(r) }}
          >
            <ListItem
              title={r.title}
              subtitle={r.notification_text ?? r.category}
              time={fmtTime(r.start_time) + (r.end_time ? '–' + fmtTime(r.end_time) : '')}
              done={dayState.occurrences.find((o) => o.routine.id === r.id)?.done}
            >
              {#snippet leading()}
                <CheckCircle
                  checked={!!dayState.occurrences.find((o) => o.routine.id === r.id)?.done}
                  onclick={() => toggleDone(r, !dayState.occurrences.find((o) => o.routine.id === r.id)?.done)}
                />
              {/snippet}
              {#snippet trailing()}
                <Menu items={menuItems} onselect={(a) => onMenu(a, r)} onclose={() => {}}>
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
          </SwipeableRow>
        {/each}
      </div>
    {/if}
  </div>
  <Fab onclick={openCreate} />
</div>

<BottomSheet open={sheetOpen} onclose={() => (sheetOpen = false)} title={editing ? 'Изменить пункт' : 'Новый пункт'}>
  <div class="form">
    <TextField label="Название" placeholder="Например: Зарядка" value={title} oninput={(v) => (title = v)} error={titleError} autofocus />
    <div class="row">
      <div class="field-group">
        <span class="f-label">Начало</span>
        <TimeWheel value={startTime} onchange={(v) => (startTime = v)} allowClear={false} />
      </div>
      <div class="field-group">
        <span class="f-label">Конец (необязательно)</span>
        <TimeWheel value={endTime} onchange={(v) => (endTime = v)} />
      </div>
    </div>
    <div class="field-group">
      <span class="f-label">Повторение</span>
      <div class="chips">
        {#each RULE_CHIPS as chip (chip.value)}
          <Chip selected={rule === chip.value} label={chip.label} onclick={() => (rule = chip.value)} />
        {/each}
      </div>
    </div>
    <TextField label="Текст уведомления" placeholder="Напомнить о…" value={notifyText} oninput={(v) => (notifyText = v)} />
    <div class="field-group">
      <span class="f-label">Дата старта</span>
      <DateGrid value={startDate} onchange={(v) => (startDate = v)} allowClear={false} />
    </div>
    <div class="field-group">
      <span class="f-label">Категория</span>
      <div class="chips">
        {#each CATEGORIES as cat (cat)}
          <Chip selected={category === cat} label={cat} onclick={() => (category = category === cat ? null : cat)} />
        {/each}
      </div>
    </div>
    <button type="button" class="save-btn" onclick={save}>{editing ? 'Сохранить' : 'Добавить'}</button>
  </div>
</BottomSheet>

<Dialog
  open={pendingDelete !== null}
  title="Удалить пункт?"
  message={pendingDelete ? '«' + pendingDelete.title + '» будет удалено из распорядка.' : ''}
  confirmLabel="Удалить"
  danger
  onconfirm={confirmDelete}
  oncancel={() => (pendingDelete = null)}
/>

<style>
  .form {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding-top: 4px;
  }
  .row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
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
