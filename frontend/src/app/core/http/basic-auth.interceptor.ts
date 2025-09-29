import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from '../config/api-base-url.token';

function encodeBasic(user: string, pass: string): string {
  const raw = `${user}:${pass}`;
  // Browser has btoa; Node (SSR) uses Buffer
  const base64 = typeof btoa !== 'undefined'
    ? btoa(raw)
    : (globalThis as any).Buffer?.from(raw, 'utf-8')?.toString('base64');
  return `Basic ${base64}`;
}

// Dev credentials — keep only for local development
const DEV_USER = 'dev';
const DEV_PASS = 'dev';

export const basicAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const apiBaseUrl = inject(API_BASE_URL);

  // Decide whether to attach the header: only to our API
  const url = req.url;
  const targets: ((u: string) => boolean)[] = [];

  if (apiBaseUrl) {
    // Absolute base URL
    targets.push((u) => u.startsWith(apiBaseUrl));
    try {
      const origin = new URL(apiBaseUrl).origin;
      targets.push((u) => {
        try { return new URL(u).origin === origin; } catch { return false; }
      });
    } catch { /* ignore parse errors */ }
  }

  // Also match typical relative API paths in dev
  targets.push((u) => u.startsWith('/api/'));

  const shouldAttach = targets.some((t) => t(url));
  if (!shouldAttach) {
    return next(req);
  }

  const auth = encodeBasic(DEV_USER, DEV_PASS);
  const cloned = req.clone({ setHeaders: { Authorization: auth } });
  return next(cloned);
};

