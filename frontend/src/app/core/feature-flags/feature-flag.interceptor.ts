import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { catchError, throwError } from 'rxjs';

export const featureFlagInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const snack = inject(MatSnackBar);
  const t = inject(TranslocoService);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (
        err instanceof HttpErrorResponse &&
        err.status === 403 &&
        err.error?.error === 'FEATURE_DISABLED'
      ) {
        const featureKey = err.error.featureKey as string;
        const minimumTier = err.error.minimumTier as string;
        snack.open(t.translate('errors.features.disabled', { tier: minimumTier }), 'OK', { duration: 4000 });
        router.navigate(['/pricing'], { queryParams: { highlight: featureKey } });
      }
      return throwError(() => err);
    }),
  );
};
