import { test, expect } from '@playwright/test';
import { DayPage } from '../pages/DayPage.js';

// Each test uses an isolated far-future date so task completions don't
// interfere with the sticker threshold of other tests.
const DATES = {
  earnSticker:    '2099-06-10',
  uncompleteTask: '2099-06-11',
  deleteTask:     '2099-06-12',
};

test.describe('sticker awards', () => {
  test('earns a sticker when completed points reach the threshold', async ({ page }) => {
    const dayPage = new DayPage(page);
    await dayPage.goto(DATES.earnSticker);

    const title = `Threshold task ${Date.now()}`;
    await dayPage.addTask(title, { points: 20 });

    await expect(dayPage.stickerShelf()).toContainText('20 points');
    await expect(dayPage.earnedStickers()).toHaveCount(0);

    await dayPage.completeTask(title);

    await expect(dayPage.earnedStickers()).toHaveCount(1);
  });

  test('sticker is removed when a completed task is unchecked below the threshold', async ({ page }) => {
    const dayPage = new DayPage(page);
    await dayPage.goto(DATES.uncompleteTask);

    const title = `Undo sticker ${Date.now()}`;
    await dayPage.addTask(title, { points: 20 });
    await dayPage.completeTask(title);
    await expect(dayPage.earnedStickers()).toHaveCount(1);

    await dayPage.completeTask(title);

    await expect(dayPage.earnedStickers()).toHaveCount(0);
    await expect(dayPage.stickerShelf()).toContainText('20 points');
  });

  test('sticker is removed when a completed task is deleted', async ({ page }) => {
    const dayPage = new DayPage(page);
    await dayPage.goto(DATES.deleteTask);

    const title = `Delete sticker ${Date.now()}`;
    await dayPage.addTask(title, { points: 20 });
    await dayPage.completeTask(title);
    await expect(dayPage.earnedStickers()).toHaveCount(1);

    await dayPage.deleteTask(title);

    await expect(dayPage.earnedStickers()).toHaveCount(0);
    await expect(dayPage.stickerShelf()).toContainText('20 points');
  });
});
