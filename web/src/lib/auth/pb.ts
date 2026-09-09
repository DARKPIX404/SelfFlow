import PocketBase, { BaseAuthStore } from 'pocketbase'
import { Capacitor } from '@capacitor/core'

// Фолбэк обязателен: CI собирает APK без .env, и без URL авторизация
// уходит на локальный веб-сервер WebView, который отвечает index.html
// с кодом 200 — SDK парсит это как «успех без токена» и пускает кого угодно.
const PB_URL =
  (import.meta.env.VITE_PB_URL as string | undefined)?.trim() ||
  'https://goldenrod-gahnospinel367650.vm-host.com'

export const pb = new PocketBase(PB_URL)

// На нативе localStorage WebView ненадёжен между обновлениями — persistence
// ведём через @capacitor/preferences (см. auth/session.svelte.ts), поэтому
// встроенный store PocketBase отключаем. В браузере остаётся localStorage.
if (Capacitor.isNativePlatform()) {
  pb.authStore = new BaseAuthStore()
}
