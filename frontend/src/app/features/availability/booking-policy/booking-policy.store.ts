import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, pipe, switchMap, tap } from 'rxjs';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../../shared/features/request.status.feature';
import { BookingPolicy, UpdateBookingPolicyRequest } from './booking-policy.model';
import { BookingPolicyService } from './booking-policy.service';

interface State {
  policy: BookingPolicy | null;
}

export const BookingPolicyStore = signalStore(
  withState<State>({ policy: null }),
  withRequestStatus(),
  withMethods((store, service = inject(BookingPolicyService)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.getCurrent().pipe(
            tap((policy) => patchState(store, { policy }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(String(err)));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    update: rxMethod<UpdateBookingPolicyRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap((req) =>
          service.update(req).pipe(
            tap((policy) => patchState(store, { policy }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(String(err)));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
  })),
);
