// frontend/e2e/fixtures/auth.fixture.ts
import { Page } from '@playwright/test';

export async function loginAsPro(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('auth_token', 'fake-pro-token');
  });
  await page.route('**/api/auth/me', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        email: 'pro@test.fr',
        name: 'Pro Test',
        role: 'PRO',
        provider: 'LOCAL',
        tenantSlug: 'beaute-du-regard',
      }),
    })
  );
}

export async function loginAsClient(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('auth_token', 'fake-client-token');
  });
  await page.route('**/api/auth/me', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 100,
        email: 'client@test.fr',
        name: 'Client Test',
        role: 'USER',
        provider: 'LOCAL',
      }),
    })
  );
}

export async function loginAsEmployee(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('auth_token', 'fake-employee-token');
  });
  await page.route('**/api/auth/me', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 500,
        email: 'employee@test.fr',
        name: 'Employee Test',
        role: 'EMPLOYEE',
        provider: 'LOCAL',
        tenantSlug: 'beaute-du-regard',
      }),
    })
  );
}

export async function loggedOut(page: Page): Promise<void> {
  await page.route('**/api/auth/me', route => route.fulfill({ status: 401 }));
}
