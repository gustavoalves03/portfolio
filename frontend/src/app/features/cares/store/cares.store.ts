import { HttpErrorResponse } from '@angular/common/http';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { computed, inject } from '@angular/core';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { Care, CareStatus, CreateCareRequest, UpdateCareRequest } from '../models/cares.model';
import { CaresService } from '../services/cares.service';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';


type CaresState = {
  cares: Care[];
};


function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      const detail =
        (typeof error.error === 'string' && error.error) ||
        error.error?.detail ||
        error.error?.message;
      return detail
        ? `Accès interdit : ${detail}`
        : 'Accès interdit : vos droits ne permettent pas cette action. Contactez votre administrateur.';
    }
    if (typeof error.error === 'string') {
      return error.error;
    }
    if (error.error?.detail) {
      return error.error.detail;
    }
    if (error.error?.message) {
      return error.error.message;
    }
    return error.message ?? fallback;
  }

  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message;
  }

  return fallback;
}

export const CaresStore = signalStore(
  withState<CaresState>({cares: []}),
  withRequestStatus(),
  withComputed((store) => ({
    availableCares: computed(() => store.cares().filter((care) => care.status === CareStatus.ACTIVE)
    ),
  })),
  withMethods((store, caresGateway = inject(CaresService)) => ({
    getCares: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          caresGateway.list().pipe(
            tap((cares) => patchState(store, { cares: cares.content }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Erreur de chargement des soins')));
              return EMPTY;
            })
          )
        )
      )
    ),
    getCare: rxMethod<number>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap((id) =>
          caresGateway.get(id).pipe(
            tap((care) => {
              // Update the care in the list if it exists, otherwise add it
              const existingIndex = store.cares().findIndex(c => c.id === id);
              if (existingIndex !== -1) {
                patchState(store, {
                  cares: store.cares().map(c => c.id === id ? care : c)
                }, setFulfilled());
              } else {
                patchState(store, {
                  cares: [...store.cares(), care]
                }, setFulfilled());
              }
            }),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Erreur de chargement du soin')));
              return EMPTY;
            })
          )
        )
      )
    ),
    createCare: rxMethod<CreateCareRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((payload) =>
          caresGateway.create(payload).pipe(
            tap((newCare) =>
              patchState(store, { cares: [...store.cares(), newCare] }, setFulfilled())
            ),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Erreur lors de la création du soin')));
              return EMPTY;
            })
          )
        )
      )
    ),
    updateCare: rxMethod<{ id: number; payload: UpdateCareRequest }>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap(({ id, payload }) =>
          caresGateway.update(id, payload).pipe(
            tap((updatedCare) =>
              patchState(
                store,
                {
                  cares: store.cares().map((care) => (care.id === id ? updatedCare : care))
                },
                setFulfilled()
              )
            ),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Erreur lors de la modification du soin')));
              return EMPTY;
            })
          )
        )
      )
    ),
    deleteCare: rxMethod<number>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((id) =>
          caresGateway.delete(id).pipe(
            tap(() =>
              patchState(
                store,
                {
                  cares: store.cares().filter((care) => care.id !== id)
                },
                setFulfilled()
              )
            ),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Erreur lors de la suppression du soin')));
              return EMPTY;
            })
          )
        )
      )
    ),
    clearError() {
      patchState(store, setFulfilled());
    },
  })),
  withHooks((store) => ({
    onInit() {
      store.getCares();
    },
    onDestroy() {
    },
  }))
);
