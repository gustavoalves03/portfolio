import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {Care, CareStatus, CreateCareRequest} from '../models/cares.model';
import {computed, inject} from '@angular/core';
import {CaresService} from '../services/cares.service';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {exhaustMap, finalize, pipe, switchMap, tap} from 'rxjs';
import {setFulfilled, setPending, withRequestStatus} from '../../../shared/features/request.status.feature';


type CaresState = {
  cares: Care[];
};


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
        switchMap(() => caresGateway.list()),
        tap(cares => patchState(store, { cares: cares.content }, setFulfilled())),
      )
    ),
    createCare: rxMethod<CreateCareRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((payload) => caresGateway.create(payload)),
        tap((newCare) => patchState(store, { cares: [...store.cares(), newCare] }, setFulfilled()))
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getCares();
    },
    onDestroy() {
    },
  }))
);
