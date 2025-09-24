import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { Care, CareStatus, CreateCareRequest } from '../models/cares.model';
import { computed, inject } from '@angular/core';
import { CaresService } from '../services/cares.service';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { exhaustMap, finalize, pipe, switchMap, tap } from 'rxjs';


type CaresState = {
  cares: Care[];
  loading: boolean;
  error: string | null;
};


export const CaresStore = signalStore(
  withState<CaresState>({ cares: [], loading: false, error: null }),
  withComputed((store) => ({
    availableCares: computed(() =>
      store
        .cares()
        .filter((care) => care.status === CareStatus.ACTIVE)
    ),
  })),
  withMethods((store, caresGateway = inject(CaresService)) => ({
    getCares: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap(() => caresGateway.list({ page: 0, size: 20 })),
        tap({
          next: (page) => patchState(store, { cares: page.content }),
          error: (err) => patchState(store, { error: err?.message ?? 'Erreur de chargement' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
    createCare: rxMethod<CreateCareRequest>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        exhaustMap((payload) => caresGateway.create(payload)),
        tap({
          next: (newCare) => patchState(store, { cares: [...store.cares(), newCare] }),
          error: (err) => patchState(store, { error: err?.message ?? 'Erreur lors de la création' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getCares();
    },
    onDestroy() {},
  }))
);
