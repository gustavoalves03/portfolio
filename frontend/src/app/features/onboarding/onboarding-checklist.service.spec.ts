import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { OnboardingChecklistService } from './onboarding-checklist.service';
import { TenantReadiness } from '../dashboard/models/dashboard.model';

function readiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
  return {
    slug: 'demo',
    name: false,
    hasCategory: false,
    hasActiveCare: false,
    hasOpeningHours: false,
    canPublish: false,
    status: 'DRAFT',
    ...overrides,
  };
}

describe('OnboardingChecklistService', () => {
  let service: OnboardingChecklistService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(OnboardingChecklistService);
  });

  describe('buildSteps', () => {
    it('returns empty when readiness is null', () => {
      expect(service.buildSteps(null)).toEqual([]);
    });

    it('returns three steps with correct keys and links', () => {
      const steps = service.buildSteps(readiness());
      expect(steps.map((s) => s.key)).toEqual(['name', 'cares', 'openingHours']);
      expect(steps.map((s) => s.link)).toEqual(['/pro/salon', '/pro/cares', '/pro/planning']);
    });

    it('marks each step done according to readiness flags', () => {
      const steps = service.buildSteps(
        readiness({ name: true, hasActiveCare: false, hasOpeningHours: true })
      );
      expect(steps.find((s) => s.key === 'name')?.done).toBe(true);
      expect(steps.find((s) => s.key === 'cares')?.done).toBe(false);
      expect(steps.find((s) => s.key === 'openingHours')?.done).toBe(true);
    });

    it('passes openCreate=care queryParam when cares step is undone', () => {
      const steps = service.buildSteps(readiness({ hasActiveCare: false }));
      const cares = steps.find((s) => s.key === 'cares');
      expect(cares?.queryParams).toEqual({ openCreate: 'care' });
    });

    it('passes null queryParams when cares step is done', () => {
      const steps = service.buildSteps(readiness({ hasActiveCare: true }));
      const cares = steps.find((s) => s.key === 'cares');
      expect(cares?.queryParams).toBeNull();
    });
  });
});
