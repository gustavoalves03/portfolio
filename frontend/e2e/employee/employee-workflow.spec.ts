import { test, expect } from '@playwright/test';
import { loginAsEmployee } from '../fixtures/auth.fixture';

function json(body: unknown, status = 200) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  };
}

test.describe('Employee workflow', () => {
  test('E1 — employee lands on bookings page and navigates to leaves', async ({ page }) => {
    await loginAsEmployee(page);

    // Minimal mocks to keep the shell happy
    await page.route('**/api/notifications/unread/count', r => r.fulfill(json(0)));
    await page.route('**/api/notifications*', r =>
      r.fulfill(
        json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
          first: true,
          last: true,
          empty: true,
        })
      )
    );

    // Mocks used by employee-leaves landing
    await page.route('**/api/employee/me', r =>
      r.fulfill(
        json({
          id: 500,
          name: 'Employee Test',
          role: 'EMPLOYEE',
          assignedCares: [],
        })
      )
    );
    await page.route('**/api/employee/me/settings', r =>
      r.fulfill(json({ annualLeaveDays: 25 }))
    );
    await page.route('**/api/employee/me/leaves*', r => r.fulfill(json([])));

    // Landing on /employee redirects to /employee/bookings
    await page.goto('/employee/bookings');

    // Landing page renders
    await expect(page.getByTestId('employee-bookings-page')).toBeVisible();
    await expect(page.getByTestId('employee-bookings-empty')).toBeVisible();

    // Action: navigate to /employee/leaves (sibling route)
    await page.goto('/employee/leaves');

    // Leaves page title should be visible (uses transloco key employee.leaves.title)
    await expect(
      page.getByRole('heading', { name: /mes congés|my leaves/i })
    ).toBeVisible();
  });
});
