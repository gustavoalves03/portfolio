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

    it('passes focus=name queryParam when name step is undone', () => {
      const steps = service.buildSteps(readiness({ name: false }));
      const nameStep = steps.find((s) => s.key === 'name');
      expect(nameStep?.queryParams).toEqual({ focus: 'name' });
    });

    it('passes null queryParams on name step when name is done', () => {
      const steps = service.buildSteps(readiness({ name: true }));
      const nameStep = steps.find((s) => s.key === 'name');
      expect(nameStep?.queryParams).toBeNull();
    });

    it('passes focus=openingHours queryParam when openingHours step is undone', () => {
      const steps = service.buildSteps(readiness({ hasOpeningHours: false }));
      const ohStep = steps.find((s) => s.key === 'openingHours');
      expect(ohStep?.queryParams).toEqual({ focus: 'openingHours' });
    });

    it('passes null queryParams on openingHours step when done', () => {
      const steps = service.buildSteps(readiness({ hasOpeningHours: true }));
      const ohStep = steps.find((s) => s.key === 'openingHours');
      expect(ohStep?.queryParams).toBeNull();
    });
  });

  describe('computeProgress', () => {
    it('returns zeros when steps is empty', () => {
      expect(service.computeProgress([])).toEqual({
        done: 0,
        total: 0,
        nextKey: null,
        percent: 0,
      });
    });

    it('counts done steps and identifies the first undone step', () => {
      const steps = service.buildSteps(
        readiness({ name: true, hasActiveCare: false, hasOpeningHours: false })
      );
      expect(service.computeProgress(steps)).toEqual({
        done: 1,
        total: 3,
        nextKey: 'cares',
        percent: 33,
      });
    });

    it('returns null nextKey when all steps are done', () => {
      const steps = service.buildSteps(
        readiness({ name: true, hasActiveCare: true, hasOpeningHours: true })
      );
      expect(service.computeProgress(steps)).toEqual({
        done: 3,
        total: 3,
        nextKey: null,
        percent: 100,
      });
    });

    it('rounds percent to nearest integer', () => {
      const steps = service.buildSteps(
        readiness({ name: false, hasActiveCare: true, hasOpeningHours: false })
      );
      // 1/3 = 33.33 → 33
      expect(service.computeProgress(steps).percent).toBe(33);
    });
  });
});
