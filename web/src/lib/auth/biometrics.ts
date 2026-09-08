/**
 * Биометрия (@aparajita/capacitor-biometric-auth).
 * Натив: если включена в настройках — LockScreen сначала пробует биометрию,
 * PIN остаётся фолбэком. Браузер: всегда false (текущее поведение — PIN).
 */

import { Capacitor } from '@capacitor/core'
import { BiometricAuth } from '@aparajita/capacitor-biometric-auth'
import { getSetting } from '../settings.svelte'

export function biometricsEnabled(): boolean {
  return Capacitor.isNativePlatform() && getSetting('biometric_enabled') === '1'
}

export async function biometricsAvailable(): Promise<boolean> {
  if (!biometricsEnabled()) return false
  try {
    const result = await BiometricAuth.checkBiometry()
    return result.isAvailable
  } catch {
    return false
  }
}

/**
 * Показать системный биометрический промпт.
 * resolve(true) — авторизован; resolve(false) — отмена/ошибка (показываем PIN).
 */
export async function authenticateWithBiometrics(): Promise<boolean> {
  if (!biometricsEnabled()) return false
  try {
    await BiometricAuth.authenticate({
      reason: 'Разблокируйте SelfFlow',
      cancelTitle: 'Ввести PIN',
    })
    return true
  } catch {
    return false
  }
}
