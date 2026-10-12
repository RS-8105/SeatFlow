import { test, expect } from '@playwright/test';

test.describe('Booking Flow, Seat Selection, Checkout, and Details', () => {
  let patronUser;

  test.beforeEach(async ({ page }) => {
    const id = Date.now() + '_' + Math.random().toString(36).substring(2, 6);
    patronUser = {
      username: `patron_${id}`,
      email: `patron_${id}@example.com`,
      password: 'password123',
    };

    // Register and login patron user
    await page.goto('/register');
    await page.fill('input#username', patronUser.username);
    await page.fill('input#email', patronUser.email);
    await page.fill('input#password', patronUser.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/events');
  });

  test('Seat selection respects seat limits and updates summary voucher', async ({ page }) => {
    await page.goto('/showtimes/1/seats');
    await expect(page.locator('h1')).toContainText('Allocation Matrix');

    // Select 2 available seats from Row E (seats E1 & E2)
    const seatE1 = page.locator('button[aria-label^="Seat E1,"]');
    const seatE2 = page.locator('button[aria-label^="Seat E2,"]');

    await seatE1.click();
    await seatE2.click();

    // Verify voucher summary updates
    await expect(page.locator('text=Selected Allocation (2/6)')).toBeVisible();
    await expect(page.locator('text=Total Commitment')).toBeVisible();

    // Verify Proceed to Checkout button is enabled with 2 seats
    const checkoutBtn = page.locator('button:has-text("Proceed to Checkout (2 Seats)")');
    await expect(checkoutBtn).toBeEnabled();

    // Selecting more than 6 seats should trigger the maximum limit notice
    const seatE3 = page.locator('button[aria-label^="Seat E3,"]');
    const seatE4 = page.locator('button[aria-label^="Seat E4,"]');
    const seatE5 = page.locator('button[aria-label^="Seat E5,"]');
    const seatE6 = page.locator('button[aria-label^="Seat E6,"]');
    const seatE7 = page.locator('button[aria-label^="Seat E7,"]');

    await seatE3.click();
    await seatE4.click();
    await seatE5.click();
    await seatE6.click();
    // 7th seat attempt
    await seatE7.click();

    // Max limit notice should be visible
    await expect(page.locator('text=Maximum 6 seats per order permitted')).toBeVisible();

    // Clear selection
    await page.click('button:has-text("Clear Selection")');
    await expect(page.locator('text=Selected Allocation (0/6)')).toBeVisible();
    await expect(page.locator('text=No seats selected yet')).toBeVisible();
  });

  test('Checkout creates hold, allows releasing hold back to pool with modal confirmation', async ({ page }) => {
    await page.goto('/showtimes/1/seats');

    // Select seat B2
    const seatB2 = page.locator('button[aria-label^="Seat B2,"]');
    await seatB2.click();

    // Proceed to checkout
    await page.click('button:has-text("Proceed to Checkout")');
    await page.waitForURL('**/checkout');
    await expect(page).toHaveURL(/\/checkout/);

    // Verify checkout ledger displays seat summary and hold active
    await expect(page.locator('text=Seat Allocation Summary')).toBeVisible();
    await expect(page.locator('button:has-text("Release Hold Back to Pool")')).toBeVisible();

    // Click Release Hold Back to Pool
    await page.click('button:has-text("Release Hold Back to Pool")');

    // Should redirect back to seats page with released modal confirmation
    await page.waitForURL('**/showtimes/1/seats');
    await expect(page.locator('role=dialog')).toBeVisible();
    await expect(page.locator('text=Seat Released')).toBeVisible();
    await expect(page.locator('text=Inventory Restored to Pool')).toBeVisible();

    // Close modal
    await page.click('button:has-text("Continue Selection")');
    await expect(page.locator('role=dialog')).not.toBeVisible();
  });

  test('Complete booking lifecycle: hold, confirm reservation, view details, and verify in booking history', async ({ page }) => {
    await page.goto('/showtimes/1/seats');

    // Dynamically select an available seat from Row E
    const availableSeat = page.locator('button[aria-label*="Status: AVAILABLE"]').first();
    const ariaLabel = await availableSeat.getAttribute('aria-label');
    const seatName = ariaLabel ? ariaLabel.split(',')[0].trim() : 'Seat';
    await availableSeat.click();

    // Proceed to checkout
    await page.click('button:has-text("Proceed to Checkout")');
    await page.waitForURL('**/checkout');

    // Fill attendee details
    await page.fill('input#firstName', 'Alex');
    await page.fill('input#lastName', 'Patron');
    await page.fill('input#checkout-email', patronUser.email);

    // Click Confirm & Settle Reservation
    const confirmBtn = page.locator('button:has-text("Confirm & Settle Reservation")');
    await expect(confirmBtn).toBeEnabled();
    await confirmBtn.click();

    // Should navigate to /bookings/{id}
    await page.waitForURL('**/bookings/*');
    await expect(page).toHaveURL(/\/bookings\/\d+/);

    // Verify Booking Details page displays all critical information
    await expect(page.locator('text=OFFICIAL DISPATCH VOUCHER')).toBeVisible();
    await expect(page.locator('text=STATUS: CONFIRMED')).toBeVisible();
    await expect(page.locator('h1')).toContainText('Coldplay: Music of the Spheres');
    await expect(page.locator('text=Starlight Amphitheater')).toBeVisible();

    // Navigate to /bookings and verify booking appears in list
    await page.click('a[href="/bookings"]');
    await page.waitForURL('**/bookings');
    await expect(page.locator('text=Coldplay: Music of the Spheres').first()).toBeVisible();
    await expect(page.locator('text=CONFIRMED').first()).toBeVisible();

    // Refresh page: booking should still be listed
    await page.reload();
    await expect(page.locator('text=Coldplay: Music of the Spheres').first()).toBeVisible();
    await expect(page.locator('text=CONFIRMED').first()).toBeVisible();
  });
});
