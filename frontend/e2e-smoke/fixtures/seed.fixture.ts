// frontend/e2e-smoke/fixtures/seed.fixture.ts
//
// Shared helpers for the full-stack smoke suite: talk directly to the real
// Spring Boot backend (profile=smoke-test) to prepare + clean state between
// tests, then inject JWTs into localStorage so the Angular app boots
// authenticated.

import { APIRequestContext, Page, request } from '@playwright/test';

export const BACKEND_BASE_URL = 'http://localhost:8080';

/**
 * Build an APIRequestContext that has already performed a GET /api/csrf so the
 * XSRF-TOKEN cookie is in its jar. Any subsequent POST will add the token as
 * the `X-XSRF-TOKEN` header because we mirror what the Angular CSRF
 * interceptor does.
 */
export async function apiContextWithCsrf(
  token?: string
): Promise<{ ctx: APIRequestContext; csrfToken: string }> {
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const ctx = await request.newContext({
    baseURL: BACKEND_BASE_URL,
    extraHTTPHeaders: headers,
  });
  const csrfRes = await ctx.get('/api/csrf');
  if (!csrfRes.ok()) {
    throw new Error(`GET /api/csrf failed: ${csrfRes.status()}`);
  }
  const csrfBody = (await csrfRes.json()) as { token: string };
  return { ctx, csrfToken: csrfBody.token };
}

/**
 * POST helper that transparently threads the CSRF token as the
 * X-XSRF-TOKEN header, matching what the Angular csrfInterceptor sends.
 */
export async function postWithCsrf(
  ctx: APIRequestContext,
  csrfToken: string,
  path: string,
  body?: unknown
): Promise<ReturnType<APIRequestContext['post']>> {
  return ctx.post(path, {
    data: body,
    headers: { 'X-XSRF-TOKEN': csrfToken },
  });
}

export interface SmokeSeed {
  salonSlug: string;
  salonName: string;
  careId: number;
  proUserId: number;
  proEmail: string;
  proToken: string;
  clientUserId: number;
  clientEmail: string;
  clientName: string;
  clientToken: string;
  /** ISO YYYY-MM-DD — a weekday in the near future, safe to book. */
  suggestedDate: string;
  /** "HH:mm" — a slot that respects the seeded opening hours (09:00–19:00). */
  suggestedTime: string;
}

/**
 * Idempotent seed call. Fails loudly if the backend is not up or the
 * smoke-test profile is not active (the seed controller is @Profile-gated).
 */
export async function seed(apiOrContext?: APIRequestContext): Promise<SmokeSeed> {
  const ctx = apiOrContext ?? (await request.newContext({ baseURL: BACKEND_BASE_URL }));
  const res = await ctx.post(`${BACKEND_BASE_URL}/api/test/seed`);
  if (!res.ok()) {
    throw new Error(
      `seed endpoint failed: ${res.status()} ${res.statusText()} — ` +
        `is the backend running with --spring-boot.run.profiles=smoke-test?`
    );
  }
  return (await res.json()) as SmokeSeed;
}

/** Wipes bookings but keeps the tenant / users / cares — cheap between tests. */
export async function reset(apiOrContext?: APIRequestContext): Promise<void> {
  const ctx = apiOrContext ?? (await request.newContext({ baseURL: BACKEND_BASE_URL }));
  const res = await ctx.post(`${BACKEND_BASE_URL}/api/test/reset`);
  if (!res.ok() && res.status() !== 204) {
    throw new Error(`reset endpoint failed: ${res.status()} ${res.statusText()}`);
  }
}

/**
 * Inject a JWT into the localStorage of every page document opened after this
 * call — mirrors what AuthService does on real login.
 */
export async function injectToken(page: Page, token: string): Promise<void> {
  await page.addInitScript((t: string) => {
    localStorage.setItem('auth_token', t);
  }, token);
}

/** Build the Angular Material datepicker aria-label for a given Date. */
export function materialDayAriaLabel(date: Date): string {
  const months = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ];
  return `${months[date.getMonth()]} ${date.getDate()}, ${date.getFullYear()}`;
}

export function parseIsoDate(iso: string): Date {
  // "YYYY-MM-DD" parsed as local (no time zone drift for datepicker).
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}
