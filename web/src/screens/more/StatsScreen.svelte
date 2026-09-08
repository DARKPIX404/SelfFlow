<script lang="ts">
  import { getDb } from '$lib/db'
  import { todayLocal } from '$lib/db/id'
  import { session } from '$lib/auth/session.svelte'
  import AppBar from '$lib/ui/AppBar.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import ProgressRing from '$lib/ui/ProgressRing.svelte'
  import type { Task, Habit, HabitLog } from '$lib/types'
  import { completionSet } from '$lib/routines'

  let version = $state(0)
  if (typeof window !== 'undefined') {
    window.addEventListener('selfflow:mutated', () => version++)
    window.addEventListener('selfflow:synced', () => version++)
  }

  interface Stats {
    total: number
    done: number
    byStatus: { label: string; value: number; color: string }[]
    byPriority: { label: string; value: number; color: string }[]
    routinesToday: number
    routinesDone: number
    notesCount: number
    habitsCount: number
    habitLogsWeek: number
  }

  function compute(): Stats | null {
    void version
    const user = session.user
    if (!user) return null
    const allTasks = getDb().query<Task>('SELECT * FROM tasks WHERE owner = ? AND deleted = 0', [user.id])
    const total = allTasks.length
    const done = allTasks.filter((t) => t.status === 'DONE').length
    const inProgress = allTasks.filter((t) => t.status === 'IN_PROGRESS').length
    const todo = allTasks.filter((t) => t.status === 'TODO').length
    const high = allTasks.filter((t) => t.priority === 'HIGH').length
    const med = allTasks.filter((t) => t.priority === 'MEDIUM').length
    const low = allTasks.filter((t) => t.priority === 'LOW').length

    const today = todayLocal()
    const habits = getDb().query<Habit>('SELECT * FROM habits WHERE owner = ? AND deleted = 0', [user.id])
    const logsWeek = getDb().query<HabitLog>(
      "SELECT * FROM habit_logs WHERE owner = ? AND deleted = 0 AND date >= date(?, '-6 days')",
      [user.id, today],
    )

    const routineRows = getDb().query<{ n: number }>(
      'SELECT COUNT(*) AS n FROM routines WHERE owner = ? AND deleted = 0 AND is_active = 1',
      [user.id],
    )

    return {
      total,
      done,
      byStatus: [
        { label: 'К выполнению', value: todo, color: 'var(--text-2)' },
        { label: 'В работе', value: inProgress, color: 'var(--warn)' },
        { label: 'Выполнено', value: done, color: 'var(--success)' },
      ],
      byPriority: [
        { label: 'Высокий', value: high, color: 'var(--warn)' },
        { label: 'Средний', value: med, color: 'var(--accent)' },
        { label: 'Низкий', value: low, color: 'var(--border)' },
      ],
      routinesToday: routineRows[0]?.n ?? 0,
      routinesDone: completionSet(user.id, today).size,
      notesCount:
        getDb().queryOne<{ n: number }>('SELECT COUNT(*) AS n FROM notes WHERE owner = ? AND deleted = 0', [user.id])?.n ?? 0,
      habitsCount: habits.length,
      habitLogsWeek: logsWeek.length,
    }
  }

  let stats = $derived(compute())

  function bars(rows: { label: string; value: number; color: string }[]) {
    const max = Math.max(1, ...rows.map((r) => r.value))
    return rows.map((r) => ({ ...r, pct: (r.value / max) * 100 }))
  }
</script>

<div class="screen">
  <AppBar title="Статистика" large />
  <div class="screen-body">
    {#if !stats || stats.total === 0 && stats.notesCount === 0}
      <EmptyState label="Пока недостаточно данных — добавьте задачи и заметки" />
    {:else}
      <section class="card ring-card">
        <ProgressRing
          value={stats.total ? stats.done / stats.total : 0}
          size={120}
          label="{stats.total ? Math.round((stats.done / stats.total) * 100) : 0}%"
        />
        <div class="ring-info">
          <h2>Задачи выполнены</h2>
          <p>{stats.done} из {stats.total}</p>
        </div>
      </section>

      <section class="card">
        <h3 class="card-title">По статусам</h3>
        {#each bars(stats.byStatus) as row (row.label)}
          <div class="bar-row">
            <span class="bar-label">{row.label}</span>
            <div class="bar-track">
              <div class="bar-fill" style="width: {row.pct}%; background: {row.color}"></div>
            </div>
            <span class="bar-value">{row.value}</span>
          </div>
        {/each}
      </section>

      <section class="card">
        <h3 class="card-title">По приоритетам</h3>
        {#each bars(stats.byPriority) as row (row.label)}
          <div class="bar-row">
            <span class="bar-label">{row.label}</span>
            <div class="bar-track">
              <div class="bar-fill" style="width: {row.pct}%; background: {row.color}"></div>
            </div>
            <span class="bar-value">{row.value}</span>
          </div>
        {/each}
      </section>

      <section class="card numbers">
        <div class="num-row">
          <span class="num">{stats.routinesDone}<span class="num-sep">/{stats.routinesToday}</span></span>
          <span class="num-label">рутин выполнено сегодня</span>
        </div>
        <div class="num-row">
          <span class="num">{stats.habitsCount}</span>
          <span class="num-label">привычек · {stats.habitLogsWeek} отметок за неделю</span>
        </div>
        <div class="num-row">
          <span class="num">{stats.notesCount}</span>
          <span class="num-label">заметок</span>
        </div>
      </section>
    {/if}
  </div>
</div>

<style>
  .ring-card {
    display: flex;
    align-items: center;
    gap: 20px;
  }
  .ring-info h2 {
    font-size: 16px;
    font-weight: 700;
    color: var(--text);
  }
  .ring-info p {
    font-size: 13px;
    color: var(--text-2);
    margin-top: 4px;
  }
  .card-title {
    font-size: 13px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
    margin-bottom: 12px;
  }
  .bar-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
  }
  .bar-row:last-child {
    margin-bottom: 0;
  }
  .bar-label {
    font-size: 13px;
    color: var(--text-2);
    width: 96px;
    flex-shrink: 0;
  }
  .bar-track {
    flex: 1;
    height: 8px;
    border-radius: 4px;
    background: var(--surface-2);
    overflow: hidden;
  }
  .bar-fill {
    height: 100%;
    border-radius: 4px;
    transition: width 300ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .bar-value {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
    min-width: 24px;
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
  .numbers {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .num-row {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }
  .num {
    font-size: 22px;
    font-weight: 800;
    color: var(--text);
    font-variant-numeric: tabular-nums;
    min-width: 40px;
  }
  .num-sep {
    color: var(--text-muted);
    font-weight: 600;
  }
  .num-label {
    font-size: 13px;
    color: var(--text-2);
  }
</style>
