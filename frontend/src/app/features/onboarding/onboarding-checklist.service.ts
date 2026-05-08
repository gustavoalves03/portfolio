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
        queryParams: readiness.name ? null : { focus: 'name' },
      },
      {
        key: 'contact',
        done: readiness.hasContact,
        link: '/pro/salon',
        queryParams: readiness.hasContact ? null : { focus: 'contact' },
      },
      {
        key: 'logo',
        done: readiness.hasLogo,
        link: '/pro/salon',
        queryParams: readiness.hasLogo ? null : { focus: 'logo' },
      },
      {
        key: 'categories',
        done: readiness.hasCategory,
        link: '/pro/cares',
        queryParams: readiness.hasCategory ? null : { focus: 'categories' },
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
        queryParams: readiness.hasOpeningHours ? null : { focus: 'openingHours' },
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
