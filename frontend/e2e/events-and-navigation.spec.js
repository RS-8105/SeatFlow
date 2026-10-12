import { test, expect } from '@playwright/test';

test.describe('Events Discovery and Navigation', () => {
  test('Application startup and navigation to event catalogue', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('text=SeatFlow').first()).toBeVisible();

    // Click Browse Events in navbar or hero
    await page.locator('a[href="/events"]').first().click();
    await page.waitForURL('**/events');
    await expect(page).toHaveURL(/\/events/);
    await expect(page.locator('h1')).toContainText('Scheduled Performances');
  });

  test('Event listing, event details, and showtimes load against backend', async ({ page }) => {
    await page.goto('/events');

    // Wait for real events from backend
    await expect(page.locator('text=Coldplay: Music of the Spheres')).toBeVisible();
    await expect(page.locator('text=Hans Zimmer Live in Concert')).toBeVisible();

    // Click View Details on Coldplay
    await page.locator('a[href="/events/1"]').first().click();
    await page.waitForURL('**/events/1');
    await expect(page).toHaveURL(/\/events\/1/);

    // Verify event details
    await expect(page.locator('h1')).toContainText('Coldplay: Music of the Spheres');
    await expect(page.locator('text=Starlight Amphitheater')).toBeVisible();

    // Verify Select Seats button exists for showtime
    const selectSeatsBtn = page.locator('a[href="/showtimes/1/seats"]').first();
    await expect(selectSeatsBtn).toBeVisible();

    // Click Select Seats
    await selectSeatsBtn.click();
    await page.waitForURL('**/showtimes/1/seats');
    await expect(page).toHaveURL(/\/showtimes\/1\/seats/);
    await expect(page.locator('h1')).toContainText('Allocation Matrix');
  });

  test('Non-existent event displays user-friendly error message, not blank page', async ({ page }) => {
    await page.goto('/events/999999');

    // Verify error container is shown with retry/return button
    await expect(page.locator('text=Event Not Found').or(page.locator('text=Failed to Load Event')).or(page.locator('text=Catalog Retrieval Fault'))).toBeVisible();
    await expect(page.locator('a[href="/events"]').first()).toBeVisible();
  });

  test('Footer navigation links, including Concurrency Lab, work as intended', async ({ page }) => {
    await page.goto('/');

    // Locate Concurrency Lab link in footer
    const footerLabLink = page.locator('footer a[href="/lab"]');
    await footerLabLink.click();
    await page.waitForURL('**/lab');
    await expect(page).toHaveURL(/\/lab/);

    // Verify Concurrency Lab title is visible at the top
    await expect(page.locator('#concurrency-lab-title')).toBeVisible();

    // Click footer Events link
    const footerEventsLink = page.locator('footer a[href="/events"]');
    await footerEventsLink.click();
    await page.waitForURL('**/events');
    await expect(page).toHaveURL(/\/events/);
  });

  test('Unknown route renders 404 Not Found without broken layout', async ({ page }) => {
    await page.goto('/some/nonexistent/route/path');

    await expect(page.locator('h1')).toHaveText('Page Not Located');
    await expect(page.locator('a:has-text("Return Home")')).toBeVisible();
  });
});
