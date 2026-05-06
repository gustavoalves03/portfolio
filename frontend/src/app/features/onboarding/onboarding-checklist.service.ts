import { Injectable } from '@angular/core';
import { TenantReadiness } from '../dashboard/models/dashboard.model';
import { OnboardingProgress, OnboardingStep } from './onboarding-step.model';

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

  computeProgress(steps: OnboardingStep[]): OnboardingProgress {
    const total = steps.length;
    const done = steps.filter((s) => s.done).length;
    const next = steps.find((s) => !s.done);
    const percent = total === 0 ? 0 : Math.round((done / total) * 100);
    return {
      done,
      total,
      nextKey: next ? next.key : null,
      percent,
    };
  }
}
