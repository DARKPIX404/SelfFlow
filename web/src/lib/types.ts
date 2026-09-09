export interface BaseEntity {
  id: string
  owner: string
  created: string
  updated: string
  /** 0 | 1 — локальный tombstone */
  deleted: number
}

export interface Routine extends BaseEntity {
  title: string
  description: string | null
  start_time: string | null
  end_time: string | null
  recurrence_rule: 'NONE' | 'DAILY' | 'WEEKDAYS' | 'WEEKLY'
  category: string | null
  notification_text: string | null
  is_active: number
}

export interface Task extends BaseEntity {
  title: string
  description: string | null
  status: 'TODO' | 'IN_PROGRESS' | 'DONE'
  priority: 'LOW' | 'MEDIUM' | 'HIGH'
  due_date: string | null
  routine_id: string | null
  goal_id: string | null
  completed_at: string | null
}

export interface Note extends BaseEntity {
  title: string | null
  content: string
}

export interface NoteTag extends BaseEntity {
  note_id: string
  tag: string
}

export interface Habit extends BaseEntity {
  title: string
  color: string | null
  icon: string | null
  target_per_week: number | null
  sort: number | null
  reminder_time: string | null
}

export interface HabitLog extends BaseEntity {
  habit_id: string
  /** YYYY-MM-DD */
  date: string
}

export interface Goal extends BaseEntity {
  title: string
  deadline: string | null
  note: string | null
  sort: number | null
}

export interface FocusSession extends BaseEntity {
  started_at: string
  minutes: number
  task_id: string | null
  routine_id: string | null
}

export interface RoutineCompletion extends BaseEntity {
  routine_id: string
  /** YYYY-MM-DD */
  date: string
}

export interface Setting extends BaseEntity {
  key: string
  value: string | null
}

export interface RoutineTemplate extends BaseEntity {
  title: string
  items_json: string | null
}

export type SyncOp = 'upsert' | 'delete'
