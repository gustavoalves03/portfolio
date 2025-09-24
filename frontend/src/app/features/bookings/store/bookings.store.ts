import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { BookingsService } from '../services/bookings.service';
import { CareBooking, CreateCareBookingRequest } from '../models/bookings.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';

type BookingsState = {
  bookings: CareBooking[];
};

export const BookingsStore = signalStore(
  withState<BookingsState>({ bookings: [] }),
  withRequestStatus(),
  withMethods((store, gateway = inject(BookingsService)) => ({
    getBookings: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => gateway.list({ page: 0, size: 50 })),
        tap({
          next: (page) => patchState(store, { bookings: page.content }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Erreur de chargement')),
        })
      )
    ),
    createBooking: rxMethod<CreateCareBookingRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((payload) => gateway.create(payload)),
        tap({
          next: (created) => patchState(store, { bookings: [...store.bookings(), created] }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Erreur à la création')),
        })
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getBookings();
    },
  }))
);
