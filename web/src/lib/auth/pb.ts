import PocketBase, { BaseAuthStore } from 'pocketbase'
import { Capacitor } from '@capacitor/core'

export const pb = new PocketBase(import.meta.env.VITE_PB_URL as string)

// На нативе localStorage WebView ненадёжен между обновлениями — persistence
// ведём через @capacitor/preferences (см. auth/session.svelte.ts), поэтому
// встроенный store PocketBase отключаем. В браузере остаётся localStorage.
if (Capacitor.isNativePlatform()) {
  pb.authStore = new BaseAuthStore()
}
