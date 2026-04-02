import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { catchError, EMPTY, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { LeaveResponse, LeaveReviewDto } from './leaves.model';
import { LeavesService } from './leaves.service';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../shared/features/request.status.feature';

type LeavesState = {
  pendingLeaves: LeaveResponse[];
};

export const LeavesStore = signalStore(
  withState<LeavesState>({ pendingLeaves: [] }),
  withRequestStatus(),
  withMethods((store, service = inject(LeavesService)) => ({
    loadPending: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.listPending().pipe(
            tap((leaves) => patchState(store, { pendingLeaves: leaves }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Error loading leaves'));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    reviewLeave: rxMethod<{ leaveId: number; dto: LeaveReviewDto }>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap(({ leaveId, dto }) =>
          service.review(leaveId, dto).pipe(
            tap(() =>
              patchState(
                store,
                {
                  pendingLeaves: store.pendingLeaves().filter((l) => l.id !== leaveId),
                },
                setFulfilled(),
              ),
            ),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Error reviewing leave'));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.loadPending();
    },
  })),
);
