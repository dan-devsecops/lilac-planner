import { test, expect } from '@playwright/test';
import { DayPage } from '../pages/DayPage.js';

test.describe('navigation', () => {
  test('/ redirects to today', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/day\//);
    expect(page.url()).toContain(DayPage.today());
  });

  test('← Prev navigates to the previous day', async ({ page }) => {
    const today = DayPage.today();
    await page.goto(`/day/${today}`);
    await page.getByRole('button', { name: '← Prev' }).click();
    await expect(page).not.toHaveURL(new RegExp(today));
    await expect(page).toHaveURL(/\/day\/\d{4}-\d{2}-\d{2}/);
  });

  test('Next → navigates to the next day', async ({ page }) => {
    const today = DayPage.today();
    await page.goto(`/day/${today}`);
    await page.getByRole('button', { name: 'Next →' }).click();
    await expect(page).not.toHaveURL(new RegExp(today));
    await expect(page).toHaveURL(/\/day\/\d{4}-\d{2}-\d{2}/);
  });

  test('Today button returns to current day from an arbitrary date', async ({ page }) => {
    await page.goto('/day/2020-01-15');
    await page.getByRole('button', { name: 'Today' }).click();
    await expect(page).toHaveURL(new RegExp(DayPage.today()));
  });

  test('Today nav link returns to current day', async ({ page }) => {
    await page.goto('/day/2020-01-15');
    await page.getByRole('link', { name: 'Today' }).click();
    await expect(page).toHaveURL(new RegExp(DayPage.today()));
  });

  test('date picker navigates to the selected date', async ({ page }) => {
    await page.goto(`/day/${DayPage.today()}`);
    await page.fill('input[type="date"]', '2025-06-01');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/2025-06-01/);
  });

  test('Statistics link opens the statistics view', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Statistics' }).click();
    await expect(page).toHaveURL(/statistics/);
  });

  test('unknown path redirects to today', async ({ page }) => {
    await page.goto('/does-not-exist');
    await page.waitForURL(/\/day\//);
    expect(page.url()).toContain(DayPage.today());
  });
});
