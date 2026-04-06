import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../shared/features/request.status.feature';
import { CalendarService } from './calendar.service';
import { AvailabilityService } from '../availability/availability.service';
import { BlockedSlotRequest, BlockedSlotResponse } from './calendar.model';
import { OpeningHourResponse } from '../availability/availability.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

type CalendarState = {
  blockedSlots: BlockedSlotResponse[];
  openingHours: OpeningHourResponse[];
  holidays: { date: string; name: string }[];
};

export const CalendarStore = signalStore(
  withState<CalendarState>({ blockedSlots: [], openingHours: [], holidays: [] }),
  withRequestStatus(),
  withMethods(
    (
      store,
      calendarService = inject(CalendarService),
      availabilityService = inject(AvailabilityService),
      http = inject(HttpClient),
      apiBaseUrl = inject(API_BASE_URL)
    ) => ({
      loadBlockedSlots: rxMethod<void>(
        pipe(
          tap(() => patchState(store, setPending())),
          switchMap(() =>
            calendarService.loadBlockedSlots().pipe(
              tap((blockedSlots) => patchState(store, { blockedSlots }, setFulfilled())),
              catchError((err) => {
                patchState(store, setError(err?.message ?? 'Erreur de chargement'));
                return EMPTY;
              })
            )
          )
        )
      ),
      loadOpeningHours: rxMethod<void>(
        pipe(
          switchMap(() =>
            availabilityService.loadHours().pipe(
              tap((openingHours) => patchState(store, { openingHours })),
              catchError(() => EMPTY)
            )
          )
        )
      ),
      createBlock: rxMethod<BlockedSlotRequest>(
        pipe(
          tap(() => patchState(store, setPending())),
          exhaustMap((req) =>
            calendarService.createBlock(req).pipe(
              tap((created) =>
                patchState(
                  store,
                  { blockedSlots: [...store.blockedSlots(), created] },
                  setFulfilled()
                )
              ),
              catchError((err) => {
                patchState(
                  store,
                  setError(err?.error?.error ?? err?.message ?? 'Erreur lors du blocage')
                );
                return EMPTY;
              })
            )
          )
        )
      ),
      deleteBlock: rxMethod<number>(
        pipe(
          tap(() => patchState(store, setPending())),
          exhaustMap((id) =>
            calendarService.deleteBlock(id).pipe(
              tap(() =>
                patchState(
                  store,
                  { blockedSlots: store.blockedSlots().filter((s) => s.id !== id) },
                  setFulfilled()
                )
              ),
              catchError((err) => {
                patchState(
                  store,
                  setError(err?.message ?? 'Erreur lors du déblocage')
                );
                return EMPTY;
              })
            )
          )
        )
      ),
      loadHolidays: rxMethod<void>(
        pipe(
          switchMap(() =>
            http
              .get<{ date: string; name: string }[]>(
                `${apiBaseUrl}/api/pro/holidays/upcoming`
              )
              .pipe(
                tap((holidays) => patchState(store, { holidays })),
                catchError(() => EMPTY)
              )
          )
        )
      ),
    })
  ),
  withHooks((store) => ({
    onInit() {
      store.loadBlockedSlots();
      store.loadOpeningHours();
      store.loadHolidays();
    },
  }))
);
