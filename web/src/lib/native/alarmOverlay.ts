import { registerPlugin } from '@capacitor/core'

/**
 * Типизированный доступ к кастомному Kotlin-плагину AlarmOverlayPlugin
 * (android/app/src/main/java/com/selfflow/app/alarm/). На вебе методы — no-op
 * заглушки (WebPlugin), поэтому вызывать можно безопасно откуда угодно.
 */

export interface ScheduleAlarmOptions {
  id: string
  /** ISO-строка (UTC, '...Z') — парсится нативно */
  timeIso: string
  title: string
  text: string
  /** ключ рингтона из res/raw: alarm_standard | lofi_chime | lofi_pluck | digital_beep | classic_bell */
  sound: string
  vibrate: boolean
  snoozeMinutes: number
}

export interface RepeatingAlarm {
  id: string
  hour: number
  minute: number
  title: string
  text: string
  sound: string
}

export interface OneShotAlarm {
  id: string
  /** эпоха в миллисекундах */
  timeMillis: number
  title: string
  text: string
  sound: string
  vibrate: boolean
  snoozeMinutes: number
}

export interface AlarmOverlayPermissions {
  exactAlarms: boolean
  notifications: boolean
  overlay: boolean
}

export interface AlarmOverlayPlugin {
  scheduleAlarm(options: ScheduleAlarmOptions): Promise<void>
  cancelAlarm(options: { id: string }): Promise<void>
  /** wake/sleep-повторы + одиночные; кэширует расписание для BootReceiver */
  rescheduleAll(options: {
    repeating: RepeatingAlarm[]
    oneShot: OneShotAlarm[]
  }): Promise<AlarmOverlayPermissions>
  checkOverlayPermission(): Promise<{ granted: boolean }>
  requestOverlayPermission(): Promise<{ granted: boolean }>
  showOverlayNow(options: { title?: string; text?: string }): Promise<void>
  checkPermissions(): Promise<AlarmOverlayPermissions>
  requestExactAlarmPermission(): Promise<{ granted: boolean }>
}

export const AlarmOverlay = registerPlugin<AlarmOverlayPlugin>('AlarmOverlay')
