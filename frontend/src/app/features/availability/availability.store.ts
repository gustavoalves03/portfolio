import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../shared/features/request.status.feature';
import { AvailabilityService } from './availability.service';
import { OpeningHourRequest, OpeningHourResponse } from './availability.model';

type AvailabilityState = {
  hours: OpeningHourResponse[];
};

export const AvailabilityStore = signalStore(
  withState<AvailabilityState>({ hours: [] }),
  withRequestStatus(),
  withMethods((store, service = inject(AvailabilityService)) => ({
    loadHours: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.loadHours().pipe(
            tap((hours) => patchState(store, { hours }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Erreur de chargement'));
              return EMPTY;
            })
          )
        )
      )
    ),
    saveHours: rxMethod<OpeningHourRequest[]>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((requests) =>
          service.saveHours(requests).pipe(
            tap((hours) => patchState(store, { hours }, setFulfilled())),
            catchError((err) => {
              patchState(
                store,
                setError(err?.error?.error ?? err?.message ?? 'Erreur de sauvegarde')
              );
              return EMPTY;
            })
          )
        )
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.loadHours();
    },
  }))
);
