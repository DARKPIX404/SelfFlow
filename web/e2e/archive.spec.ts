import { expect, test, type Page } from '@playwright/test'

/**
 * Архив задач: свайп-удаление переносит задачу в архив (мягкое удаление),
 * из архива её можно восстановить обратно в список.
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

  try {
    const skip = page.locator('button.skip')
    await skip.waitFor({ timeout: 10_000 })
    await skip.click()
  } catch {
    // onboarding уже пройден
  }
}

test('архив: задача уходит в архив и восстанавливается', async ({ page }) => {
  await registerFreshUser(page)
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)

  await page.goto('/#/tasks')
  await page.getByRole('button', { name: 'Добавить' }).first().click()
  await page.locator('input[placeholder="Что нужно сделать?"]').fill('E2E архивная')
  await page.locator('button.save-btn').click()
  await expect(page.locator('.item', { hasText: 'E2E архивная' })).toBeVisible()

  // кебаб → «В архив» → подтверждение диалога
  const row = page.locator('.task-row', { hasText: 'E2E архивная' })
  await row.locator('button[aria-label="Меню"]').click()
  await page.locator('[role="menuitem"]', { hasText: 'В архив' }).click()
  await page.locator('button', { hasText: 'В архив' }).last().click()

  await expect(page.locator('.item', { hasText: 'E2E архивная' })).toHaveCount(0)

  // открываем архив из шапки и восстанавливаем
  await page.locator('button[aria-label="Архив"]').click()
  await expect(page.locator('.item', { hasText: 'E2E архивная' })).toBeVisible()
  await page.locator('.item', { hasText: 'E2E архивная' }).locator('button[aria-label="Меню"]').click()
  await page.locator('[role="menuitem"]', { hasText: 'Восстановить' }).click()

  await expect(page.locator('.item', { hasText: 'E2E архивная' })).toHaveCount(0)
  await page.goto('/#/tasks')
  await expect(page.locator('.item', { hasText: 'E2E архивная' })).toBeVisible()
})
