import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { switchMap } from 'rxjs';
import { CsrfService } from '../security/csrf.service';

/**
 * CSRF Interceptor for Spring Security
 *
 * Reads the XSRF-TOKEN cookie set by Spring Security and adds it
 * as the X-XSRF-TOKEN header to all mutating requests (POST, PUT, DELETE, PATCH).
 *
 * Spring Security expects:
 * - Cookie: XSRF-TOKEN=abc123 (set automatically by backend)
 * - Header: X-XSRF-TOKEN: abc123 (added by this interceptor)
 */
export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  // Only add CSRF token for mutating requests (not GET/HEAD/OPTIONS)
  const methodsRequiringCsrf = ['POST', 'PUT', 'DELETE', 'PATCH'];

  if (!methodsRequiringCsrf.includes(req.method)) {
    return next(req);
  }

  // During SSR there is no document/cookie, skip gracefully
  if (typeof document === 'undefined') {
    return next(req);
  }

  const csrfService = inject(CsrfService);

  // Read CSRF token from cookie
  const csrfToken = getCookie('XSRF-TOKEN');

  // If token exists, add it to the request header
  if (csrfToken) {
    const clonedRequest = req.clone({
      setHeaders: {
        'X-XSRF-TOKEN': csrfToken
      }
    });
    return next(clonedRequest);
  }

  // Token missing (first call or cookie expired): fetch a fresh one, then retry with header
  return csrfService.initializeCsrfToken().pipe(
    switchMap(() => {
      const refreshedToken = getCookie('XSRF-TOKEN');
      if (refreshedToken) {
        const retriedRequest = req.clone({
          setHeaders: {
            'X-XSRF-TOKEN': refreshedToken
          }
        });
        return next(retriedRequest);
      }
      return next(req);
    })
  );
};

/**
 * Helper function to read a cookie by name
 */
function getCookie(name: string): string | null {
  const matches = document.cookie.match(
    new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()[\]\\/+^])/g, '\\$1') + '=([^;]*)')
  );
  return matches ? decodeURIComponent(matches[1]) : null;
}
