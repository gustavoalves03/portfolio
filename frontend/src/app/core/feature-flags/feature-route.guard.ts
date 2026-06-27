import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { FeatureFlagsStore } from './feature-flags.store';
import { FeatureKey } from './feature-key';

export const requireFeature = (key: FeatureKey): CanActivateFn => () => {
  const store = inject(FeatureFlagsStore);
  const router = inject(Router);
  if (store.isEnabled(key)()) return true;
  router.navigate(['/pricing'], { queryParams: { highlight: key } });
  return false;
};
