import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { BookingsService } from '../services/bookings.service';
import { CareBooking, CreateCareBookingRequest } from '../models/bookings.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { exhaustMap, finalize, pipe, switchMap, tap } from 'rxjs';

type BookingsState = {
  bookings: CareBooking[];
  loading: boolean;
  error: string | null;
};

export const BookingsStore = signalStore(
  withState<BookingsState>({ bookings: [], loading: false, error: null }),
  withMethods((store, gateway = inject(BookingsService)) => ({
    getBookings: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap(() => gateway.list({ page: 0, size: 50 })),
        tap({
          next: (page) => patchState(store, { bookings: page.content }),
          error: (err) => patchState(store, { error: err?.message ?? 'Erreur de chargement' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
    createBooking: rxMethod<CreateCareBookingRequest>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        exhaustMap((payload) => gateway.create(payload)),
        tap({
          next: (created) => patchState(store, { bookings: [...store.bookings(), created] }),
          error: (err) => patchState(store, { error: err?.message ?? 'Erreur à la création' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getBookings();
    },
  }))
);
