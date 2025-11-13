import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Credentials Interceptor
 *
 * Enables sending cookies with all HTTP requests by setting withCredentials: true
 * This is required for CSRF cookies to be sent to and received from the backend
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  // Clone request with withCredentials enabled
  const clonedRequest = req.clone({
    withCredentials: true
  });

  return next(clonedRequest);
};
