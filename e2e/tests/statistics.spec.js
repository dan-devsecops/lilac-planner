import { test, expect } from '@playwright/test';

test.describe('statistics', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/statistics');
    await page.waitForURL(/statistics/);
  });

  test('loads without error', async ({ page }) => {
    await expect(page.locator('body')).not.toContainText('Error');
    await expect(page.locator('body')).not.toContainText('Loading');
  });

  test('shows summary tiles', async ({ page }) => {
    // Summary tile labels are always rendered regardless of data
    await expect(page.locator('text=Total points')).toBeVisible();
    await expect(page.locator('text=Tasks completed')).toBeVisible();
  });

  test('switches between time ranges', async ({ page }) => {
    for (const range of ['Week', 'Month', 'Quarter', 'Year']) {
      await page.getByRole('button', { name: range }).click();
      await expect(page.getByRole('button', { name: range })).toHaveClass(/active|selected|current/);
    }
  });

  test('chart renders after switching to Month range', async ({ page }) => {
    await page.getByRole('button', { name: 'Month' }).click();
    // recharts renders a .recharts-wrapper containing the main chart svg
    await expect(page.locator('.recharts-wrapper').first()).toBeVisible();
  });
});
