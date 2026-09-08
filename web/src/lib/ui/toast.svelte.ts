/**
 * Toast-менеджер: нижний плавающий снекбар, с опциональным action «Отменить».
 * Единый стейт для всех экранов.
 */

export interface ToastData {
  message: string
  actionLabel?: string
  onAction?: () => void
  durationMs?: number
}

export const toastState = $state<{ current: ToastData | null }>({ current: null })

let timer: ReturnType<typeof setTimeout> | null = null

export function showToast(data: ToastData): void {
  if (timer) clearTimeout(timer)
  toastState.current = data
  timer = setTimeout(() => {
    toastState.current = null
  }, data.durationMs ?? 4000)
}

export function hideToast(): void {
  if (timer) clearTimeout(timer)
  toastState.current = null
}
