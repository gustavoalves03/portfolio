import { Injectable } from '@angular/core';
import { TenantReadiness } from '../dashboard/models/dashboard.model';
import { OnboardingStep } from './onboarding-step.model';

@Injectable({ providedIn: 'root' })
export class OnboardingChecklistService {
  buildSteps(readiness: TenantReadiness | null): OnboardingStep[] {
    if (!readiness) return [];
    return [
      {
        key: 'name',
        done: readiness.name,
        link: '/pro/salon',
        queryParams: null,
      },
      {
        key: 'cares',
        done: readiness.hasActiveCare,
        link: '/pro/cares',
        queryParams: readiness.hasActiveCare ? null : { openCreate: 'care' },
      },
      {
        key: 'openingHours',
        done: readiness.hasOpeningHours,
        link: '/pro/planning',
        queryParams: null,
      },
    ];
  }
}
