export class DayPage {
  constructor(page) {
    this.page = page;
  }

  static today() {
    // Local-time YYYY-MM-DD, matching the app's date (App.jsx uses date-fns
    // format(new Date(), 'yyyy-MM-dd'), which is local). toISOString() is UTC and
    // diverges from the app's local "today" around midnight, which made the
    // "redirects to today" navigation tests fail when CI ran near the date boundary.
    const d = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  async goto(date = DayPage.today()) {
    await this.page.goto(`/day/${date}`);
    await this.page.waitForSelector('.card');
  }

  async addTask(title, { points = 1, scheduledTime = '', recurrence = 'NONE' } = {}) {
    await this.page.fill('input[placeholder="Add a new task…"]', title);
    if (points !== 1) {
      await this.page.fill('input[title="Points"]', String(points));
    }
    if (scheduledTime) {
      await this.page.fill('input[type="time"]', scheduledTime);
    }
    if (recurrence !== 'NONE') {
      await this.page.selectOption('select[title="Recurrence"]', recurrence);
    }
    await this.page.click('button[type="submit"]');
    await this.page.waitForSelector(`span.title:text("${title}")`);
  }

  // Returns a locator scoped to the task row matching the given title.
  task(title) {
    return this.page.locator('.task-item').filter({ hasText: title });
  }

  async completeTask(title) {
    await this.task(title).getByRole('checkbox').click();
    // Wait for the API response to update React state before returning.
    await this.task(title).locator('input[type="checkbox"]').waitFor();
  }

  async deleteTask(title) {
    await this.page.getByLabel(`Delete "${title}"`).click();
    await this.page.getByLabel(`Confirm delete "${title}"`).click();
  }

  async editTaskTitle(oldTitle, newTitle) {
    // Locate the span directly - task filter breaks once the span is replaced by an input
    // because input values don't appear in textContent.
    await this.page.locator('span.title', { hasText: oldTitle }).click();
    const input = this.page.locator('input.title-input');
    await input.fill(newTitle);
    await input.press('Enter');
    await this.page.waitForSelector(`span.title:text("${newTitle}")`);
  }

  async editTaskPoints(title, newPoints) {
    const input = this.task(title).getByLabel(`Points for "${title}"`);
    await input.fill(String(newPoints));
    await input.press('Tab');
  }

  async copyTaskToNextDay(title) {
    await this.task(title).getByLabel(`Copy "${title}" to next day`).click();
  }

  async moveTaskUp(title) {
    const handle = this.task(title).getByLabel(/Reorder/);
    await handle.focus();
    await handle.press('ArrowUp');
  }

  async moveTaskDown(title) {
    const handle = this.task(title).getByLabel(/Reorder/);
    await handle.focus();
    await handle.press('ArrowDown');
  }

  earnedPoints() {
    return this.page.locator('.totals-bar b').first();
  }

  // Returns the zero-based index of a task in the list, for order assertions.
  taskIndex(title) {
    return this.page.locator('.task-item', { hasText: title }).evaluate(
      (el) => Array.from(el.parentElement.children).indexOf(el),
    );
  }

  // Sticker shelf helpers
  earnedStickers() {
    return this.page.locator('.sticker-grid .sticker:not(.placeholder)');
  }

  stickerShelf() {
    return this.page.locator('.sticker-shelf');
  }
}
