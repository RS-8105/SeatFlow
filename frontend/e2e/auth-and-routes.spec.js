import { test, expect } from '@playwright/test';

test.describe('Auth and Protected Routes', () => {
  test('Registration client validation and successful user registration', async ({ page }) => {
    const id = Date.now() + '_' + Math.random().toString(36).substring(2, 6);
    const testUser = {
      username: `reg_${id}`,
      email: `reg_${id}@example.com`,
      password: 'password123',
    };

    await page.goto('/register');
    await expect(page.locator('h1')).toHaveText('Register Patron Account');

    // Attempt registration with invalid short password (< 6 chars)
    await page.fill('input#username', testUser.username);
    await page.fill('input#email', testUser.email);
    await page.fill('input#password', '12345');
    await page.click('button[type="submit"]');

    // Verify validation error message appears
    await expect(page.locator('text=Password must be at least 6 characters')).toBeVisible();

    // Now fill valid password and register
    await page.fill('input#password', testUser.password);
    await page.click('button[type="submit"]');

    // Registration succeeds and redirects to events
    await page.waitForURL('**/events');
    await expect(page).toHaveURL(/\/events/);

    // Navbar should reflect authenticated username
    await expect(page.locator(`text=${testUser.username}`)).toBeVisible();
  });

  test('Login rejects invalid credentials and accepts valid credentials', async ({ page }) => {
    const id = Date.now() + '_' + Math.random().toString(36).substring(2, 6);
    const loginUser = {
      username: `login_${id}`,
      email: `login_${id}@example.com`,
      password: 'password123',
    };

    // First register the user so valid credentials exist
    await page.goto('/register');
    await page.fill('input#username', loginUser.username);
    await page.fill('input#email', loginUser.email);
    await page.fill('input#password', loginUser.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/events');

    // Log out so we can test the login form
    await page.click('button:has-text("Sign Out")');
    await page.goto('/login');
    await expect(page.locator('h1')).toHaveText('Sign In to SeatFlow');

    // Submit invalid credentials
    await page.fill('input#username', loginUser.username);
    await page.fill('input#password', 'wrongPassword123');
    await page.click('button[type="submit"]');

    // Error alert is visible
    await expect(page.locator('text=Invalid credentials provided')).toBeVisible();

    // Submit correct credentials
    await page.fill('input#password', loginUser.password);
    await page.click('button[type="submit"]');

    // Redirected to events
    await page.waitForURL('**/events');
    await expect(page).toHaveURL(/\/events/);
    await expect(page.locator(`text=${loginUser.username}`)).toBeVisible();
  });

  test('Protected routes redirect unauthenticated users to login', async ({ page }) => {
    // Ensure no token in localStorage
    await page.goto('/');
    await page.evaluate(() => {
      localStorage.clear();
    });

    // Try navigating to protected /bookings
    await page.goto('/bookings');
    await page.waitForURL('**/login');
    await expect(page).toHaveURL(/\/login/);

    // Try navigating to protected /checkout
    await page.goto('/checkout');
    await page.waitForURL('**/login');
    await expect(page).toHaveURL(/\/login/);
  });

  test('Authenticated user can access bookings and logout clears auth state', async ({ page }) => {
    const id = Date.now() + '_' + Math.random().toString(36).substring(2, 6);
    const user = {
      username: `bk_${id}`,
      email: `bk_${id}@example.com`,
      password: 'password123',
    };

    // Register user
    await page.goto('/register');
    await page.fill('input#username', user.username);
    await page.fill('input#email', user.email);
    await page.fill('input#password', user.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/events');

    // Navigate to /bookings
    await page.click('a[href="/bookings"]');
    await page.waitForURL('**/bookings');
    await expect(page.locator('h1')).toContainText('Reservation History');

    // Logout
    await page.click('button:has-text("Sign Out")');

    // Verify auth state is cleared from localStorage
    const storedToken = await page.evaluate(() => localStorage.getItem('ticketlock_token'));
    expect(storedToken).toBeNull();

    // Now visiting /bookings redirects to /login
    await page.goto('/bookings');
    await page.waitForURL('**/login');
    await expect(page).toHaveURL(/\/login/);
  });
});
