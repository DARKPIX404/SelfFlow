<script lang="ts">
  import { session } from '$lib/auth/session.svelte'
  import { getDb } from '$lib/db'
  import { goals, tasks } from '$lib/db/repositories'
  import { fmtDateShort } from '$lib/format'
  import { navigate } from '$lib/nav.svelte'
  import type { Goal, Task } from '$lib/types'
  import AppBar from '$lib/ui/AppBar.svelte'
  import BottomSheet from '$lib/ui/BottomSheet.svelte'
  import CheckCircle from '$lib/ui/CheckCircle.svelte'
  import DateGrid from '$lib/ui/DateGrid.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import Fab from '$lib/ui/Fab.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import Menu, { type MenuItem } from '$lib/ui/Menu.svelte'
  import ProgressRing from '$lib/ui/ProgressRing.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'

  let version = $state(0)
  if (typeof window !== 'undefined') {
    window.addEventListener('selfflow:mutated', () => version++)
    window.addEventListener('selfflow:synced', () => version++)
  }

  function bump() {
    version++
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  interface GoalCard {
    goal: Goal
    linked: Task[]
    doneCount: number
    pct: number
  }

  let cards = $derived.by((): GoalCard[] => {
    void version
    const user = session.user
    if (!user) return []
    const allTasks = tasks.list(user.id)
    return goals
      .list(user.id)
      .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.created.localeCompare(b.created))
      .map((goal) => {
        const linked = allTasks
          .filter((t) => t.goal_id === goal.id)
          .sort((a, b) => (a.status === 'DONE' ? 1 : 0) - (b.status === 'DONE' ? 1 : 0))
        const doneCount = linked.filter((t) => t.status === 'DONE').length
        return {
          goal,
          linked,
          doneCount,
          pct: linked.length ? doneCount / linked.length : 0,
        }
      })
  })

  // --- форма ---
  let sheetOpen = $state(false)
  let editing = $state<Goal | null>(null)
  let title = $state('')
  let titleError = $state('')
  let deadline = $state<string | null>(null)
  let note = $state('')

  function openCreate() {
    editing = null
    title = ''
    titleError = ''
    deadline = null
    note = ''
    sheetOpen = true
  }

  function openEdit(g: Goal) {
    editing = g
    title = g.title
    titleError = ''
    deadline = g.deadline ? g.deadline.slice(0, 10) : null
    note = g.note ?? ''
    sheetOpen = true
  }

  function save() {
    const user = session.user
    if (!user) return
    if (!title.trim()) {
      titleError = 'Введите название'
      return
    }
    const data = {
      title: title.trim(),
      deadline: deadline ? `${deadline} 00:00:00.000Z` : null,
      note: note.trim() || null,
    }
    if (editing) {
      goals.update(editing.id, data)
    } else {
      goals.create(user.id, { ...data, sort: cards.length })
    }
    sheetOpen = false
    haptic('medium')
    bump()
  }

  // --- удаление ---
  function requestDelete(g: Goal) {
    goals.remove(g.id)
    haptic('medium')
    showToast({
      message: '«' + g.title + '» удалена',
      actionLabel: 'Отменить',
      onAction: () => {
        getDb().run('UPDATE goals SET deleted = 0 WHERE id = ?', [g.id])
        goals.update(g.id, { title: g.title })
        bump()
      },
    })
    bump()
  }

  type MenuAction = 'edit' | 'delete'
  const menuItems: MenuItem<MenuAction>[] = [
    { label: 'Редактировать', icon: 'pencil', value: 'edit' },
    { label: 'Удалить', icon: 'trash', value: 'delete', danger: true },
  ]

  function onMenu(action: MenuAction, g: Goal) {
    if (action === 'edit') openEdit(g)
    else requestDelete(g)
  }

  function toggleTask(t: Task) {
    tasks.update(t.id, {
      status: t.status === 'DONE' ? 'TODO' : 'DONE',
      completed_at: t.status === 'DONE' ? null : new Date().toISOString(),
    })
    haptic('medium')
    bump()
  }
</script>

<div class="screen">
  <AppBar title="Цели" subtitle="Долгосрочные направления" large />
  <div class="screen-body">
    {#if cards.length === 0}
      <EmptyState label="Целей пока нет" cta="Добавить цель" onCta={openCreate} />
    {:else}
      <div class="stack">
        {#each cards as card (card.goal.id)}
          <article class="goal-card">
            <div class="gc-head">
              <ProgressRing value={card.pct} size={56} stroke={6} label="{Math.round(card.pct * 100)}%" />
              <div class="gc-head-text">
                <h3 class="gc-title">{card.goal.title}</h3>
                {#if card.goal.deadline}
                  <span class="gc-deadline">до {fmtDateShort(card.goal.deadline.slice(0, 10))}</span>
                {/if}
                <span class="gc-count">{card.doneCount}/{card.linked.length} задач</span>
              </div>
              <Menu items={menuItems} onselect={(a) => onMenu(a, card.goal)} onclose={() => {}}>
                {#snippet children({ open })}
                  <button type="button" class="kebab" aria-label="Меню" onclick={open}>
                    <Icon name="dots" size={18} />
                  </button>
                {/snippet}
              </Menu>
            </div>
            {#if card.goal.note}
              <p class="gc-note">{card.goal.note}</p>
            {/if}
            {#if card.linked.length > 0}
              <div class="gc-tasks">
                {#each card.linked.slice(0, 3) as t (t.id)}
                  <div class="gc-task">
                    <CheckCircle checked={t.status === 'DONE'} size={22} onclick={() => toggleTask(t)} />
                    <span class:done={t.status === 'DONE'}>{t.title}</span>
                  </div>
                {/each}
                {#if card.linked.length > 3}
                  <button type="button" class="gc-more" onclick={() => navigate('tasks')}>
                    ещё {card.linked.length - 3} →
                  </button>
                {/if}
              </div>
            {:else}
              <button type="button" class="gc-more" onclick={() => navigate('tasks')}>
                Привязать задачи →
              </button>
            {/if}
          </article>
        {/each}
      </div>
    {/if}
  </div>
  <Fab onclick={openCreate} />
</div>

<BottomSheet open={sheetOpen} onclose={() => (sheetOpen = false)} title={editing ? 'Изменить цель' : 'Новая цель'}>
  <div class="form">
    <TextField label="Название" placeholder="Например: Подготовиться к марафону" value={title} oninput={(v) => (title = v)} error={titleError} autofocus />
    <div class="field-group">
      <span class="f-label">Дедлайн (необязательно)</span>
      <DateGrid value={deadline} onchange={(v) => (deadline = v)} />
    </div>
    <TextField label="Заметка" placeholder="Почему это важно?" value={note} oninput={(v) => (note = v)} multiline />
    <button type="button" class="save-btn" onclick={save}>{editing ? 'Сохранить' : 'Создать'}</button>
  </div>
</BottomSheet>

<style>
  .goal-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .gc-head {
    display: flex;
    align-items: center;
    gap: 14px;
  }
  .gc-head-text {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .gc-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--text);
    line-height: 1.25;
  }
  .gc-deadline {
    font-size: 12px;
    font-weight: 600;
    color: var(--warn);
  }
  .gc-count {
    font-size: 12px;
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
  }
  .kebab {
    border: none;
    background: transparent;
    color: var(--text-muted);
    width: 36px;
    height: 36px;
    border-radius: 10px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    flex-shrink: 0;
  }
  .gc-note {
    font-size: 13px;
    color: var(--text-2);
  }
  .gc-tasks {
    display: flex;
    flex-direction: column;
    gap: 6px;
    border-top: 1px solid var(--border);
    padding-top: 10px;
  }
  .gc-task {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 14px;
    color: var(--text);
  }
  .gc-task .done {
    text-decoration: line-through;
    color: var(--text-muted);
  }
  .gc-more {
    border: none;
    background: transparent;
    color: var(--accent);
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    padding: 6px 0;
    text-align: left;
    min-height: 36px;
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
</style>
