import { test, expect } from '@playwright/test';
import { DayPage } from '../pages/DayPage.js';

test.describe('task management', () => {
  let dayPage;

  test.beforeEach(async ({ page }) => {
    dayPage = new DayPage(page);
    await dayPage.goto();
  });

  test('adds a task', async () => {
    const title = `Task ${Date.now()}`;
    await dayPage.addTask(title);
    await expect(dayPage.task(title)).toBeVisible();
  });

  test('adds a task with custom points', async () => {
    const title = `Points task ${Date.now()}`;
    await dayPage.addTask(title, { points: 5 });
    await expect(dayPage.task(title).getByLabel(`Points for "${title}"`)).toHaveValue('5');
  });

  test('completes a task and increments earned points', async () => {
    const title = `Complete me ${Date.now()}`;
    await dayPage.addTask(title, { points: 3 });

    const before = parseInt(await dayPage.earnedPoints().textContent());
    await dayPage.completeTask(title);

    await expect(dayPage.task(title)).toHaveClass(/completed/);
    await expect(dayPage.earnedPoints()).toHaveText(String(before + 3));
  });

  test('uncompleting a task decrements earned points', async () => {
    const title = `Undo me ${Date.now()}`;
    await dayPage.addTask(title, { points: 4 });

    // Complete first and wait for React state to settle before reading points.
    await dayPage.completeTask(title);
    await expect(dayPage.task(title)).toHaveClass(/completed/);
    const before = parseInt(await dayPage.earnedPoints().textContent());

    await dayPage.completeTask(title);
    await expect(dayPage.task(title)).not.toHaveClass(/completed/);
    await expect(dayPage.earnedPoints()).toHaveText(String(before - 4));
  });

  test('delete requires two clicks to confirm', async ({ page }) => {
    const title = `Delete me ${Date.now()}`;
    await dayPage.addTask(title);

    // First click arms the confirm state.
    await page.getByLabel(`Delete "${title}"`).click();
    const confirmBtn = page.getByLabel(`Confirm delete "${title}"`);
    await expect(confirmBtn).toHaveText('?');
    await expect(dayPage.task(title)).toBeVisible();

    // Second click confirms deletion.
    await confirmBtn.click();
    await expect(dayPage.task(title)).not.toBeVisible();
  });

  test('delete confirm resets if not clicked within 3 s', async ({ page }) => {
    const title = `Timeout delete ${Date.now()}`;
    await dayPage.addTask(title);

    await page.getByLabel(`Delete "${title}"`).click();
    await expect(page.getByLabel(`Confirm delete "${title}"`)).toBeVisible();

    await page.waitForTimeout(3200);
    await expect(page.getByLabel(`Delete "${title}"`)).toHaveText('×');
  });

  test('edits task title inline', async () => {
    const original = `Original ${Date.now()}`;
    const updated  = `Updated  ${Date.now()}`;
    await dayPage.addTask(original);
    await dayPage.editTaskTitle(original, updated);

    await expect(dayPage.task(updated)).toBeVisible();
    await expect(dayPage.task(original)).not.toBeVisible();
  });

  test('edits task points', async () => {
    const title = `Edit pts ${Date.now()}`;
    await dayPage.addTask(title, { points: 2 });
    await dayPage.editTaskPoints(title, 8);

    await expect(dayPage.task(title).getByLabel(`Points for "${title}"`)).toHaveValue('8');
  });

  test('copies task to next day and shows flash toast', async ({ page }) => {
    const title = `Copy me ${Date.now()}`;
    await dayPage.addTask(title);
    await dayPage.copyTaskToNextDay(title);

    await expect(page.locator('.flash-toast')).toBeVisible();
    await expect(page.locator('.flash-toast')).toContainText('Copied to');
  });

  test('reorders tasks with keyboard', async () => {
    const a = `Alpha ${Date.now()}`;
    const b = `Beta  ${Date.now()}`;
    await dayPage.addTask(a);
    await dayPage.addTask(b);

    const aBefore = await dayPage.taskIndex(a);
    const bBefore = await dayPage.taskIndex(b);
    expect(aBefore).toBeLessThan(bBefore); // sanity: a added first

    await dayPage.moveTaskUp(b);

    const aAfter = await dayPage.taskIndex(a);
    const bAfter = await dayPage.taskIndex(b);
    expect(bAfter).toBeLessThan(aAfter); // b moved above a
  });

  test('adds a recurring task', async () => {
    const title = `Daily ${Date.now()}`;
    await dayPage.addTask(title, { recurrence: 'DAILY' });

    await expect(dayPage.task(title).locator('.badge.recurrence')).toBeVisible();
    await expect(dayPage.task(title).locator('.badge.recurrence')).toContainText('daily');
  });

  test('deleting a recurring task removes it from future days', async ({ page }) => {
    const dayPage2 = new DayPage(page);
    const day1 = '2099-06-20';
    const day2 = '2099-06-21';
    const title = `Recurring delete ${Date.now()}`;

    await dayPage2.goto(day1);
    await dayPage2.addTask(title, { recurrence: 'DAILY' });

    // Pre-created instance must be visible on the next day.
    await dayPage2.goto(day2);
    await expect(dayPage2.task(title)).toBeVisible();

    // Delete from day 1.
    await dayPage2.goto(day1);
    await dayPage2.deleteTask(title);
    await expect(dayPage2.task(title)).not.toBeVisible();

    // Must also be gone from day 2.
    await dayPage2.goto(day2);
    await expect(dayPage2.task(title)).not.toBeVisible();
  });
});
