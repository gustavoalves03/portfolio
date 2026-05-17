import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './auth.model';

/**
 * Blocks unverified PRO users from reaching `/pro/**` and redirects them to
 * `/verify-email-required`. Must be placed AFTER `authGuard` so the user is
 * loaded by the time we read `auth.user()`.
 */
export const proEmailVerifiedGuard: CanActivateFn = () => {
  const platformId = inject(PLATFORM_ID);

  // During SSR, always allow — the real check happens after client hydration
  if (!isPlatformBrowser(platformId)) {
    return true;
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.user();

  if (user?.roles?.includes(Role.PRO) && !user.emailVerified) {
    router.navigate(['/verify-email-required']);
    return false;
  }
  return true;
};
