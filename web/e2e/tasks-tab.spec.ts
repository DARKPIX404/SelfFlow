import { expect, test, type Page } from '@playwright/test'

/**
 * Регрессия: переход на вкладку «Задачи» не должен бросать
 * "wrong api use: tried to bind a value of an unknown type (undefined)".
 * Тест падает, если появляется оверлей ошибки или pageerror при навигации.
 */

async function registerFreshUser(page: Page): Promise<void> {
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@test.local`
  const password = 'e2e-password-123'

  await page.goto('/#/register')
  await page.waitForSelector('input[type="email"]', { timeout: 15_000 })

  await page.locator('input[type="email"]').fill(email)
  const passwordInputs = page.locator('input[type="password"]')
  await passwordInputs.nth(0).fill(password)
  if ((await passwordInputs.count()) > 1) await passwordInputs.nth(1).fill(password)
  await page.locator('button[type="submit"]').click()

  const skip = page.locator('text=Пропустить')
  try {
    await skip.click({ timeout: 3_000 })
  } catch {
    // onboarding уже пройден
  }
}

test('переход на вкладку «Задачи» без ошибок bind(undefined)', async ({ page }) => {
  const pageErrors: Error[] = []
  page.on('pageerror', (e) => pageErrors.push(e))

  await registerFreshUser(page)
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)

  // создаём задачу через UI, чтобы вкладка рисовала непустой список
  await page.goto('/#/tasks')
  await page.getByRole('button', { name: 'Добавить' }).first().click()
  await page.locator('input[placeholder="Что нужно сделать?"]').fill('E2E задача')
  await page.locator('button.save-btn').click()

  // повторные переходы туда-обратно — как у пользователя
  for (const tab of ['today', 'routines', 'tasks', 'notes', 'tasks', 'more', 'tasks'] as const) {
    await page.goto(`/#/${tab}`)
    await page.waitForTimeout(400)
  }

  const overlay = page.locator('#error-overlay')
  const overlayText = (await overlay.count()) > 0 ? await overlay.first().textContent() : null

  expect(
    { overlayText, pageErrors: pageErrors.map((e) => String(e.stack ?? e)) },
  ).toEqual({ overlayText: null, pageErrors: [] })
})
