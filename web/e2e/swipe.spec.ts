import { expect, test, type Page } from '@playwright/test'

/**
 * Свайпы задач (мышь → pointer events) и кебаб-меню (тач).
 * Регрессия: свайп влево → «в работу»; свайп вправо → выполнено;
 * кебаб открывает меню после fix min-width:0 (кнопка не уезжает за край).
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

async function createTask(page: Page, name: string): Promise<void> {
  await page.goto('/#/tasks')
  await page.getByRole('button', { name: 'Добавить' }).first().click()
  await page.locator('input[placeholder="Что нужно сделать?"]').fill(name)
  await page.locator('button.save-btn').click()
  await expect(page.locator('.item', { hasText: name })).toBeVisible()
}

test('свайп влево → в работу, свайп вправо → выполнено', async ({ page }) => {
  await registerFreshUser(page)
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)
  await createTask(page, 'E2E свайп')

  const row = page.locator('.task-row', { hasText: 'E2E свайп' })
  const box = (await row.boundingBox())!
  const cy = box.y + box.height / 2
  const cx = box.x + box.width / 2

  // свайп влево: мышь ведём от центра влево на 60% ширины
  await page.mouse.move(cx, cy)
  await page.mouse.down()
  await page.mouse.move(cx - box.width * 0.6, cy, { steps: 12 })
  await page.mouse.up()
  await page.waitForTimeout(400)

  await page.goto('/#/tasks')
  await page.locator('button.seg', { hasText: 'В работе' }).click()
  await expect(page.locator('.item', { hasText: 'E2E свайп' })).toBeVisible()

  // свайп вправо → выполнено
  const row2 = page.locator('.task-row', { hasText: 'E2E свайп' })
  const box2 = (await row2.boundingBox())!
  await page.mouse.move(box2.x + box2.width / 2, box2.y + box2.height / 2)
  await page.mouse.down()
  await page.mouse.move(box2.x + box2.width * 1.1, box2.y + box2.height / 2, { steps: 12 })
  await page.mouse.up()
  await page.waitForTimeout(400)

  await page.goto('/#/tasks')
  await page.locator('button.seg', { hasText: 'Выполнено' }).click()
  await expect(page.locator('.item', { hasText: 'E2E свайп' })).toBeVisible()
})

test('кебаб открывает меню', async ({ page }) => {
  await registerFreshUser(page)
  await page.waitForFunction(() => (window as unknown as { __sf?: unknown }).__sf != null)
  await createTask(page, 'E2E кебаб')

  const kebab = page.locator('.task-row', { hasText: 'E2E кебаб' }).locator('button[aria-label="Меню"]')
  await expect(kebab).toBeVisible()
  await kebab.click()
  await expect(page.locator('[role="menuitem"]').first()).toBeVisible()
})
