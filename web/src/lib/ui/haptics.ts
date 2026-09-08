/**
 * Тонкий haptics-слой. На нативе — @capacitor/haptics, на вебе — no-op.
 *
 * selection — переключение вкладок/сегментов/колёс
 * light — tap по кнопкам
 * medium — завершение задачи, отметка рутины
 * notification — будильник / ошибка PIN
 */

import { Capacitor } from '@capacitor/core'
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics'

export type HapticKind = 'selection' | 'light' | 'medium' | 'notification'

let enabled = true
const native = Capacitor.isNativePlatform()

export function setHapticsEnabled(value: boolean): void {
  enabled = value
}

export function haptic(kind: HapticKind): void {
  if (!enabled || !native) return
  try {
    switch (kind) {
      case 'selection':
        void Haptics.selectionStart().then(() => Haptics.selectionEnd())
        break
      case 'light':
        void Haptics.impact({ style: ImpactStyle.Light })
        break
      case 'medium':
        void Haptics.impact({ style: ImpactStyle.Medium })
        break
      case 'notification':
        void Haptics.notification({ type: NotificationType.Warning })
        break
    }
  } catch {
    // haptics недоступен на устройстве — не ломаем UI
  }
}
