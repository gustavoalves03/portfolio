import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';
import { Role } from './auth.model';

export const roleGuard = (requiredRole: Role): CanActivateFn => {
  return () => {
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
