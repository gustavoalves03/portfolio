import { computed, inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { FeatureFlagsService } from './feature-flags.service';
import { ALL_FEATURE_KEYS, FeatureFlagSnapshot, FeatureKey } from './feature-key';

const emptyFlags = (): FeatureFlagSnapshot =>
  ALL_FEATURE_KEYS.reduce((acc, k) => ({ ...acc, [k]: false }), {} as FeatureFlagSnapshot);

export const FeatureFlagsStore = signalStore(
  { providedIn: 'root' },
  withState({ flags: emptyFlags() as FeatureFlagSnapshot, loaded: false }),
  withMethods((store, svc = inject(FeatureFlagsService)) => ({
    load: rxMethod<void>(
      pipe(
        switchMap(() => svc.fetch()),
        tap((flags) => patchState(store, { flags, loaded: true })),
      ),
    ),
    reset: () => patchState(store, { flags: emptyFlags(), loaded: false }),
    isEnabled: (key: FeatureKey) => computed(() => store.flags()[key] === true),
  })),
);
