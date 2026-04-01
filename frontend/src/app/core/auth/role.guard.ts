import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { Role } from './auth.model';

export const roleGuard = (requiredRole: Role): CanActivateFn => {
  return () => {
    const platformId = inject(PLATFORM_ID);

    // During SSR, always allow — the real check happens after client hydration
    if (!isPlatformBrowser(platformId)) {
      return of(true);
    }

    const authService = inject(AuthService);
    const router = inject(Router);

    return authService.checkAuthentication().pipe(
      map((authenticated) => {
        if (!authenticated) {
          return router.createUrlTree(['/login']);
        }
        const user = authService.user();
        if (user?.role === requiredRole || user?.role === Role.ADMIN) {
          return true;
        }
        return router.createUrlTree(['/']);
      })
    );
  };
};
