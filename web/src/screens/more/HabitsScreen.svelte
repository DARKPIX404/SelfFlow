<script lang="ts">
  import { session } from '$lib/auth/session.svelte'
  import { getDb } from '$lib/db'
  import { todayLocal } from '$lib/db/id'
  import { habits } from '$lib/db/repositories'
  import {
    HABIT_COLORS,
    currentStreak,
    bestStreak,
    heatmapWeeks,
    logDateSet,
    monthPercent,
    toggleLog,
    weekProgress,
  } from '$lib/habits'
  import type { Habit } from '$lib/types'
  import type { IconName } from '$lib/ui/icons'
  import AppBar from '$lib/ui/AppBar.svelte'
  import BottomSheet from '$lib/ui/BottomSheet.svelte'
  import CheckCircle from '$lib/ui/CheckCircle.svelte'
  import Chip from '$lib/ui/Chip.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import Fab from '$lib/ui/Fab.svelte'
  import Heatmap from '$lib/ui/Heatmap.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import Menu, { type MenuItem } from '$lib/ui/Menu.svelte'
  import ProgressRing from '$lib/ui/ProgressRing.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import TimeWheel from '$lib/ui/TimeWheel.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'
  import { requestRescheduleReminders } from '$lib/notifications'

  const HABIT_ICONS: IconName[] = [
    'flame', 'target', 'sun', 'moon', 'alarm', 'bell',
    'chart', 'shield', 'home', 'play', 'note', 'tag',
  ]

  const today = todayLocal()

  let version = $state(0)
  if (typeof window !== 'undefined') {
    window.addEventListener('selfflow:mutated', () => version++)
    window.addEventListener('selfflow:synced', () => version++)
  }

  function bump() {
    version++
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  interface HabitCard {
    habit: Habit
    color: string
    doneToday: boolean
    streak: number
    weekDone: number
    target: number
  }

  let cards = $derived.by((): HabitCard[] => {
    void version
    const user = session.user
    if (!user) return []
    return habits.list(user.id).map((habit) => {
      const dates = logDateSet(user.id, habit.id)
      return {
        habit,
        color: habit.color ?? 'var(--accent)',
        doneToday: dates.has(today),
        streak: currentStreak(dates, today),
        weekDone: weekProgress(dates, today),
        target: habit.target_per_week ?? 7,
      }
    })
  })

  function toggle(card: HabitCard) {
    const user = session.user
    if (!user) return
    toggleLog(user.id, card.habit.id, today)
    haptic('medium')
    bump()
  }

  // --- детальный шит ---
  let detail = $state<HabitCard | null>(null)

  let detailDates = $derived.by(() => {
    void version
    const user = session.user
    if (!user || !detail) return new Set<string>()
    return logDateSet(user.id, detail.habit.id)
  })

  let detailStats = $derived.by(() => {
    if (!detail) return null
    return {
      weeks: heatmapWeeks(detailDates, today),
      month: monthPercent(detailDates, today),
      best: bestStreak(detailDates),
    }
  })

  // --- форма ---
  let sheetOpen = $state(false)
  let editing = $state<Habit | null>(null)
  let title = $state('')
  let titleError = $state('')
  let color = $state(HABIT_COLORS[0])
  let icon = $state<IconName>('flame')
  let target = $state(7)
  let reminder = $state<string | null>(null)

  function openCreate() {
    editing = null
    title = ''
    titleError = ''
    color = HABIT_COLORS[0]
    icon = 'flame'
    target = 7
    reminder = null
    sheetOpen = true
  }

  function openEdit(h: Habit) {
    editing = h
    title = h.title
    titleError = ''
    color = h.color ?? HABIT_COLORS[0]
    icon = (h.icon as IconName | null) ?? 'flame'
    target = h.target_per_week ?? 7
    reminder = h.reminder_time ? h.reminder_time.slice(11, 16) : null
    detail = null
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
      color,
      icon,
      target_per_week: target,
      reminder_time: reminder ? `2000-01-01 ${reminder}:00.000Z` : null,
    }
    if (editing) {
      habits.update(editing.id, data)
    } else {
      habits.create(user.id, { ...data, sort: cards.length })
    }
    sheetOpen = false
    haptic('medium')
    requestRescheduleReminders()
    bump()
  }

  // --- удаление ---
  function requestDelete(h: Habit) {
    detail = null
    habits.remove(h.id)
    haptic('medium')
    showToast({
      message: '«' + h.title + '» удалена',
      actionLabel: 'Отменить',
      onAction: () => {
        getDb().run('UPDATE habits SET deleted = 0 WHERE id = ?', [h.id])
        habits.update(h.id, { title: h.title })
        requestRescheduleReminders()
        bump()
      },
    })
    requestRescheduleReminders()
    bump()
  }

  type MenuAction = 'edit' | 'delete'
  const menuItems: MenuItem<MenuAction>[] = [
    { label: 'Редактировать', icon: 'pencil', value: 'edit' },
    { label: 'Удалить', icon: 'trash', value: 'delete', danger: true },
  ]

  function onMenu(action: MenuAction, h: Habit) {
    if (action === 'edit') openEdit(h)
    else requestDelete(h)
  }
</script>

<div class="screen">
  <AppBar title="Привычки" subtitle="Ежедневные отметки" large />
  <div class="screen-body">
    {#if cards.length === 0}
      <EmptyState label="Привычек пока нет" cta="Добавить привычку" onCta={openCreate} />
    {:else}
      <div class="grid">
        {#each cards as card (card.habit.id)}
          <article class="habit-card" style="--hc: {card.color}">
            <div class="hc-top">
              <CheckCircle checked={card.doneToday} size={34} onclick={() => toggle(card)} />
              <Menu items={menuItems} onselect={(a) => onMenu(a, card.habit)} onclose={() => {}}>
                {#snippet children({ open })}
                  <button type="button" class="kebab" aria-label="Меню" onclick={open}>
                    <Icon name="dots" size={18} />
                  </button>
                {/snippet}
              </Menu>
            </div>
            <button type="button" class="hc-main" onclick={() => (detail = card)}>
              <span class="hc-icon"><Icon name={(card.habit.icon as IconName | null) ?? 'flame'} size={20} /></span>
              <span class="hc-title">{card.habit.title}</span>
              <span class="hc-streak" class:lit={card.streak > 0}>
                <Icon name="flame" size={14} />
                {card.streak > 0 ? `${card.streak} ${card.streak === 1 ? 'день' : card.streak < 5 ? 'дня' : 'дней'} подряд` : 'нет серии'}
              </span>
              <span class="hc-week">
                <ProgressRing value={Math.min(1, card.weekDone / card.target)} size={26} stroke={4} color="var(--hc)" />
                <span class="hc-week-label">{card.weekDone}/{card.target} нед.</span>
              </span>
            </button>
          </article>
        {/each}
      </div>
    {/if}
  </div>
  <Fab onclick={openCreate} />
</div>

<BottomSheet open={sheetOpen} onclose={() => (sheetOpen = false)} title={editing ? 'Изменить привычку' : 'Новая привычка'}>
  <div class="form">
    <TextField label="Название" placeholder="Например: Чтение 20 минут" value={title} oninput={(v) => (title = v)} error={titleError} autofocus />
    <div class="field-group">
      <span class="f-label">Цвет</span>
      <div class="swatches">
        {#each HABIT_COLORS as c (c)}
          <button
            type="button"
            class="swatch"
            class:sel={color === c}
            style="background: {c}"
            aria-label="Цвет {c}"
            onclick={() => { haptic('selection'); color = c }}
          ></button>
        {/each}
      </div>
    </div>
    <div class="field-group">
      <span class="f-label">Иконка</span>
      <div class="icon-grid">
        {#each HABIT_ICONS as name (name)}
          <button
            type="button"
            class="icon-pick"
            class:sel={icon === name}
            aria-label="Иконка {name}"
            onclick={() => { haptic('selection'); icon = name }}
          >
            <Icon {name} size={20} />
          </button>
        {/each}
      </div>
    </div>
    <div class="field-group">
      <span class="f-label">Цель в неделю</span>
      <div class="chips">
        {#each [1, 2, 3, 4, 5, 6, 7] as n (n)}
          <Chip selected={target === n} label={n === 7 ? 'Каждый день' : String(n)} onclick={() => (target = n)} />
        {/each}
      </div>
    </div>
    <div class="field-group">
      <span class="f-label">Напоминание (необязательно)</span>
      <TimeWheel value={reminder} onchange={(v) => (reminder = v)} />
    </div>
    <button type="button" class="save-btn" onclick={save}>{editing ? 'Сохранить' : 'Создать'}</button>
  </div>
</BottomSheet>

<BottomSheet open={detail !== null} onclose={() => (detail = null)} title={detail?.habit.title ?? ''}>
  {#if detail && detailStats}
    <div class="detail">
      <div class="detail-head" style="--hc: {detail.color}">
        <span class="hc-icon big"><Icon name={(detail.habit.icon as IconName | null) ?? 'flame'} size={24} /></span>
        <div class="detail-head-text">
          <span class="hc-streak" class:lit={detail.streak > 0}>
            <Icon name="flame" size={14} />
            {detail.streak > 0 ? `${detail.streak} дн. подряд` : 'нет серии'}
          </span>
          <span class="hc-week-label">{detail.weekDone}/{detail.target} за текущую неделю</span>
        </div>
        <CheckCircle checked={detailDates.has(today)} size={34} onclick={() => { const c = detail; if (c) toggle(c) }} />
      </div>

      <div class="field-group">
        <span class="f-label">Последние 12 недель</span>
        <Heatmap weeks={detailStats.weeks} />
      </div>

      <div class="stat-grid">
        <div class="stat">
          <span class="stat-num">{detailStats.month}%</span>
          <span class="stat-label">выполнено за месяц</span>
        </div>
        <div class="stat">
          <span class="stat-num">{detailStats.best}</span>
          <span class="stat-label">лучшая серия, дней</span>
        </div>
      </div>

      <div class="detail-actions">
        <button type="button" class="btn ghost" onclick={() => openEdit(detail!.habit)}>
          <Icon name="pencil" size={17} /> Редактировать
        </button>
        <button type="button" class="btn danger" onclick={() => requestDelete(detail!.habit)}>
          <Icon name="trash" size={17} /> Удалить
        </button>
      </div>
    </div>
  {/if}
</BottomSheet>

<style>
  .grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
  .habit-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .hc-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
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
  }
  .hc-main {
    border: none;
    background: transparent;
    padding: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    cursor: pointer;
    text-align: left;
    color: var(--text);
  }
  .hc-icon {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--hc) 18%, transparent);
    color: var(--hc);
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  .hc-icon.big {
    width: 44px;
    height: 44px;
  }
  .hc-title {
    font-size: 15px;
    font-weight: 700;
    line-height: 1.25;
  }
  .hc-streak {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-muted);
  }
  .hc-streak.lit {
    color: var(--accent);
  }
  .hc-week {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
  .hc-week-label {
    font-size: 12px;
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
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
  .swatches {
    display: flex;
    gap: 10px;
  }
  .swatch {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: 2px solid transparent;
    cursor: pointer;
    transition: transform 120ms;
  }
  .swatch.sel {
    border-color: var(--text);
    transform: scale(1.1);
  }
  .icon-grid {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 8px;
  }
  .icon-pick {
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text-2);
    border-radius: 12px;
    aspect-ratio: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
  }
  .icon-pick.sel {
    background: var(--accent-soft);
    color: var(--accent);
    border-color: transparent;
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
  .detail {
    display: flex;
    flex-direction: column;
    gap: 18px;
    padding-top: 4px;
  }
  .detail-head {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .detail-head-text {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .stat-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
  .stat {
    background: var(--surface-2);
    border-radius: 12px;
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .stat-num {
    font-size: 22px;
    font-weight: 800;
    color: var(--text);
    font-variant-numeric: tabular-nums;
  }
  .stat-label {
    font-size: 12px;
    color: var(--text-2);
  }
  .detail-actions {
    display: flex;
    gap: 10px;
  }
  .btn {
    flex: 1;
    border: none;
    border-radius: 12px;
    padding: 12px;
    min-height: 46px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
  }
  .btn.ghost {
    background: var(--surface-2);
    color: var(--text);
  }
  .btn.danger {
    background: var(--accent-soft);
    color: var(--accent);
  }
</style>
