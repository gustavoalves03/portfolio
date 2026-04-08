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
  pastLoaded: boolean;
  pastPending: boolean;
  cancellingId: number | null;
};

export const ClientBookingsStore = signalStore(
  withState<ClientBookingsState>({
    upcoming: [],
    past: [],
    pastLoaded: false,
    pastPending: false,
    cancellingId: null,
  }),
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
        tap(() => patchState(store, { pastPending: true })),
        switchMap(() => service.getMyBookings('past')),
        tap({
          next: (past) => patchState(store, { past, pastLoaded: true, pastPending: false }),
          error: (err) => patchState(store, { pastPending: false }, setError(err?.message ?? 'Error loading bookings')),
        })
      )
    ),
    cancelBooking: rxMethod<{ slug: string; bookingId: number }>(
      pipe(
        tap(({ bookingId }) => patchState(store, { cancellingId: bookingId })),
        switchMap(({ slug, bookingId }) =>
          service.cancelBooking(slug, bookingId).pipe(
            tap({
              next: () => {
                // Remove from upcoming, add to past is not needed since we reload
                const updated = store.upcoming().filter((b) => b.bookingId !== bookingId);
                patchState(store, { upcoming: updated, cancellingId: null });
              },
              error: () => patchState(store, { cancellingId: null }),
            })
          )
        )
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.loadUpcoming();
    },
  }))
);
