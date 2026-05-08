import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { OnboardingChecklistService } from './onboarding-checklist.service';
import { TenantReadiness } from '../dashboard/models/dashboard.model';

function readiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
  return {
    slug: 'demo',
    name: false,
    hasCategory: false,
    hasContact: false,
    hasLogo: false,
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

    it('returns six steps in wizard order with correct links', () => {
      const steps = service.buildSteps(readiness());
      expect(steps.map((s) => s.key)).toEqual([
        'name',
        'contact',
        'logo',
        'categories',
        'cares',
        'openingHours',
      ]);
      expect(steps.map((s) => s.link)).toEqual([
        '/pro/salon',
        '/pro/salon',
        '/pro/salon',
        '/pro/cares',
        '/pro/cares',
        '/pro/planning',
      ]);
    });

    it('marks each step done according to readiness flags', () => {
      const steps = service.buildSteps(
        readiness({
          name: true,
          hasContact: false,
          hasLogo: true,
          hasCategory: false,
          hasActiveCare: false,
          hasOpeningHours: true,
        })
      );
      expect(steps.find((s) => s.key === 'name')?.done).toBe(true);
      expect(steps.find((s) => s.key === 'contact')?.done).toBe(false);
      expect(steps.find((s) => s.key === 'logo')?.done).toBe(true);
      expect(steps.find((s) => s.key === 'categories')?.done).toBe(false);
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

    it('passes focus=contact queryParam when contact step is undone', () => {
      const steps = service.buildSteps(readiness({ hasContact: false }));
      const step = steps.find((s) => s.key === 'contact');
      expect(step?.queryParams).toEqual({ focus: 'contact' });
    });

    it('passes focus=logo queryParam when logo step is undone', () => {
      const steps = service.buildSteps(readiness({ hasLogo: false }));
      const step = steps.find((s) => s.key === 'logo');
      expect(step?.queryParams).toEqual({ focus: 'logo' });
    });

    it('passes focus=categories queryParam when categories step is undone', () => {
      const steps = service.buildSteps(readiness({ hasCategory: false }));
      const step = steps.find((s) => s.key === 'categories');
      expect(step?.queryParams).toEqual({ focus: 'categories' });
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
      // 1 of 6 done (name) → next undone is contact
      expect(service.computeProgress(steps)).toEqual({
        done: 1,
        total: 6,
        nextKey: 'contact',
        percent: 17,
      });
    });

    it('returns null nextKey when all steps are done', () => {
      const steps = service.buildSteps(
        readiness({
          name: true,
          hasContact: true,
          hasLogo: true,
          hasCategory: true,
          hasActiveCare: true,
          hasOpeningHours: true,
        })
      );
      expect(service.computeProgress(steps)).toEqual({
        done: 6,
        total: 6,
        nextKey: null,
        percent: 100,
      });
    });

    it('rounds percent to nearest integer', () => {
      const steps = service.buildSteps(
        readiness({ name: false, hasActiveCare: true, hasOpeningHours: false })
      );
      // 1/6 = 16.67 → 17
      expect(service.computeProgress(steps).percent).toBe(17);
    });
  });
});
