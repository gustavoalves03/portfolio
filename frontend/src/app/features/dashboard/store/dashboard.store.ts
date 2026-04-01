import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { TenantReadiness } from '../models/dashboard.model';
import { CareBookingDetailed } from '../../bookings/models/bookings.model';
import { DashboardService } from '../services/dashboard.service';
import { BookingsService } from '../../bookings/services/bookings.service';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../../shared/features/request.status.feature';

type DashboardState = {
  readiness: TenantReadiness | null;
  recentBookings: CareBookingDetailed[];
  todayCount: number;
  weekCount: number;
  publishSuccess: boolean;
  unpublishSuccess: boolean;
};

export const DashboardStore = signalStore(
  withState<DashboardState>({
    readiness: null,
    recentBookings: [],
    todayCount: 0,
    weekCount: 0,
    publishSuccess: false,
    unpublishSuccess: false,
  }),
  withRequestStatus(),
  withComputed((store) => ({
    isActive: computed(() => store.readiness()?.status === 'ACTIVE'),
    isDraft: computed(() => store.readiness()?.status === 'DRAFT'),
    canPublish: computed(() => store.readiness()?.canPublish ?? false),
  })),
  withMethods(
    (
      store,
      dashboardService = inject(DashboardService),
      bookingsService = inject(BookingsService)
    ) => ({
      loadReadiness: rxMethod<void>(
        pipe(
          tap(() => patchState(store, setPending())),
          switchMap(() =>
            dashboardService.getReadiness().pipe(
              tap((readiness) => patchState(store, { readiness }, setFulfilled())),
              catchError(() => {
                patchState(store, setError('Erreur de chargement'));
                return EMPTY;
              })
            )
          )
        )
      ),
      loadActivity: rxMethod<void>(
        pipe(
          switchMap(() => {
            const now = new Date();
            const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
            const dayOfWeek = now.getDay() === 0 ? 7 : now.getDay();
            const weekStart = new Date(todayStart);
            weekStart.setDate(weekStart.getDate() - (dayOfWeek - 1));
            const weekEnd = new Date(weekStart);
            weekEnd.setDate(weekEnd.getDate() + 7);

            return bookingsService
              .listDetailed(
                { from: weekStart.toISOString(), status: undefined },
                { page: 0, size: 5, sort: 'appointmentDate,asc' }
              )
              .pipe(
                tap((page) => {
                  const todayStr = todayStart.toISOString().split('T')[0];
                  const todayCount = page.content.filter(
                    (b) => b.appointmentDate === todayStr
                  ).length;
                  const weekCount = page.content.length;
                  patchState(store, {
                    recentBookings: page.content,
                    todayCount,
                    weekCount,
                  });
                }),
                catchError(() => EMPTY)
              );
          })
        )
      ),
      publish: rxMethod<void>(
        pipe(
          tap(() => patchState(store, { publishSuccess: false }, setPending())),
          exhaustMap(() =>
            dashboardService.publish().pipe(
              switchMap(() => dashboardService.getReadiness()),
              tap((readiness) => {
                patchState(store, { readiness, publishSuccess: true }, setFulfilled());
              }),
              catchError(() => {
                patchState(store, setError('Erreur lors de la publication'));
                return EMPTY;
              })
            )
          )
        )
      ),
      unpublish: rxMethod<void>(
        pipe(
          tap(() => patchState(store, { unpublishSuccess: false }, setPending())),
          exhaustMap(() =>
            dashboardService.unpublish().pipe(
              switchMap(() => dashboardService.getReadiness()),
              tap((readiness) => {
                patchState(store, { readiness, unpublishSuccess: true }, setFulfilled());
              }),
              catchError(() => {
                patchState(store, setError('Erreur lors de la dépublication'));
                return EMPTY;
              })
            )
          )
        )
      ),
    })
  ),
  withHooks((store) => ({
    onInit() {
      store.loadReadiness();
    },
  }))
);
