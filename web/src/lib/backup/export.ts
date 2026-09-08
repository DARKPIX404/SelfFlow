import { Capacitor } from '@capacitor/core'
import { Directory, Encoding, Filesystem } from '@capacitor/filesystem'
import { Share } from '@capacitor/share'
import { BACKUP_TABLES, BACKUP_VERSION, type BackupFile, type BackupRow } from './format'
import { getDb } from '../db'

/**
 * Экспорт бэкапа: все записи текущего owner (включая tombstone) → JSON v2.
 * Нативно — файл в кэше + системный диалог «поделиться» (@capacitor/share),
 * в браузере — скачивание через Blob.
 */

export function buildBackup(owner: string): BackupFile {
  const db = getDb()
  const data = {} as BackupFile['data']
  for (const table of BACKUP_TABLES) {
    data[table] = db.query<BackupRow>(`SELECT * FROM ${table} WHERE owner = ?`, [owner])
  }
  return { version: BACKUP_VERSION, exportedAt: new Date().toISOString(), data }
}

export function backupFileName(): string {
  const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  return `selfflow-backup-${stamp}.json`
}

function downloadInBrowser(json: string, fileName: string): void {
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/** @returns текст JSON бэкапа (для тестов и переиспользования) */
export async function exportBackup(owner: string): Promise<{ json: string; fileName: string }> {
  const file = buildBackup(owner)
  const json = JSON.stringify(file, null, 2)
  const fileName = backupFileName()
  if (Capacitor.isNativePlatform()) {
    const written = await Filesystem.writeFile({
      path: fileName,
      data: json,
      directory: Directory.Cache,
      encoding: Encoding.UTF8,
    })
    try {
      await Share.share({
        title: 'Бэкап SelfFlow',
        url: written.uri,
        dialogTitle: 'Экспорт данных SelfFlow',
      })
    } catch (e) {
      // пользователь закрыл диалог «поделиться» — файл уже записан, это не ошибка
      const msg = typeof e === 'object' && e !== null && 'message' in e ? String(e.message) : ''
      if (!/cancel|cancelled|canceled/i.test(msg)) throw e
    }
  } else {
    downloadInBrowser(json, fileName)
  }
  return { json, fileName }
}
