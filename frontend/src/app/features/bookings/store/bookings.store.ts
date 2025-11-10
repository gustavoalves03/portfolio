import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { BookingsService } from '../services/bookings.service';
import { CareBooking, CreateCareBookingRequest, UpdateCareBookingRequest } from '../models/bookings.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, map, pipe, switchMap, tap } from 'rxjs';
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
    updateBooking: rxMethod<{ id: number; payload: UpdateCareBookingRequest }>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap(({ id, payload }) =>
          gateway.update(id, payload).pipe(
            tap((updatedBooking) =>
              patchState(
                store,
                {
                  bookings: store.bookings().map((booking) => (booking.id === id ? updatedBooking : booking))
                },
                setFulfilled()
              )
            ),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Erreur lors de la modification de la réservation'));
              return EMPTY;
            })
          )
        )
      )
    ),
    deleteBooking: rxMethod<number>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((id) =>
          gateway.delete(id).pipe(
            tap(() =>
              patchState(
                store,
                {
                  bookings: store.bookings().filter((booking) => booking.id !== id)
                },
                setFulfilled()
              )
            ),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Erreur lors de la suppression de la réservation'));
              return EMPTY;
            })
          )
        )
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getBookings();
    },
  }))
);
