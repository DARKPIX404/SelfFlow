import { expect, test, type Page } from '@playwright/test'

/**
 * Фаза 4: сохранение рутины должно приводить к перепланированию локальных
 * уведомлений. Нативных плагинов в браузере нет — подменяем
 * LocalNotifications моком через тестовый шов __sf.__setLocalNotificationsForTest
 * и проверяем, что schedule() вызван с уведомлением рутины.
 */

interface LnMock {
  calls: { schedule: unknown[]; cancel: unknown[]; channels: unknown[] }
}

async function registerFreshUser(page: Page): Promise<void> {
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@test.local`
  const password = 'e2e-password-123'

  await page.goto('/#/register')
  await page.waitForSelector('input[type="email"]', { timeout: 15_000 })

  await page.locator('input[type="email"]').fill(email)
  // LoginScreen и RegisterScreen: email + два поля пароля (register) или одно
  const passwordInputs = page.locator('input[type="password"]')
  await passwordInputs.nth(0).fill(password)
  if ((await passwordInputs.count()) > 1) await passwordInputs.nth(1).fill(password)
  await page.locator('button[type="submit"]').click()

  // после входа может показаться onboarding — пропускаем
  const skip = page.locator('text=Пропустить')
  try {
    await skip.click({ timeout: 3_000 })
  } catch {
    // onboarding уже пройден
  }
}

test('создание рутины перепланирует локальные уведомления (мок плагина)', async ({ page }) => {
  await registerFreshUser(page)

  // ждём загрузки приложения и ставим мок плагина уведомлений
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)
  await page.evaluate(() => {
    const mock = {
      calls: { schedule: [] as unknown[], cancel: [] as unknown[], channels: [] as unknown[] },
      async schedule(o: unknown) {
        mock.calls.schedule.push(o)
      },
      async cancel(o: unknown) {
        mock.calls.cancel.push(o)
      },
      async getPending() {
        return { notifications: [] as { id: number }[] }
      },
      async createChannel(c: unknown) {
        mock.calls.channels.push(c)
      },
    }
    ;(window as unknown as { __lnMock: unknown }).__lnMock = mock
    const sf = (window as unknown as { __sf: Record<string, (m: unknown) => void> }).__sf
    sf.__setLocalNotificationsForTest(mock)
  })

  // переходим в «Распорядок» и создаём пункт.
  // Важно: inactive-таб показывает только иконку (подпись — aria-label).
  await page.getByRole('button', { name: 'Распорядок', exact: true }).click()
  await page.getByRole('button', { name: 'Добавить пункт' }).click()
  await page.locator('input[placeholder="Например: Зарядка"]').fill('E2E зарядка')
  await page.getByRole('button', { name: 'Ежедневно' }).click()

  // ставим дату старта на завтра, чтобы время уведомления точно было в будущем
  await page.locator('button.date-trigger').click()
  const tomorrow = new Date(Date.now() + 24 * 3600 * 1000).getDate()
  await page
    .locator('.grid-sheet button.day', { hasText: String(tomorrow) })
    .first()
    .click()

  await page.locator('button.save-btn').click()

  // ждём debounce (500 мс) + запас
  await page.waitForTimeout(1500)

  const schedules = await page.evaluate(() => {
    const mock = (window as unknown as { __lnMock: LnMock }).__lnMock
    return mock.calls.schedule
  })
  expect(schedules.length).toBeGreaterThan(0)

  const first = schedules[0] as { notifications: { title: string; body: string; channelId: string; schedule: { at: string } }[] }
  expect(first.notifications.length).toBeGreaterThan(0)
  const routineNotes = first.notifications.filter((n) => n.body === 'E2E зарядка')
  expect(routineNotes.length).toBeGreaterThan(0)
  expect(routineNotes[0].channelId).toBe('routine_reminders')
  expect(new Date(routineNotes[0].schedule.at).getTime()).toBeGreaterThan(Date.now())

  // каналы созданы (routine_reminders + alarm_channel)
  const channels = await page.evaluate(() => {
    const mock = (window as unknown as { __lnMock: LnMock }).__lnMock
    return mock.calls.channels
  })
  const channelIds = (channels as { id: string }[]).map((c) => c.id)
  expect(channelIds).toContain('routine_reminders')
  expect(channelIds).toContain('alarm_channel')
})
