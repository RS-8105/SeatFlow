import { test, expect } from '@playwright/test';

test.describe('Concurrency Lab Real Simulation', () => {
  test('Concurrency Lab controls execute simulation burst and display metrics', async ({ page }) => {
    const id = Date.now() + '_' + Math.random().toString(36).substring(2, 6);
    const labUser = {
      username: `lab_${id}`,
      email: `lab_${id}@example.com`,
      password: 'password123',
    };

    // Authenticate user to access demo endpoints
    await page.goto('/register');
    await page.fill('input#username', labUser.username);
    await page.fill('input#email', labUser.email);
    await page.fill('input#password', labUser.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/events');

    await page.goto('/lab');
    await expect(page.locator('#concurrency-lab-title')).toBeVisible();

    // Verify controls are present
    const runBtn = page.locator('button:has-text("Run Concurrency Simulation")');
    await expect(runBtn).toBeVisible();

    // Click Run Concurrency Simulation
    await runBtn.click();

    // Wait for simulation execution and results telemetry
    await expect(page.locator('text=Simulation Verification Output')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('text=ACQUIRED').first()).toBeVisible();
    await expect(page.locator('text=REJECTED').first()).toBeVisible();

    // Click Reset Demo Showtime
    const resetBtn = page.locator('button:has-text("Reset Demo Showtime")');
    await resetBtn.click();

    // Confirm modal should appear
    await expect(page.locator('text=Confirm Demo Reset')).toBeVisible();

    // Confirm reset
    const confirmResetBtn = page.locator('button:has-text("Confirm Reset")');
    await confirmResetBtn.click();

    // Success banner should appear
    await expect(page.locator('text=reset successfully')).toBeVisible({ timeout: 10000 });
  });
});
