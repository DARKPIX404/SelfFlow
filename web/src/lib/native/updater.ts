import { registerPlugin } from '@capacitor/core'

/**
 * Нативный помощник in-app обновления (см. UpdaterPlugin.kt).
 * В браузере — заглушки: UI обновления существует только на устройстве.
 */

export interface AppInfo {
  versionName: string
  versionCode: number
}

export interface UpdaterPlugin {
  getAppInfo(): Promise<AppInfo>
  download(o: { url: string }): Promise<{ path: string }>
  install(o: { path: string }): Promise<void>
  addListener(event: 'downloadProgress', cb: (e: { percent: number }) => void): Promise<{ remove: () => void }>
  removeAllListeners(): Promise<void>
}

export const Updater = registerPlugin<UpdaterPlugin>('SelfFlowUpdater', {
  web: {
    getAppInfo: async () => ({ versionName: 'web', versionCode: 0 }),
    download: async () => ({ path: '' }),
    install: async () => {},
    addListener: async () => ({ remove: () => {} }),
    removeAllListeners: async () => {},
  },
})
