import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../config/api-base-url.token';
import { tap, catchError, of } from 'rxjs';

/**
 * Service to initialize CSRF token
 * Calls /api/csrf on app startup to trigger Spring Security token generation
 * Only runs in browser (not during SSR)
 */
@Injectable({ providedIn: 'root' })
export class CsrfService {
  private http = inject(HttpClient);
  private baseUrl = inject(API_BASE_URL);
  private platformId = inject(PLATFORM_ID);

  /**
   * Initialize CSRF protection by calling the backend endpoint
   * This triggers Spring Security to generate and set the XSRF-TOKEN cookie
   * Only executes in browser context (skipped during SSR)
   */
  initializeCsrfToken() {
    // Skip CSRF initialization during SSR
    if (!isPlatformBrowser(this.platformId)) {
      return of(null);
    }

    return this.http.get(`${this.baseUrl}/api/csrf`).pipe(
      tap(() => console.log('[CSRF] Token initialized')),
      catchError(error => {
        console.error('[CSRF] Failed to initialize token:', error);
        return of(null);
      })
    );
  }
}
