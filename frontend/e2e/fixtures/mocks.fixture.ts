// frontend/e2e/fixtures/mocks.fixture.ts
import { Page } from '@playwright/test';
import {
  CARES,
  AVAILABLE_SLOTS,
  SLOTS_BY_CARE,
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
  // Cares endpoints (listOrdered -> /api/care/ordered, also /api/care with pagination)
  await page.route('**/api/care/ordered*', r => r.fulfill(json(CARES)));
  await page.route('**/api/care?*', r => r.fulfill(json({
    content: CARES, totalElements: CARES.length, totalPages: 1,
    number: 0, size: 20, first: true, last: true, empty: false,
  })));
  await page.route('**/api/care', r => r.fulfill(json({
    content: CARES, totalElements: CARES.length, totalPages: 1,
    number: 0, size: 20, first: true, last: true, empty: false,
  })));
  await page.route('**/api/employees*', r => r.fulfill(json(EMPLOYEES)));
  await page.route(
    '**/api/pro/opening-hours/available-slots*',
    r => r.fulfill(json(AVAILABLE_SLOTS))
  );
  // Salon client endpoints (pro namespace)
  await page.route('**/api/pro/clients/recent*', r => r.fulfill(json(SALON_CLIENTS)));
  await page.route('**/api/pro/clients/search*', r => r.fulfill(json(SALON_CLIENTS)));
  await page.route('**/api/pro/clients', async route => {
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
    `**/api/salon/${PUBLIC_SALON.slug}/slots/by-care*`,
    r => r.fulfill(json(SLOTS_BY_CARE))
  );
  await page.route(
    `**/api/salon/${PUBLIC_SALON.slug}/employees*`,
    r => r.fulfill(json(EMPLOYEES))
  );
  await page.route(
    `**/api/salon/${PUBLIC_SALON.slug}/opening-hours*`,
    r => r.fulfill(json([
      { id: 1, dayOfWeek: 1, openTime: '09:00', closeTime: '18:00' },
      { id: 2, dayOfWeek: 2, openTime: '09:00', closeTime: '18:00' },
      { id: 3, dayOfWeek: 3, openTime: '09:00', closeTime: '18:00' },
      { id: 4, dayOfWeek: 4, openTime: '09:00', closeTime: '18:00' },
      { id: 5, dayOfWeek: 5, openTime: '09:00', closeTime: '18:00' },
      { id: 6, dayOfWeek: 6, openTime: '09:00', closeTime: '18:00' },
      { id: 7, dayOfWeek: 7, openTime: '09:00', closeTime: '18:00' },
    ]))
  );
  await page.route(`**/api/salon/${PUBLIC_SALON.slug}/book`, async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill(json({ id: 77 }, 201));
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
