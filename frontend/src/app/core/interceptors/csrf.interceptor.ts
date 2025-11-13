import { HttpInterceptorFn } from '@angular/common/http';

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

  // Read CSRF token from cookie
  const csrfToken = getCookie('XSRF-TOKEN');

  console.log('[CSRF Interceptor] Method:', req.method, 'URL:', req.url);
  console.log('[CSRF Interceptor] All cookies:', document.cookie);
  console.log('[CSRF Interceptor] CSRF Token found:', csrfToken);

  // If token exists, add it to the request header
  if (csrfToken) {
    const clonedRequest = req.clone({
      setHeaders: {
        'X-XSRF-TOKEN': csrfToken
      }
    });
    console.log('[CSRF Interceptor] Added X-XSRF-TOKEN header');
    return next(clonedRequest);
  }

  console.warn('[CSRF Interceptor] No CSRF token found in cookies!');
  return next(req);
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
