import { expect, test, type Page } from '@playwright/test'

/**
 * Регрессия фокус-сессий: старт, стоп и закрытие экрана не должны бросать
 * ошибок (падало на записи focusSessions с owner=undefined у «фейковой»
 * сессии — теперь страховка на уровне входа + sanitize в драйвере).
 */

async function registerFreshUser(page: Page): Promise<void> {
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@test.local`
  await page.goto('/#/register')
  await page.waitForSelector('input[type="email"]', { timeout: 15_000 })
  await page.locator('input[type="email"]').fill(email)
  const passwordInputs = page.locator('input[type="password"]')
  await passwordInputs.nth(0).fill('e2e-password-123')
  if ((await passwordInputs.count()) > 1) await passwordInputs.nth(1).fill('e2e-password-123')
  await page.locator('button[type="submit"]').click()
  // после входа может показаться onboarding — ждём появления и пропускаем
  try {
    const skip = page.locator('button.skip')
    await skip.waitFor({ timeout: 10_000 })
    await skip.click()
  } catch {
    // onboarding уже пройден
  }
}

test('фокус: старт → стоп → закрытие без ошибок', async ({ page }) => {
  const pageErrors: Error[] = []
  page.on('pageerror', (e) => pageErrors.push(e))

  await registerFreshUser(page)
  await page.waitForSelector('nav.tabbar', { timeout: 15_000 })

  // открываем фокус через «Ещё»
  await page.goto('/#/more')
  await page.getByText('Фокус', { exact: true }).click()
  await expect(page.locator('.focus-title')).toBeVisible()

  // старт и сразу стоп
  await page.getByRole('button', { name: 'Начать фокус' }).click()
  await expect(page.locator('.timer-time')).toBeVisible()
  await page.getByRole('button', { name: 'Стоп' }).click()

  // закрытие экрана назад — срабатывает back() → stop() при активной фазе
  await page.getByRole('button', { name: 'Начать фокус' }).click()
  await page.getByRole('button', { name: 'Назад' }).click()

  // снова на «Ещё», оверлея ошибки нет
  await expect(page.getByRole('heading', { name: 'Ещё' })).toBeVisible({ timeout: 10_000 })
  expect(await page.locator('#error-overlay').count()).toBe(0)
  expect(pageErrors).toEqual([])

  // сессия зафиксировалась в локальной БД
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)
  const count = await page.evaluate(() => {
    const sf = (window as unknown as { __sf: { getDb: () => { query: (sql: string) => unknown[] } } }).__sf
    return sf.getDb().query('SELECT COUNT(*) AS n FROM focus_sessions').length
  })
  expect(count).toBeGreaterThan(0)
})
