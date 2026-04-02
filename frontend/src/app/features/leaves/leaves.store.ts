import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { catchError, EMPTY, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { LeaveResponse, LeaveReviewDto, LeaveType } from './leaves.model';
import { LeavesService } from './leaves.service';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../shared/features/request.status.feature';

type LeavesState = {
  pendingLeaves: LeaveResponse[];
  historyLeaves: LeaveResponse[];
};

export const LeavesStore = signalStore(
  withState<LeavesState>({ pendingLeaves: [], historyLeaves: [] }),
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
    loadHistory: rxMethod<LeaveType | undefined>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap((type) =>
          service.listHistory(type).pipe(
            tap((leaves) => patchState(store, { historyLeaves: leaves }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Error loading history'));
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
