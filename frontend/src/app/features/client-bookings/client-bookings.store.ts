import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../shared/features/request.status.feature';
import { ClientBookingsService } from './client-bookings.service';
import { ClientBookingHistoryResponse } from './client-bookings.model';

type ClientBookingsState = {
  upcoming: ClientBookingHistoryResponse[];
  past: ClientBookingHistoryResponse[];
};

export const ClientBookingsStore = signalStore(
  withState<ClientBookingsState>({ upcoming: [], past: [] }),
  withRequestStatus(),
  withMethods((store, service = inject(ClientBookingsService)) => ({
    loadUpcoming: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => service.getMyBookings('upcoming')),
        tap({
          next: (upcoming) => patchState(store, { upcoming }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Error loading bookings')),
        })
      )
    ),
    loadPast: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => service.getMyBookings('past')),
        tap({
          next: (past) => patchState(store, { past }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Error loading bookings')),
        })
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.loadUpcoming();
    },
  }))
);
