import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { TenantResponse, UpdateTenantRequest } from '../models/salon-profile.model';
import { SalonProfileService } from '../services/salon-profile.service';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';

type SalonProfileState = {
  tenant: TenantResponse | null;
  saveSuccess: boolean;
  saveError: boolean;
};

export const SalonProfileStore = signalStore(
  withState<SalonProfileState>({ tenant: null, saveSuccess: false, saveError: false }),
  withRequestStatus(),
  withMethods((store, service = inject(SalonProfileService)) => ({
    loadProfile: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.getProfile().pipe(
            tap(tenant => patchState(store, { tenant }, setFulfilled())),
            catchError(() => {
              // Load failures stay silent: the page may simply have nothing
              // to show yet. Only save failures surface a snackbar.
              patchState(store, setFulfilled());
              return EMPTY;
            })
          )
        )
      )
    ),
    updateProfile: rxMethod<UpdateTenantRequest>(
      pipe(
        tap(() => patchState(store, { saveSuccess: false, saveError: false }, setPending())),
        exhaustMap(request =>
          service.updateProfile(request).pipe(
            tap(tenant => patchState(store, { tenant, saveSuccess: true, saveError: false }, setFulfilled())),
            catchError(() => {
              patchState(store, { saveError: true }, setError('Erreur lors de la sauvegarde'));
              return EMPTY;
            })
          )
        )
      )
    ),
    clearStatus() {
      patchState(store, { saveSuccess: false, saveError: false }, setFulfilled());
    }
  })),
  withHooks((store) => ({
    onInit() {
      store.loadProfile();
    }
  }))
);
