// frontend/e2e/fixtures/mocks.fixture.ts
import { Page } from '@playwright/test';
import {
  CARES,
  AVAILABLE_SLOTS,
  SALON_CLIENTS,
  EMPLOYEES,
  PUBLIC_SALON,
} from '../test-data/fixtures';

function json(body: unknown, status = 200) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  };
}

export async function setupProBookingMocks(page: Page): Promise<void> {
  await page.route('**/api/cares*', r => r.fulfill(json(CARES)));
  await page.route('**/api/employees*', r => r.fulfill(json(EMPLOYEES)));
  await page.route(
    '**/api/pro/opening-hours/available-slots*',
    r => r.fulfill(json(AVAILABLE_SLOTS))
  );
  await page.route('**/api/salon-clients/recent*', r => r.fulfill(json(SALON_CLIENTS)));
  await page.route('**/api/salon-clients/search*', r => r.fulfill(json(SALON_CLIENTS)));
  await page.route('**/api/salon-clients', async route => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      await route.fulfill(json({ id: 99, ...body }, 201));
    } else {
      await route.continue();
    }
  });
  await page.route('**/api/bookings/detailed*', r =>
    r.fulfill(json({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
      first: true,
      last: true,
      empty: true,
    }))
  );
  await page.route('**/api/bookings', async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill(json({ id: 42 }, 201));
    } else {
      await route.continue();
    }
  });
  await page.route('**/api/notifications/unread/count', r => r.fulfill(json(0)));
  await page.route('**/api/notifications*', r => r.fulfill(json({
    content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
    first: true, last: true, empty: true,
  })));
}

export async function setupClientBookingMocks(page: Page): Promise<void> {
  await page.route(
    `**/api/salon/${PUBLIC_SALON.slug}`,
    r => r.fulfill(json(PUBLIC_SALON))
  );
  await page.route(
    `**/api/salon/${PUBLIC_SALON.slug}/available-slots*`,
    r => r.fulfill(json(AVAILABLE_SLOTS))
  );
  await page.route(
    `**/api/salon/${PUBLIC_SALON.slug}/employees*`,
    r => r.fulfill(json(EMPLOYEES))
  );
  await page.route(`**/api/salon/${PUBLIC_SALON.slug}/book`, async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill(json({ id: 77 }, 201));
    } else {
      await route.continue();
    }
  });
}
