/**
 * In-app обновление APK с GitHub Releases.
 * Сборка в CI встраивает номер релиза (VITE_RELEASE_NUM = github.run_number),
 * приложение сравнивает его с тегом последнего релиза (vN) и предлагает
 * скачать APK нативным плагином (UpdaterPlugin.kt), затем установить его.
 * Локальная сборка без VITE_RELEASE_NUM не считает себя устаревшей.
 */

import { Capacitor } from '@capacitor/core'
import { Updater } from '$lib/native/updater'
import { showToast } from '$lib/ui/toast.svelte'
import { haptic } from '$lib/ui/haptics'

export interface UpdateInfo {
  tag: string
  version: number
  name: string
  notes: string
  url: string
}

export const updateState = $state<{
  checking: boolean
  available: UpdateInfo | null
  downloading: boolean
  progress: number
  dismissed: boolean
  currentVersion: string
}>({
  checking: false,
  available: null,
  downloading: false,
  progress: 0,
  dismissed: false,
  currentVersion: '',
})

const MY_RELEASE = Number(import.meta.env.VITE_RELEASE_NUM ?? 0)
const REPO_API = 'https://api.github.com/repos/DARKPZ404/SelfFlow/releases/latest'

function parseRelease(json: unknown): UpdateInfo | null {
  const r = json as { tag_name?: string; name?: string; body?: string; assets?: { name: string; browser_download_url: string }[] }
  if (!r?.tag_name) return null
  const version = Number(String(r.tag_name).replace(/^v/i, ''))
  if (!Number.isFinite(version)) return null
  const asset = r.assets?.find((a) => a.name.endsWith('.apk'))
  if (!asset) return null
  return {
    tag: r.tag_name,
    version,
    name: r.name ?? r.tag_name,
    notes: r.body ?? '',
    url: asset.browser_download_url,
  }
}

export async function checkForUpdate(manual = false): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  if (updateState.checking || updateState.downloading) return
  if (MY_RELEASE <= 0 && !manual) return // локальная сборка — только ручная проверка
  updateState.checking = true
  try {
    const app = await Updater.getAppInfo()
    updateState.currentVersion = app.versionName
    const res = await fetch(REPO_API, { headers: { Accept: 'application/vnd.github+json' } })
    if (!res.ok) throw new Error(`GitHub API: ${res.status}`)
    const info = parseRelease(await res.json())
    if (info && info.version > MY_RELEASE) {
      updateState.available = info
      updateState.dismissed = false
    } else if (manual) {
      showToast({ message: 'Обновлений нет' })
    }
  } catch (e) {
    if (manual) showToast({ message: `Не удалось проверить обновления: ${e instanceof Error ? e.message : String(e)}` })
  } finally {
    updateState.checking = false
  }
}

export async function downloadAndInstall(): Promise<void> {
  const info = updateState.available
  if (!info || updateState.downloading) return
  updateState.downloading = true
  updateState.progress = 0
  const listener = await Updater.addListener('downloadProgress', (e) => {
    updateState.progress = e.percent
  })
  try {
    const { path } = await Updater.download({ url: info.url })
    await Updater.install({ path })
    updateState.available = null
    haptic('medium')
  } catch (e) {
    showToast({ message: `Ошибка обновления: ${e instanceof Error ? e.message : String(e)}`, durationMs: 8000 })
  } finally {
    await listener.remove()
    updateState.downloading = false
    updateState.progress = 0
  }
}

export function dismissUpdate(): void {
  updateState.dismissed = true
}
