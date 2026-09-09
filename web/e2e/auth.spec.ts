import { expect, test, type Page } from '@playwright/test'

/**
 * Регрессия авторизации:
 * 1. Неверный логин/пароль НЕ должен пускать в приложение (была «фейковая»
 *    авторизация: ответ WebView 200+HTML трактовался SDK как успех).
 * 2. Гостевой режим «Продолжить без аккаунта» должен открывать приложение
 *    без сервера (session.user = local-guest, authStore невалиден).
 */

async function gotoLogin(page: Page): Promise<void> {
  await page.goto('/#/login')
  await page.waitForSelector('input[type="email"]', { timeout: 15_000 })
}

test('неверный пароль отклоняется и не пускает в приложение', async ({ page }) => {
  const pageErrors: Error[] = []
  page.on('pageerror', (e) => pageErrors.push(e))

  await gotoLogin(page)
  await page.locator('input[type="email"]').fill('definitely-not-a-user@test.local')
  await page.locator('input[type="password"]').fill('wrongpass123')
  await page.locator('button[type="submit"]').click()

  // ошибка показана, остаёмся на экране входа
  await expect(page.locator('.error')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('input[type="email"]')).toBeVisible()

  // в приложение не пустили — ни таб-бара, ни оверлея ошибки
  expect(await page.locator('nav.tabbar').count()).toBe(0)
  expect(await page.locator('#error-overlay').count()).toBe(0)
  expect(pageErrors).toEqual([])

  // authStore остался пустым
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)
  const authValid = await page.evaluate(() => {
    const sf = (window as unknown as { __sf: { pb: { authStore: { isValid: boolean } } } }).__sf
    return sf.pb.authStore.isValid
  })
  expect(authValid).toBe(false)
})

test('гостевой режим: «Продолжить без аккаунта» открывает приложение', async ({ page }) => {
  const pageErrors: Error[] = []
  page.on('pageerror', (e) => pageErrors.push(e))

  await gotoLogin(page)
  await page.getByRole('button', { name: 'Продолжить без аккаунта' }).click()

  // перезагрузка → стартап под гостем (onboarding для свежей гостевой БД — пропускаем)
  await page.waitForSelector('nav.tabbar', { timeout: 15_000 })
  try {
    const skip = page.locator('button.skip')
    await skip.waitFor({ timeout: 10_000 })
    await skip.click()
  } catch {
    // onboarding уже пройден
  }
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)

  const state = await page.evaluate(() => {
    const sf = (window as unknown as { __sf: { pb: { authStore: { isValid: boolean } } } }).__sf
    return { authValid: sf.pb.authStore.isValid }
  })
  expect(state.authValid).toBe(false)

  // данные пишутся в локальную БД гостя — создаём задачу и читаем её обратно
  await page.goto('/#/tasks')
  await page.getByRole('button', { name: 'Добавить' }).first().click()
  await page.locator('input[placeholder="Что нужно сделать?"]').fill('Гостевая задача')
  await page.locator('button.save-btn').click()
  await expect(page.locator('text=Гостевая задача')).toBeVisible({ timeout: 10_000 })

  // повторный стартап: гостевая сессия восстанавливается, данные на месте.
  // веб-драйвер персистит БД в localStorage с дебаунсом 300 мс — дать дописать.
  await page.waitForTimeout(700)
  await page.reload()
  await page.waitForSelector('nav.tabbar', { timeout: 15_000 })
  await page.goto('/#/tasks')
  await expect(page.locator('text=Гостевая задача')).toBeVisible({ timeout: 10_000 })

  expect(await page.locator('#error-overlay').count()).toBe(0)
  expect(pageErrors).toEqual([])
})
