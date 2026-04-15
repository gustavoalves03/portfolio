import { computed, inject } from '@angular/core';
import {
  patchState,
  signalStore,
  withComputed,
  withMethods,
  withState,
} from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, pipe, switchMap, tap } from 'rxjs';
import { BookingsService } from '../../../bookings/services/bookings.service';
import {
  CareBookingDetailed,
  CareBookingStatus,
} from '../../../bookings/models/bookings.model';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../../../shared/features/request.status.feature';

type ActiveTab = 'upcoming' | 'past';

type ClientBookingsState = {
  bookings: CareBookingDetailed[];
  activeTab: ActiveTab;
};

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

export const ClientBookingsStore = signalStore(
  withState<ClientBookingsState>({
    bookings: [],
    activeTab: 'upcoming',
  }),
  withRequestStatus(),
  withComputed((store) => {
    const today = todayStr();

    const todayBookings = computed(() =>
      store
        .bookings()
        .filter((b) => b.appointmentDate === today)
        .sort((a, b) => a.appointmentTime.localeCompare(b.appointmentTime))
    );

    const upcomingBookings = computed(() =>
      store
        .bookings()
        .filter((b) => b.appointmentDate > today)
        .sort(
          (a, b) =>
            a.appointmentDate.localeCompare(b.appointmentDate) ||
            a.appointmentTime.localeCompare(b.appointmentTime)
        )
    );

    const pastBookings = computed(() =>
      store
        .bookings()
        .filter((b) => b.appointmentDate < today)
        .sort(
          (a, b) =>
            b.appointmentDate.localeCompare(a.appointmentDate) ||
            b.appointmentTime.localeCompare(a.appointmentTime)
        )
    );

    return {
      todayBookings,
      upcomingBookings,
      pastBookings,
      upcomingCount: computed(
        () => todayBookings().length + upcomingBookings().length
      ),
      pastCount: computed(() => pastBookings().length),
    };
  }),
  withMethods(
    (store, bookingsService = inject(BookingsService)) => ({
      loadBookings: rxMethod<number>(
        pipe(
          tap(() => patchState(store, setPending())),
          switchMap((userId) =>
            bookingsService
              .listDetailed({ userId }, { size: 100, sort: 'appointmentDate,desc' })
              .pipe(
                tap((page) =>
                  patchState(store, { bookings: page.content }, setFulfilled())
                ),
                catchError(() => {
                  patchState(store, setError('Error loading bookings'));
                  return EMPTY;
                })
              )
          )
        )
      ),

      markNoShow: rxMethod<CareBookingDetailed>(
        pipe(
          tap((booking) =>
            patchState(store, {
              bookings: store.bookings().map((b) =>
                b.id === booking.id
                  ? { ...b, status: CareBookingStatus.NO_SHOW }
                  : b
              ),
            })
          ),
          switchMap((booking) =>
            bookingsService
              .update(booking.id, {
                userId: booking.user.id,
                careId: booking.care.id,
                quantity: booking.quantity,
                appointmentDate: booking.appointmentDate,
                appointmentTime: booking.appointmentTime,
                status: CareBookingStatus.NO_SHOW,
                salonClientId: booking.salonClientId ?? undefined,
              })
              .pipe(
                tap(() => patchState(store, setFulfilled())),
                catchError(() => {
                  patchState(store, {
                    bookings: store.bookings().map((b) =>
                      b.id === booking.id
                        ? { ...b, status: CareBookingStatus.CONFIRMED }
                        : b
                    ),
                  }, setError('Error marking no-show'));
                  return EMPTY;
                })
              )
          )
        )
      ),

      setActiveTab(tab: ActiveTab): void {
        patchState(store, { activeTab: tab });
      },
    })
  )
);
