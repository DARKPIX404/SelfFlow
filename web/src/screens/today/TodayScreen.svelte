<script lang="ts">
  import { dayState } from '$lib/dayState.svelte'
  import { todayLocal } from '$lib/db/id'
  import { fmtDateLong, fmtTime, greetingByHour } from '$lib/format'
  import { navigate } from '$lib/nav.svelte'
  import { syncAll } from '$lib/sync/sync'
  import AppBar from '$lib/ui/AppBar.svelte'
  import ListItem from '$lib/ui/ListItem.svelte'
  import CheckCircle from '$lib/ui/CheckCircle.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import PullToRefresh from '$lib/ui/PullToRefresh.svelte'
  import { setCompletion } from '$lib/routines'
  import { logDateSet, toggleLog, weekProgress } from '$lib/habits'
  import { tasks } from '$lib/db/repositories'
  import { session } from '$lib/auth/session.svelte'
  import ProgressRing from '$lib/ui/ProgressRing.svelte'
  import { haptic } from '$lib/ui/haptics'

  const today = todayLocal()
  const greeting = greetingByHour(new Date().getHours())

  function toggleOccurrence(routineId: string, done: boolean) {
    if (!session.user) return
    setCompletion(session.user.id, routineId, today, done)
    haptic('medium')
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  function completeTask(id: string) {
    if (!session.user) return
    haptic('medium')
    tasks.update(id, { status: 'DONE', completed_at: new Date().toISOString() })
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  function toggleHabit(habitId: string) {
    const user = session.user
    if (!user) return
    toggleLog(user.id, habitId, today)
    haptic('medium')
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  let doneCount = $derived(dayState.occurrences.filter((o) => o.done).length)

  interface TodayHabit {
    id: string
    title: string
    icon: string | null
    color: string
    done: boolean
    week: number
    target: number
  }

  let habitCards = $derived.by((): TodayHabit[] => {
    void dayState.version
    const user = session.user
    if (!user) return []
    return dayState.habits.map((h) => {
      const dates = logDateSet(user.id, h.id)
      return {
        id: h.id,
        title: h.title,
        icon: h.icon,
        color: h.color ?? 'var(--accent)',
        done: dates.has(today),
        week: weekProgress(dates, today),
        target: h.target_per_week ?? 7,
      }
    })
  })

  let activeGoal = $derived.by(() => {
    void dayState.version
    const user = session.user
    if (!user) return null
    const goal = [...dayState.goals].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.created.localeCompare(b.created))[0]
    if (!goal) return null
    const linked = tasks.list(user.id).filter((t) => t.goal_id === goal.id)
    const done = linked.filter((t) => t.status === 'DONE').length
    return { goal, done, total: linked.length }
  })
</script>

<div class="screen">
  <AppBar title={greeting} subtitle={fmtDateLong(today)} large>
    {#snippet actions()}
      <button type="button" class="icon-btn" aria-label="Обновить" onclick={() => void syncAll()}>
        <Icon name="refresh" size={20} />
      </button>
    {/snippet}
  </AppBar>

  <PullToRefresh onRefresh={syncAll}>
    <div class="screen-body">
      <section class="digest card">
        <div class="digest-row">
          <span class="d-num">{doneCount}<span class="d-sep">/{dayState.occurrences.length}</span></span>
          <span class="d-label">пунктов распорядка выполнено</span>
        </div>
        <div class="digest-row">
          <span class="d-num">{dayState.taskCounts.total - dayState.taskCounts.done}</span>
          <span class="d-label">открытых задач · {dayState.noteCount} заметок</span>
        </div>
      </section>

      {#if habitCards.length > 0}
        <h2 class="section-title">Привычки дня</h2>
        <div class="habits-row">
          {#each habitCards as h (h.id)}
            <div class="habit-mini" style="--hc: {h.color}">
              <CheckCircle checked={h.done} size={30} onclick={() => toggleHabit(h.id)} />
              <span class="hm-title">{h.title}</span>
              <span class="hm-week">{h.week}/{h.target}</span>
            </div>
          {/each}
        </div>
      {/if}

      <h2 class="section-title">Распорядок</h2>
      {#if dayState.occurrences.length === 0}
        <EmptyState small label="На сегодня ничего не запланировано" />
      {:else}
        <div class="stack">
          {#each dayState.occurrences as occ (occ.routine.id)}
            <ListItem
              title={occ.routine.title}
              subtitle={occ.routine.notification_text}
              time={fmtTime(occ.routine.start_time)}
              done={occ.done}
              onclick={() => navigate('routines')}
            >
              {#snippet leading()}
                <CheckCircle checked={occ.done} onclick={() => toggleOccurrence(occ.routine.id, !occ.done)} />
              {/snippet}
            </ListItem>
          {/each}
        </div>
      {/if}
      <button type="button" class="link-btn" onclick={() => navigate('routines')}>Весь распорядок →</button>

      {#if activeGoal}
        <h2 class="section-title">Активная цель</h2>
        <button type="button" class="goal-card" onclick={() => navigate('more', ['goals'])}>
          <ProgressRing
            value={activeGoal.total ? activeGoal.done / activeGoal.total : 0}
            size={44}
            stroke={5}
          />
          <span class="goal-text">
            <span class="goal-title">{activeGoal.goal.title}</span>
            <span class="goal-sub">{activeGoal.done}/{activeGoal.total} задач</span>
          </span>
          <Icon name="chevron-right" size={18} />
        </button>
      {/if}

      <h2 class="section-title">Задачи дня</h2>
      {#if dayState.tasksDue.length === 0 && dayState.tasksNoDate.length === 0}
        <EmptyState small label="Задач на сегодня нет" />
      {:else}
        <div class="stack">
          {#each [...dayState.tasksDue, ...dayState.tasksNoDate] as task (task.id)}
            <ListItem
              title={task.title}
              subtitle={task.description}
              done={false}
              onclick={() => navigate('tasks')}
            >
              {#snippet leading()}
                <CheckCircle checked={false} onclick={() => completeTask(task.id)} />
              {/snippet}
            </ListItem>
          {/each}
        </div>
      {/if}
      <button type="button" class="link-btn" onclick={() => navigate('tasks')}>Все задачи →</button>

      <button type="button" class="focus-card" onclick={() => navigate('more', ['focus'])}>
        <span class="focus-icon"><Icon name="play" size={20} /></span>
        <span class="focus-text">
          <span class="focus-title">Фокус-сессия</span>
          <span class="focus-sub">25 минут без отвлечений</span>
        </span>
        <Icon name="chevron-right" size={18} />
      </button>

      <h2 class="section-title">Прочее</h2>
      <div class="stack">
        <ListItem title="Заметки" subtitle="Идеи и мысли" icon="note" onclick={() => navigate('notes')} />
        <ListItem title="Статистика" subtitle="Прогресс и сводки" icon="chart" onclick={() => navigate('more', ['stats'])} />
        <ListItem title="Будильник" subtitle="Время подъёма и отбоя" icon="alarm" onclick={() => navigate('more', ['alarm'])} />
      </div>
    </div>
  </PullToRefresh>
</div>

<style>
  .digest {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .digest-row {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }
  .d-num {
    font-size: 22px;
    font-weight: 800;
    letter-spacing: -0.02em;
    color: var(--text);
    font-variant-numeric: tabular-nums;
    min-width: 44px;
  }
  .d-sep {
    color: var(--text-muted);
    font-weight: 600;
  }
  .d-label {
    font-size: 13px;
    color: var(--text-2);
  }
  .habits-row {
    display: flex;
    gap: 10px;
    overflow-x: auto;
    padding: 2px 2px 6px;
    scrollbar-width: none;
  }
  .habits-row::-webkit-scrollbar {
    display: none;
  }
  .habit-mini {
    flex-shrink: 0;
    width: 128px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 14px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .hm-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
    line-height: 1.25;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  .hm-week {
    font-size: 11px;
    font-weight: 600;
    color: var(--hc);
    font-variant-numeric: tabular-nums;
  }
  .goal-card {
    display: flex;
    align-items: center;
    gap: 14px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 12px 14px;
    cursor: pointer;
    color: var(--text-muted);
    text-align: left;
  }
  .goal-text {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .goal-title {
    font-size: 15px;
    font-weight: 700;
    color: var(--text);
  }
  .goal-sub {
    font-size: 12px;
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
  }
  .focus-card {
    display: flex;
    align-items: center;
    gap: 14px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 14px 16px;
    cursor: pointer;
    color: var(--text-muted);
    text-align: left;
  }
  .focus-icon {
    width: 42px;
    height: 42px;
    border-radius: 50%;
    background: var(--accent-soft);
    color: var(--accent);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .focus-text {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .focus-title {
    font-size: 15px;
    font-weight: 700;
    color: var(--text);
  }
  .focus-sub {
    font-size: 12px;
    color: var(--text-2);
  }
</style>
